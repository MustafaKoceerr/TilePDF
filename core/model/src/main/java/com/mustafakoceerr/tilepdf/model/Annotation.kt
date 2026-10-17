package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import java.util.UUID

/** `/NM` prefix of every annotation TilePDF creates. */
const val OWN_ANNOTATION_PREFIX = "tilepdf-"

fun newAnnotationId(): String = OWN_ANNOTATION_PREFIX + UUID.randomUUID()

fun isOwnAnnotationId(id: String?): Boolean = id != null && id.startsWith(OWN_ANNOTATION_PREFIX)

/**
 * An annotation TilePDF made. Never changed in place: an edit replaces it
 * with a new [id]. Positions are top-left PDF points on [pageIndex].
 */
sealed interface Annotation {
    val id: String
    val pageIndex: Int

    /** ARGB. */
    val color: Int
}

enum class MarkupKind { Highlight, Underline, StrikeOut }

/** Highlight, underline or strikeout over selected text; one rect per line. */
data class TextMarkup(
    override val id: String,
    override val pageIndex: Int,
    override val color: Int,
    val kind: MarkupKind,
    val rects: List<PdfRect>,
    /** The text it covers, for the annotation list. */
    val text: String,
) : Annotation

/** Freehand pen strokes. */
data class Ink(
    override val id: String,
    override val pageIndex: Int,
    override val color: Int,
    val widthPt: Float,
    val strokes: List<List<PdfPoint>>,
) : Annotation

/** A sticky note; [at] is the icon's top-left corner. */
data class Note(
    override val id: String,
    override val pageIndex: Int,
    override val color: Int,
    val at: PdfPoint,
    val text: String,
) : Annotation

/**
 * Another app's markup annotation. It can be listed and deleted, never edited.
 *
 * @param key its `/NM`, or `#<index>` among the page's annotations when it has none.
 * @param subtype PDFium's `FPDF_ANNOT_*` value.
 * @param inkStrokes the pen strokes when it is an ink annotation, else empty.
 */
data class ForeignAnnotation(
    val pageIndex: Int,
    val key: String,
    val subtype: Int,
    val bounds: PdfRect,
    val contents: String,
    val inkStrokes: List<List<PdfPoint>>,
) {
    val ref: ForeignRef get() = ForeignRef(pageIndex, key)
}

/** Identifies a foreign annotation in the file a draft is based on. */
data class ForeignRef(val pageIndex: Int, val key: String)

/** The annotations on one page. */
data class PageAnnotations(val own: List<Annotation>, val foreign: List<ForeignAnnotation>)
