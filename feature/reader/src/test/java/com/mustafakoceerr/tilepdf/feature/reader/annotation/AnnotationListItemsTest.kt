package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationListItemsTest {

    private val color = 0xFFFFEB3B.toInt()
    private val lowNote = Note("n", 1, color, PdfPoint(10f, 500f), "aşağıda")
    private val highMarkup = TextMarkup("m", 1, color, MarkupKind.StrikeOut, listOf(PdfRect(10f, 100f, 50f, 110f)), "üstte")
    private val firstPageInk = Ink("i", 0, color, 2f, listOf(listOf(PdfPoint(5f, 700f))))
    private val foreignSquare = ForeignAnnotation(1, "#2", 5, PdfRect(0f, 300f, 10f, 310f), "kare", emptyList())
    private val foreignUnderline = ForeignAnnotation(2, "a", 10, PdfRect(0f, 0f, 10f, 10f), "", emptyList())

    private val items = annotationListItems(listOf(lowNote, highMarkup, firstPageInk), listOf(foreignSquare, foreignUnderline))

    @Test
    fun orderedByPageThenTop() {
        assertEquals(
            listOf(
                AnnotationTarget.Own(firstPageInk),
                AnnotationTarget.Own(highMarkup),
                AnnotationTarget.Foreign(foreignSquare),
                AnnotationTarget.Own(lowNote),
                AnnotationTarget.Foreign(foreignUnderline),
            ),
            items.map { it.target },
        )
    }

    @Test
    fun foreignKinds() {
        assertEquals(AnnotationKind.Other, items.single { it.target == AnnotationTarget.Foreign(foreignSquare) }.kind)
        assertEquals(AnnotationKind.Underline, items.single { it.target == AnnotationTarget.Foreign(foreignUnderline) }.kind)
    }

    @Test
    fun texts() {
        assertEquals(listOf("", "üstte", "kare", "aşağıda", ""), items.map { it.text })
        assertEquals(listOf(0, 1, 1, 1, 2), items.map { it.pageIndex })
    }
}
