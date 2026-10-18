package com.mustafakoceerr.tilepdf.core.pdfwriter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalUpdateTest {

    private fun classic(): ByteArray {
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            .obj(2, "<< /Type /Pages /Kids [] /Count 0 >>")
        pdf.xrefTable(listOf(1, 2), "/Size 3 /Root 1 0 R /ID [<0102> <0304>]")
        return pdf.bytes()
    }

    @Test
    fun objectsSurviveWritingAndReading() {
        val dict = PdfDict(
            linkedMapOf(
                "Name" to PdfName("Two Words/#"),
                "Text" to PdfString("a(b)\\c\r\n".toByteArray()),
                "Hex" to PdfString(byteArrayOf(0, -1, 16), hex = true),
                "List" to PdfArray(listOf(PdfNumber("1.5"), PdfBool(false), PdfNull, PdfRef(7, 0))),
            ),
        )
        val update = IncrementalUpdate(PdfFile(classic()))
        val ref = update.add(dict)

        val reread = PdfFile(update.write()).resolve(ref)

        assertEquals(dict, reread)
    }

    @Test
    fun theOriginalBytesComeFirstUnchanged() {
        val original = classic()
        val update = IncrementalUpdate(PdfFile(original))
        update.add(PdfString("new".toByteArray()))

        val written = update.write()

        assertArrayEquals(original, written.copyOf(original.size))
    }

    @Test
    fun newAndReplacedObjectsAreReadBackWithTheOldOnes() {
        val file = PdfFile(classic())
        val update = IncrementalUpdate(file)
        val pages = (file.resolve(PdfRef(2)) as PdfDict).with("Count", PdfNumber(5))
        update.set(PdfRef(2), pages)
        val added = update.add(PdfStream(PdfDict(emptyMap()), "q Q".toByteArray()))

        val reread = PdfFile(update.write())

        assertEquals(PdfRef(3), added)
        assertEquals(PdfNumber(5), (reread.resolve(PdfRef(2)) as PdfDict)["Count"])
        assertArrayEquals("q Q".toByteArray(), (reread.resolve(added) as PdfStream).data)
        assertEquals(PdfName("Catalog"), (reread.resolve(PdfRef(1)) as PdfDict)["Type"])
        assertEquals(PdfNumber(4), reread.trailer["Size"])
        assertEquals(PdfRef(1), reread.trailer["Root"])
        assertEquals(file.trailer["ID"], reread.trailer["ID"])
        assertEquals(PdfNumber(file.startxref.toLong()), reread.trailer["Prev"])
        assertFalse(reread.lastSectionIsStream)
    }

    @Test
    fun afterAnXrefStreamTheUpdateIsAnXrefStream() {
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            .obj(2, "<< /Type /Pages /Kids [] /Count 0 >>")
        val xrefAt = pdf.size
        val rows = listOf(xrefRow(0, 0, 0xFFFF), xrefRow(1, pdf.offsets.getValue(1), 0), xrefRow(1, pdf.offsets.getValue(2), 0), xrefRow(1, xrefAt, 0))
        pdf.stream(3, "/Type /XRef /Size 4 /W [1 4 2] /Root 1 0 R /Filter /FlateDecode", deflate(rows.reduce(ByteArray::plus)))
        pdf.raw("startxref\n$xrefAt\n%%EOF\n")
        val update = IncrementalUpdate(PdfFile(pdf.bytes()))
        val added = update.add(PdfName("Added"))

        val reread = PdfFile(update.write())

        assertTrue(reread.lastSectionIsStream)
        assertEquals(PdfName("Added"), reread.resolve(added))
        assertEquals(PdfName("Pages"), (reread.resolve(PdfRef(2)) as PdfDict)["Type"])
    }
}
