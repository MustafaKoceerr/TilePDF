package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationCodecTest {

    private val annotations: List<Annotation> = listOf(
        TextMarkup(
            "tilepdf-1", 0, 0xFFFFEB3B.toInt(), MarkupKind.Underline,
            listOf(PdfRect(1f, 2f, 3f, 4f), PdfRect(5f, 6f, 7f, 8f)), "çğışöü İ",
        ),
        Ink(
            "tilepdf-2", 3, 0xFF2196F3.toInt(), 2.5f,
            listOf(listOf(PdfPoint(1f, 1f), PdfPoint(2f, 3f)), listOf(PdfPoint(9f, 9f))),
        ),
        Note("tilepdf-3", 7, 0xFFFFEB3B.toInt(), PdfPoint(10f, 20f), "Not: \"tırnak\" ve\nsatır"),
    )

    @Test
    fun allKindsRoundTrip() {
        assertEquals(annotations, AnnotationCodec.decodeAnnotations(AnnotationCodec.encodeAnnotations(annotations)))
    }

    @Test
    fun refsRoundTrip() {
        val refs = setOf(ForeignRef(0, "#2"), ForeignRef(4, "acrobat-1"))
        assertEquals(refs, AnnotationCodec.decodeRefs(AnnotationCodec.encodeRefs(refs)))
    }

    @Test
    fun emptyListsRoundTrip() {
        assertEquals(emptyList<Annotation>(), AnnotationCodec.decodeAnnotations(AnnotationCodec.encodeAnnotations(emptyList())))
        assertEquals(emptySet<ForeignRef>(), AnnotationCodec.decodeRefs(AnnotationCodec.encodeRefs(emptySet())))
    }

    @Test
    fun unknownTypeIsSkipped() {
        val note = annotations.last()
        val noteJson = AnnotationCodec.encodeAnnotations(listOf(note)).removePrefix("[").removeSuffix("]")
        val json = """[{"type":"stamp","v":1,"id":"tilepdf-9","page":0,"color":0},$noteJson]"""
        assertEquals(listOf(note), AnnotationCodec.decodeAnnotations(json))
    }

    @Test
    fun formValuesRoundTrip() {
        val values = mapOf("name" to "Ada \"Lovelace\"", "city" to "İzmir", "agree" to "Off")

        assertEquals(values, AnnotationCodec.decodeFormValues(AnnotationCodec.encodeFormValues(values)))
    }
}
