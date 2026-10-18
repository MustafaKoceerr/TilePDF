package com.mustafakoceerr.tilepdf.core.pdfwriter

import java.io.ByteArrayOutputStream

/** Writes PDF objects as bytes. */
object PdfWriter {

    fun serialize(obj: PdfObject): ByteArray = ByteArrayOutputStream().also { write(obj, it) }.toByteArray()

    fun write(obj: PdfObject, out: ByteArrayOutputStream) {
        when (obj) {
            PdfNull -> out.ascii("null")
            is PdfBool -> out.ascii(obj.value.toString())
            is PdfNumber -> out.ascii(obj.text)
            is PdfRef -> out.ascii("${obj.num} ${obj.gen} R")
            is PdfName -> out.ascii(nameText(obj.name))
            is PdfString -> if (obj.hex) out.ascii(hexText(obj.bytes)) else literal(obj.bytes, out)
            is PdfArray -> {
                out.ascii("[")
                obj.items.forEachIndexed { i, item ->
                    if (i > 0) out.ascii(" ")
                    write(item, out)
                }
                out.ascii("]")
            }
            is PdfDict -> {
                out.ascii("<<")
                for ((key, value) in obj.entries) {
                    out.ascii(nameText(key))
                    out.ascii(" ")
                    write(value, out)
                }
                out.ascii(">>")
            }
            is PdfStream -> {
                write(obj.dict.with("Length", PdfNumber(obj.data.size.toLong())), out)
                out.ascii("\nstream\n")
                out.write(obj.data)
                out.ascii("\nendstream")
            }
        }
    }

    /** A name with every byte outside the plain printable ones, and `#`, as #xx. */
    private fun nameText(name: String): String = buildString {
        append('/')
        for (c in name) {
            val code = c.code and 0xFF
            if (code in 0x21..0x7E && c !in "()<>[]{}/%#") append(c) else append("#%02X".format(code))
        }
    }

    private fun hexText(bytes: ByteArray) = "<" + bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) } + ">"

    private fun literal(bytes: ByteArray, out: ByteArrayOutputStream) {
        out.ascii("(")
        for (b in bytes) {
            when (val c = b.toInt() and 0xFF) {
                '('.code, ')'.code, '\\'.code -> {
                    out.write('\\'.code)
                    out.write(c)
                }
                '\r'.code -> out.ascii("\\r")
                else -> out.write(c)
            }
        }
        out.ascii(")")
    }

    private fun ByteArrayOutputStream.ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))
}
