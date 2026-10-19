package com.mustafakoceerr.tilepdf.core.pdfwriter

import java.io.ByteArrayOutputStream

/** Reads PDF objects from [bytes] starting at [pos]. */
internal class PdfLexer(private val bytes: ByteArray, var pos: Int = 0) {

    fun readObject(): PdfObject {
        skipSpace()
        if (pos >= bytes.size) throw PdfFormatException("Unexpected end at $pos")
        return when (val c = bytes[pos].toInt().toChar()) {
            '<' -> if (peek(1) == '<'.code) readDict() else readHexString()
            '[' -> readArray()
            '(' -> readLiteralString()
            '/' -> readName()
            't', 'f', 'n' -> when (val word = readToken()) {
                "true" -> PdfBool(true)
                "false" -> PdfBool(false)
                "null" -> PdfNull
                else -> throw PdfFormatException("Unexpected '$word' at $pos")
            }
            else -> if (c.isDigit() || c == '+' || c == '-' || c == '.') readNumberOrRef() else throw PdfFormatException("Unexpected '$c' at $pos")
        }
    }

    /** `num gen obj`, its object and, after a dictionary, a stream whose length [length] resolves. */
    fun readIndirect(length: (PdfObject) -> Long?): Triple<Int, Int, PdfObject> {
        val num = readToken().toIntOrNull() ?: throw PdfFormatException("No object number at $pos")
        val gen = readToken().toIntOrNull() ?: throw PdfFormatException("No generation at $pos")
        if (readToken() != "obj") throw PdfFormatException("No 'obj' at $pos")
        val obj = readObject()
        val afterObject = pos
        skipSpace()
        if (obj is PdfDict && startsWith("stream")) {
            pos += "stream".length
            if (peek(0) == '\r'.code) pos++
            if (peek(0) == '\n'.code) pos++
            val start = pos
            val declared = obj["Length"]?.let(length)?.toInt()
            val end = if (declared != null && start + declared <= bytes.size && endstreamFollows(start + declared)) {
                start + declared
            } else {
                findEndstream(start)
            }
            pos = end
            skipSpace()
            if (startsWith("endstream")) pos += "endstream".length
            return Triple(num, gen, PdfStream(obj, bytes.copyOfRange(start, end)))
        }
        pos = afterObject
        return Triple(num, gen, obj)
    }

    /** The next whitespace-separated token, delimiters ending it. */
    fun readToken(): String {
        skipSpace()
        val start = pos
        while (pos < bytes.size && isRegular(bytes[pos].toInt())) pos++
        return String(bytes, start, pos - start, Charsets.ISO_8859_1)
    }

    fun startsWith(text: String): Boolean {
        if (pos + text.length > bytes.size) return false
        return text.indices.all { bytes[pos + it].toInt() == text[it].code }
    }

    fun skipSpace() {
        while (pos < bytes.size) {
            val c = bytes[pos].toInt()
            when {
                isSpace(c) -> pos++
                c == '%'.code -> while (pos < bytes.size && bytes[pos].toInt() != '\n'.code && bytes[pos].toInt() != '\r'.code) pos++
                else -> return
            }
        }
    }

    private fun peek(ahead: Int): Int = if (pos + ahead < bytes.size) bytes[pos + ahead].toInt() and 0xFF else -1

    private fun endstreamFollows(at: Int): Boolean {
        val saved = pos
        pos = at
        skipSpace()
        val found = startsWith("endstream")
        pos = saved
        return found
    }

    /** Where the data ends when /Length can't be trusted: before "endstream" and its line break. */
    private fun findEndstream(from: Int): Int {
        val marker = "endstream".toByteArray(Charsets.ISO_8859_1)
        var at = from
        while (at + marker.size <= bytes.size) {
            if (marker.indices.all { bytes[at + it] == marker[it] }) {
                var end = at
                if (end > from && bytes[end - 1].toInt() == '\n'.code) end--
                if (end > from && bytes[end - 1].toInt() == '\r'.code) end--
                return end
            }
            at++
        }
        throw PdfFormatException("No endstream after $from")
    }

    private fun readDict(): PdfDict {
        pos += 2
        val entries = LinkedHashMap<String, PdfObject>()
        while (true) {
            skipSpace()
            if (startsWith(">>")) {
                pos += 2
                return PdfDict(entries)
            }
            val key = readObject() as? PdfName ?: throw PdfFormatException("Dictionary key isn't a name at $pos")
            entries[key.name] = readObject()
        }
    }

    private fun readArray(): PdfArray {
        pos++
        val items = mutableListOf<PdfObject>()
        while (true) {
            skipSpace()
            if (peek(0) == ']'.code) {
                pos++
                return PdfArray(items)
            }
            items += readObject()
        }
    }

    private fun readName(): PdfName {
        pos++
        val out = StringBuilder()
        while (pos < bytes.size && isRegular(bytes[pos].toInt())) {
            val c = bytes[pos].toInt() and 0xFF
            if (c == '#'.code && pos + 2 < bytes.size) {
                val code = String(bytes, pos + 1, 2, Charsets.ISO_8859_1).toIntOrNull(16)
                if (code != null) {
                    out.append(code.toChar())
                    pos += 3
                    continue
                }
            }
            out.append(c.toChar())
            pos++
        }
        return PdfName(out.toString())
    }

    private fun readNumberOrRef(): PdfObject {
        val first = readToken()
        if (first.toIntOrNull() != null) {
            val saved = pos
            val second = readToken()
            if (second.toIntOrNull() != null && readToken() == "R") return PdfRef(first.toInt(), second.toInt())
            pos = saved
        }
        if (first.toDoubleOrNull() == null) throw PdfFormatException("Bad number '$first' at $pos")
        return PdfNumber(first)
    }

    private fun readHexString(): PdfString {
        pos++
        val digits = StringBuilder()
        while (pos < bytes.size && bytes[pos].toInt() != '>'.code) {
            val c = bytes[pos].toInt().toChar()
            if (!isSpace(c.code)) digits.append(c)
            pos++
        }
        pos++
        if (digits.length % 2 == 1) digits.append('0')
        return PdfString(ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, hex = true)
    }

    private fun readLiteralString(): PdfString {
        pos++
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < bytes.size) {
            val c = bytes[pos].toInt() and 0xFF
            pos++
            when (c) {
                '('.code -> {
                    depth++
                    out.write(c)
                }
                ')'.code -> {
                    depth--
                    if (depth == 0) return PdfString(out.toByteArray())
                    out.write(c)
                }
                '\\'.code -> readEscape(out)
                else -> out.write(c)
            }
        }
        throw PdfFormatException("Unclosed string")
    }

    private fun readEscape(out: ByteArrayOutputStream) {
        if (pos >= bytes.size) return
        val c = bytes[pos].toInt() and 0xFF
        pos++
        when (c.toChar()) {
            'n' -> out.write('\n'.code)
            'r' -> out.write('\r'.code)
            't' -> out.write('\t'.code)
            'b' -> out.write(8)
            'f' -> out.write(12)
            '\r' -> if (peek(0) == '\n'.code) pos++
            '\n' -> Unit
            in '0'..'7' -> {
                var value = c - '0'.code
                repeat(2) {
                    val next = peek(0)
                    if (next in '0'.code..'7'.code) {
                        value = value * 8 + (next - '0'.code)
                        pos++
                    }
                }
                out.write(value and 0xFF)
            }
            else -> out.write(c)
        }
    }

    companion object {
        fun isSpace(c: Int) = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32

        fun isRegular(c: Int) = !isSpace(c) && c.toChar() !in "()<>[]{}/%"
    }
}
