package com.mustafakoceerr.tilepdf.core.pdfwriter

import java.io.ByteArrayOutputStream

/**
 * New and replaced objects for [file], written after its bytes, which stay
 * untouched, with one cross-reference section of the same kind as the
 * file's last: a table, or a stream after a stream.
 */
class IncrementalUpdate(private val file: PdfFile) {

    private val objects = sortedMapOf<Int, PdfObject>()
    private var nextNumber = maxOf(
        (file.trailer["Size"] as? PdfNumber)?.long?.toInt() ?: 0,
        (file.objectNumbers.maxOrNull() ?: 0) + 1,
    )

    /** Adds [obj] as a new object; returns its reference. */
    fun add(obj: PdfObject): PdfRef {
        val ref = PdfRef(nextNumber++)
        objects[ref.num] = obj
        return ref
    }

    /** Replaces the object [ref] points to with [obj]. */
    fun set(ref: PdfRef, obj: PdfObject) {
        objects[ref.num] = obj
    }

    fun write(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(file.bytes)
        if (file.bytes.isNotEmpty() && file.bytes.last().toInt() != '\n'.code) out.ascii("\n")
        val offsets = sortedMapOf<Int, Int>()
        for ((num, obj) in objects) {
            offsets[num] = out.size()
            out.ascii("$num 0 obj\n")
            PdfWriter.write(obj, out)
            out.ascii("\nendobj\n")
        }
        val xrefAt = out.size()
        if (file.lastSectionIsStream) writeXrefStream(out, offsets, xrefAt) else writeXrefTable(out, offsets)
        out.ascii("startxref\n$xrefAt\n%%EOF\n")
        return out.toByteArray()
    }

    /** The trailer entries the update carries over, with its own size and link back. */
    private fun trailerEntries(size: Int): LinkedHashMap<String, PdfObject> = LinkedHashMap<String, PdfObject>().apply {
        put("Size", PdfNumber(size.toLong()))
        for (key in listOf("Root", "Info", "ID")) file.trailer[key]?.let { put(key, it) }
        put("Prev", PdfNumber(file.startxref.toLong()))
    }

    private fun writeXrefTable(out: ByteArrayOutputStream, offsets: Map<Int, Int>) {
        out.ascii("xref\n")
        for (run in runs(offsets.keys.toList())) {
            out.ascii("${run.first()} ${run.size}\n")
            for (num in run) out.ascii("%010d 00000 n\r\n".format(offsets.getValue(num)))
        }
        out.ascii("trailer\n")
        PdfWriter.write(PdfDict(trailerEntries(nextNumber)), out)
        out.ascii("\n")
    }

    private fun writeXrefStream(out: ByteArrayOutputStream, offsets: Map<Int, Int>, xrefAt: Int) {
        val streamNumber = nextNumber++
        val all = offsets + (streamNumber to xrefAt)
        val rows = ByteArrayOutputStream()
        val numbers = all.keys.sorted()
        for (num in numbers) {
            val offset = all.getValue(num)
            rows.write(1)
            for (shift in listOf(24, 16, 8, 0)) rows.write((offset ushr shift) and 0xFF)
            rows.write(0)
            rows.write(0)
        }
        val dict = trailerEntries(nextNumber).apply {
            put("Type", PdfName("XRef"))
            put("W", PdfArray(listOf(PdfNumber(1), PdfNumber(4), PdfNumber(2))))
            put("Index", PdfArray(runs(numbers).flatMap { listOf(PdfNumber(it.first().toLong()), PdfNumber(it.size.toLong())) }))
        }
        out.ascii("$streamNumber 0 obj\n")
        PdfWriter.write(PdfStream(PdfDict(dict), rows.toByteArray()), out)
        out.ascii("\nendobj\n")
    }

    /** [numbers] in runs of consecutive numbers, as xref subsections are. */
    private fun runs(numbers: List<Int>): List<List<Int>> {
        val result = mutableListOf<MutableList<Int>>()
        for (num in numbers.sorted()) {
            val last = result.lastOrNull()
            if (last != null && last.last() + 1 == num) last += num else result += mutableListOf(num)
        }
        return result
    }

    private fun ByteArrayOutputStream.ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))
}
