package com.mustafakoceerr.tilepdf.core.pdfwriter

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/** Builds small PDFs byte by byte, recording each object's offset as it goes. */
class TestPdf(header: String = "%PDF-1.7\n") {
    private val out = ByteArrayOutputStream().apply { write(header.toByteArray(Charsets.ISO_8859_1)) }
    val offsets = sortedMapOf<Int, Int>()

    val size: Int get() = out.size()

    fun raw(text: String) = apply { out.write(text.toByteArray(Charsets.ISO_8859_1)) }

    fun raw(bytes: ByteArray) = apply { out.write(bytes) }

    fun obj(num: Int, body: String) = apply {
        offsets[num] = out.size()
        raw("$num 0 obj\n$body\nendobj\n")
    }

    fun stream(num: Int, dict: String, data: ByteArray) = apply {
        offsets[num] = out.size()
        raw("$num 0 obj\n<< $dict /Length ${data.size} >>\nstream\n")
        raw(data)
        raw("\nendstream\nendobj\n")
    }

    /** A classic xref table for [nums] and the trailer; returns the table's offset. */
    fun xrefTable(nums: List<Int>, trailer: String): Int {
        val at = out.size()
        raw("xref\n")
        for (num in nums.sorted()) raw("$num 1\n${"%010d".format(offsets.getValue(num))} 00000 n \n")
        raw("trailer\n<< $trailer >>\nstartxref\n$at\n%%EOF\n")
        return at
    }

    fun bytes(): ByteArray = out.toByteArray()
}

fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater()
    deflater.setInput(data)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    return out.toByteArray()
}

/** Rows of [width] bytes, each PNG Up filtered (predictor 12), as xref streams usually are. */
fun pngUp(rows: List<ByteArray>, width: Int): ByteArray {
    val out = ByteArrayOutputStream()
    var previous = ByteArray(width)
    for (row in rows) {
        out.write(2)
        for (i in 0 until width) out.write((row[i] - previous[i]) and 0xFF)
        previous = row
    }
    return out.toByteArray()
}

/** An xref stream row: [type], a 4-byte field and a 2-byte field. */
fun xrefRow(type: Int, second: Int, third: Int) = byteArrayOf(
    type.toByte(),
    (second ushr 24).toByte(), (second ushr 16).toByte(), (second ushr 8).toByte(), second.toByte(),
    (third ushr 8).toByte(), third.toByte(),
)
