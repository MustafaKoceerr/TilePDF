package com.mustafakoceerr.tilepdf.core.pdfwriter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfFileTest {

    private fun name(value: String) = PdfName(value)

    @Test
    fun classicXrefObjectsAreRead() {
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            .obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
            .obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595.5 842] /Rotate -90 >>")
        pdf.xrefTable(listOf(1, 2, 3), "/Size 4 /Root 1 0 R")

        val file = PdfFile(pdf.bytes())

        assertEquals(PdfRef(1, 0), file.trailer["Root"])
        val page = file.resolve(PdfRef(3, 0)) as PdfDict
        assertEquals(name("Page"), page["Type"])
        assertEquals(listOf(0.0, 0.0, 595.5, 842.0), (page["MediaBox"] as PdfArray).items.map { (it as PdfNumber).value })
        assertEquals(-90L, (page["Rotate"] as PdfNumber).long)
        assertEquals(setOf(1, 2, 3), file.objectNumbers)
    }

    @Test
    fun theNewestRevisionWins() {
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            .obj(2, "<< /Type /Pages /Kids [] /Count 0 >>")
        val first = pdf.xrefTable(listOf(1, 2), "/Size 3 /Root 1 0 R")
        pdf.obj(2, "<< /Type /Pages /Kids [] /Count 7 >>").obj(3, "(added)")
        pdf.xrefTable(listOf(2, 3), "/Size 4 /Root 1 0 R /Prev $first")

        val file = PdfFile(pdf.bytes())

        assertEquals(7L, ((file.resolve(PdfRef(2, 0)) as PdfDict)["Count"] as PdfNumber).long)
        assertEquals(name("Catalog"), (file.resolve(PdfRef(1, 0)) as PdfDict)["Type"])
        assertEquals(setOf(1, 2, 3), file.objectNumbers)
        assertEquals(PdfNumber("4"), file.trailer["Size"])
    }

    @Test
    fun xrefStreamsAndObjectStreamsAreRead() {
        // Object 3 is a plain object; 4 and 5 live in object stream 6.
        val inside = "<< /Kind /Inside >> [1 (two) /three]"
        val header = "4 0 5 ${"<< /Kind /Inside >> ".length} "
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            .obj(2, "<< /Type /Pages /Kids [] /Count 0 >>")
            .obj(3, "<< /Plain true >>")
        pdf.stream(6, "/Type /ObjStm /N 2 /First ${header.length} /Filter /FlateDecode", deflate((header + inside).toByteArray()))
        val xrefAt = pdf.size
        val rows = listOf(
            xrefRow(0, 0, 0xFFFF),
            xrefRow(1, pdf.offsets.getValue(1), 0),
            xrefRow(1, pdf.offsets.getValue(2), 0),
            xrefRow(1, pdf.offsets.getValue(3), 0),
            xrefRow(2, 6, 0),
            xrefRow(2, 6, 1),
            xrefRow(1, pdf.offsets.getValue(6), 0),
            xrefRow(1, xrefAt, 0),
        )
        pdf.stream(
            7,
            "/Type /XRef /Size 8 /W [1 4 2] /Root 1 0 R /Filter /FlateDecode /DecodeParms << /Predictor 12 /Columns 7 >>",
            deflate(pngUp(rows, 7)),
        )
        pdf.raw("startxref\n$xrefAt\n%%EOF\n")

        val file = PdfFile(pdf.bytes())

        assertEquals(PdfBool(true), (file.resolve(PdfRef(3, 0)) as PdfDict)["Plain"])
        assertEquals(name("Inside"), (file.resolve(PdfRef(4, 0)) as PdfDict)["Kind"])
        val array = file.resolve(PdfRef(5, 0)) as PdfArray
        assertEquals(listOf<PdfObject>(PdfNumber("1"), PdfString("two".toByteArray()), name("three")), array.items)
        assertTrue(file.lastSectionIsStream)
    }

    @Test
    fun stringsAndNamesAreDecoded() {
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /A (a\\(b\\)\\n\\101 (nested)) /B <48 65 6c6C6f> /N /Two#20Words /E () >>")
        pdf.xrefTable(listOf(1), "/Size 2 /Root 1 0 R")

        val dict = PdfFile(pdf.bytes()).resolve(PdfRef(1, 0)) as PdfDict

        assertArrayEquals("a(b)\nA (nested)".toByteArray(), (dict["A"] as PdfString).bytes)
        assertArrayEquals("Hello".toByteArray(), (dict["B"] as PdfString).bytes)
        assertEquals(name("Two Words"), dict["N"])
        assertArrayEquals(ByteArray(0), (dict["E"] as PdfString).bytes)
    }

    @Test
    fun streamsKeepTheirRawData() {
        val data = "BT /F1 12 Tf (Hi) Tj ET".toByteArray()
        val pdf = TestPdf().obj(1, "<< /Type /Catalog >>").stream(2, "", data)
        pdf.xrefTable(listOf(1, 2), "/Size 3 /Root 1 0 R")

        val stream = PdfFile(pdf.bytes()).resolve(PdfRef(2, 0)) as PdfStream

        assertArrayEquals(data, stream.data)
        assertEquals(PdfNumber("${data.size}"), stream.dict["Length"])
    }

    @Test
    fun everyObjectOfTheRepoTestDocumentsResolves() {
        for (asset in listOf("signed.pdf", "forms.pdf", "annotated.pdf", "outline.pdf")) {
            val file = PdfFile(File("../testing/src/main/assets/$asset").readBytes())
            for (num in file.objectNumbers) file.resolve(PdfRef(num, 0))
            assertTrue(asset, file.trailer["Root"] is PdfRef)
        }
    }
}
