package com.mustafakoceerr.tilepdf.core.pdfwriter

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * A PDF file's objects, read through its cross-reference sections: classic
 * tables and xref streams, newest first along `/Prev`, with objects inside
 * object streams. Enough to add an incremental update, not to render.
 */
class PdfFile(val bytes: ByteArray) {

    private sealed interface Entry {
        data class At(val offset: Int) : Entry
        data class InStream(val stream: Int, val index: Int) : Entry
        data object Free : Entry
    }

    private class Section(val trailer: PdfDict, val entries: List<Pair<Int, Entry>>, val isStream: Boolean)

    private val entries = HashMap<Int, Entry>()
    private val cache = HashMap<Int, PdfObject>()
    private val objectStreams = HashMap<Int, Pair<ByteArray, List<Pair<Int, Int>>>>()

    /** Where the last cross-reference section starts. */
    val startxref: Int

    /** The newest trailer, or the newest xref stream's dictionary. */
    val trailer: PdfDict

    /** Whether the newest section is an xref stream: an update should write one too. */
    val lastSectionIsStream: Boolean

    /** Every object number in use. */
    val objectNumbers: Set<Int> get() = entries.filterValues { it != Entry.Free }.keys

    init {
        startxref = findStartxref()
        var newest: Section? = null
        var offset: Int? = startxref
        val seen = HashSet<Int>()
        while (offset != null && seen.add(offset)) {
            val section = readSection(offset)
            if (newest == null) newest = section
            section.entries.forEach { (num, entry) -> entries.putIfAbsent(num, entry) }
            // A hybrid file's classic trailer points at an xref stream with more entries.
            (section.trailer["XRefStm"] as? PdfNumber)?.let { stm ->
                if (seen.add(stm.long.toInt())) readSection(stm.long.toInt()).entries.forEach { (num, entry) -> entries.putIfAbsent(num, entry) }
            }
            offset = (section.trailer["Prev"] as? PdfNumber)?.long?.toInt()
        }
        trailer = newest?.trailer ?: throw PdfFormatException("No cross-reference section")
        lastSectionIsStream = newest.isStream
    }

    fun resolve(obj: PdfObject): PdfObject = if (obj is PdfRef) resolve(obj) else obj

    fun resolve(ref: PdfRef): PdfObject = cache.getOrPut(ref.num) {
        when (val entry = entries[ref.num]) {
            is Entry.At -> PdfLexer(bytes, entry.offset).readIndirect(::lengthOf).third
            is Entry.InStream -> fromObjectStream(entry)
            Entry.Free, null -> PdfNull
        }
    }

    private fun lengthOf(obj: PdfObject): Long? = when (obj) {
        is PdfNumber -> obj.long
        is PdfRef -> (resolve(obj) as? PdfNumber)?.long
        else -> null
    }

    private fun findStartxref(): Int {
        val marker = "startxref".toByteArray(Charsets.ISO_8859_1)
        var at = bytes.size - marker.size
        while (at >= 0) {
            if (marker.indices.all { bytes[at + it] == marker[it] }) {
                return PdfLexer(bytes, at + marker.size).readToken().toIntOrNull()
                    ?: throw PdfFormatException("No offset after startxref")
            }
            at--
        }
        throw PdfFormatException("No startxref")
    }

    private fun readSection(offset: Int): Section {
        val lexer = PdfLexer(bytes, offset)
        lexer.skipSpace()
        return if (lexer.startsWith("xref")) readTable(lexer) else readStream(offset)
    }

    private fun readTable(lexer: PdfLexer): Section {
        lexer.readToken()
        val found = mutableListOf<Pair<Int, Entry>>()
        while (true) {
            lexer.skipSpace()
            if (lexer.startsWith("trailer")) break
            val start = lexer.readToken().toIntOrNull() ?: throw PdfFormatException("Bad xref subsection at ${lexer.pos}")
            val count = lexer.readToken().toIntOrNull() ?: throw PdfFormatException("Bad xref count at ${lexer.pos}")
            for (i in 0 until count) {
                val offset = lexer.readToken().toIntOrNull() ?: throw PdfFormatException("Bad xref entry at ${lexer.pos}")
                lexer.readToken()
                val type = lexer.readToken()
                found += (start + i) to if (type == "n") Entry.At(offset) else Entry.Free
            }
        }
        lexer.readToken()
        val trailer = lexer.readObject() as? PdfDict ?: throw PdfFormatException("No trailer dictionary")
        return Section(trailer, found, isStream = false)
    }

