package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup

enum class AnnotationKind { Highlight, Underline, StrikeOut, Ink, Note, Other }

/** One row of the annotation list. */
data class AnnotationListItem(
    val pageIndex: Int,
    val kind: AnnotationKind,
    /** The marked-up text, a note's text, or another app's comment. */
    val text: String,
    val target: AnnotationTarget,
)

/** Every annotation, in reading order: by page, then from the top. */
fun annotationListItems(own: List<Annotation>, foreign: List<ForeignAnnotation>): List<AnnotationListItem> {
    val rows = own.map { annotation ->
        val item = when (annotation) {
            is TextMarkup -> AnnotationListItem(annotation.pageIndex, annotation.kind.listKind, annotation.text, AnnotationTarget.Own(annotation))
            is Ink -> AnnotationListItem(annotation.pageIndex, AnnotationKind.Ink, "", AnnotationTarget.Own(annotation))
            is Note -> AnnotationListItem(annotation.pageIndex, AnnotationKind.Note, annotation.text, AnnotationTarget.Own(annotation))
        }
        item to annotation.top
    } + foreign.map { annotation ->
        AnnotationListItem(annotation.pageIndex, foreignKind(annotation.subtype), annotation.contents, AnnotationTarget.Foreign(annotation)) to
            annotation.bounds.top
    }
    return rows.sortedWith(compareBy({ it.first.pageIndex }, { it.second })).map { it.first }
}

/** Where the item starts on its page, for jumping to it. */
val AnnotationListItem.topPt: Float
    get() = when (target) {
        is AnnotationTarget.Own -> target.annotation.top
        is AnnotationTarget.Foreign -> target.annotation.bounds.top
    }

/** Where an annotation starts, from the top of its page. */
val Annotation.top: Float
    get() = when (this) {
        is TextMarkup -> rects.minOfOrNull { it.top } ?: 0f
        is Ink -> strokes.flatten().minOfOrNull { it.y } ?: 0f
        is Note -> at.y
    }

private val MarkupKind.listKind: AnnotationKind
    get() = when (this) {
        MarkupKind.Highlight -> AnnotationKind.Highlight
        MarkupKind.Underline -> AnnotationKind.Underline
        MarkupKind.StrikeOut -> AnnotationKind.StrikeOut
    }

/** PDFium subtypes: Text 1, Highlight 9, Underline 10, StrikeOut 12, Ink 15. */
private fun foreignKind(subtype: Int) = when (subtype) {
    1 -> AnnotationKind.Note
    9 -> AnnotationKind.Highlight
    10 -> AnnotationKind.Underline
    12 -> AnnotationKind.StrikeOut
    15 -> AnnotationKind.Ink
    else -> AnnotationKind.Other
}