    private fun readStream(offset: Int): Section {
        val stream = PdfLexer(bytes, offset).readIndirect(::lengthOf).third as? PdfStream
            ?: throw PdfFormatException("No xref stream at $offset")
        val dict = stream.dict
        val widths = (dict["W"] as? PdfArray)?.items?.map { (it as PdfNumber).long.toInt() }
            ?: throw PdfFormatException("Xref stream without /W")
        val size = (dict["Size"] as? PdfNumber)?.long?.toInt() ?: 0
        val index = (dict["Index"] as? PdfArray)?.items?.map { (it as PdfNumber).long.toInt() } ?: listOf(0, size)
        val data = decode(stream)
        val rowLength = widths.sum()
        val found = mutableListOf<Pair<Int, Entry>>()
        var row = 0
        for ((start, count) in index.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }) {
            for (i in 0 until count) {
                val at = row * rowLength
                if (at + rowLength > data.size) break
                var field = at
                fun next(width: Int): Long {
                    var value = 0L
                    repeat(width) { value = (value shl 8) or (data[field++].toLong() and 0xFF) }
                    return value
                }
                val type = if (widths[0] == 0) 1L else next(widths[0])
                val second = next(widths[1])
                val third = next(widths[2])
                found += (start + i) to when (type) {
                    1L -> Entry.At(second.toInt())
                    2L -> Entry.InStream(second.toInt(), third.toInt())
                    else -> Entry.Free
                }
                row++
            }
        }
        return Section(dict, found, isStream = true)
    }

    private fun fromObjectStream(entry: Entry.InStream): PdfObject {
        val (data, offsets) = objectStreams.getOrPut(entry.stream) {
            val stream = resolve(PdfRef(entry.stream)) as? PdfStream ?: throw PdfFormatException("No object stream ${entry.stream}")
            val decoded = decode(stream)
            val count = (stream.dict["N"] as? PdfNumber)?.long?.toInt() ?: 0
            val first = (stream.dict["First"] as? PdfNumber)?.long?.toInt() ?: 0
            val header = PdfLexer(decoded)
            val pairs = List(count) { (header.readToken().toIntOrNull() ?: 0) to (first + (header.readToken().toIntOrNull() ?: 0)) }
            decoded to pairs
        }
        val (_, offset) = offsets.getOrNull(entry.index) ?: return PdfNull
        return PdfLexer(data, offset).readObject()
    }

    /** A stream's data with its filters undone: Flate, with PNG predictors. */
    internal fun decode(stream: PdfStream): ByteArray {
        val filters = when (val filter = stream.dict["Filter"]) {
            is PdfName -> listOf(filter.name)
            is PdfArray -> filter.items.map { (it as PdfName).name }
            else -> emptyList()
        }
        val parms = when (val p = stream.dict["DecodeParms"]) {
            is PdfDict -> listOf(p)
            is PdfArray -> p.items.map { resolve(it) as? PdfDict }
            else -> emptyList()
        }
        var data = stream.data
        filters.forEachIndexed { i, filter ->
            data = when (filter) {
                "FlateDecode", "Fl" -> unpredict(inflate(data), parms.getOrNull(i))
                else -> throw PdfFormatException("Unsupported filter $filter")
            }
        }
        return data
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun unpredict(data: ByteArray, parms: PdfDict?): ByteArray {
        val predictor = (parms?.get("Predictor") as? PdfNumber)?.long?.toInt() ?: 1
        if (predictor < 10) return data
        val colors = (parms?.get("Colors") as? PdfNumber)?.long?.toInt() ?: 1
        val bits = (parms?.get("BitsPerComponent") as? PdfNumber)?.long?.toInt() ?: 8
        val columns = (parms?.get("Columns") as? PdfNumber)?.long?.toInt() ?: 1
        val pixel = maxOf(1, colors * bits / 8)
        val rowLength = (columns * colors * bits + 7) / 8
        val out = ByteArrayOutputStream()
        var previous = ByteArray(rowLength)
        var at = 0
        while (at + 1 + rowLength <= data.size) {
            val type = data[at].toInt()
            val row = data.copyOfRange(at + 1, at + 1 + rowLength)
            for (i in row.indices) {
                val left = if (i >= pixel) row[i - pixel].toInt() and 0xFF else 0
                val up = previous[i].toInt() and 0xFF
                val upLeft = if (i >= pixel) previous[i - pixel].toInt() and 0xFF else 0
                val value = row[i].toInt() and 0xFF
                row[i] = when (type) {
                    1 -> value + left
                    2 -> value + up
                    3 -> value + (left + up) / 2
                    4 -> value + paeth(left, up, upLeft)
                    else -> value
                }.toByte()
            }
            out.write(row)
            previous = row
            at += 1 + rowLength
        }
        return out.toByteArray()
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }
}
