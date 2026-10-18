package com.mustafakoceerr.tilepdf.core.pdf

import androidx.annotation.Keep
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.PageAnnotations
import com.mustafakoceerr.tilepdf.model.TextMarkup
import com.mustafakoceerr.tilepdf.model.isOwnAnnotationId

/**
 * One annotation as PDFium reports it, in top-left points. Built by native
 * code, hence [Keep].
 *
 * @param index its position among the page's annotations.
 * @param id its `/NM`, if any.
 * @param rect left, top, right, bottom.
 * @param points markup quads as four corner pairs each (TL, TR, BL, BR), or
 *   every ink path concatenated, or empty.
 * @param pathSizes points per ink path.
 */
@Keep
internal class NativeAnnotation(
    @JvmField val subtype: Int,
    @JvmField val index: Int,
    @JvmField val id: String?,
    @JvmField val contents: String?,
    @JvmField val color: Int,
    @JvmField val width: Float,
    @JvmField val rect: FloatArray,
    @JvmField val points: FloatArray,
    @JvmField val pathSizes: IntArray,
    @JvmField val hasAppearance: Boolean,
)

/** PDFium's `FPDF_ANNOT_*` subtypes used here. */
internal object AnnotSubtype {
    const val TEXT = 1
    const val HIGHLIGHT = 9
    const val UNDERLINE = 10
    const val STRIKEOUT = 12
    const val INK = 15

    /** Markup annotations a user may delete: Text through FileAttachment, minus Link and Popup. */
    val DELETABLE = setOf(1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17)
}

/** Sorts [natives] into our annotations and deletable foreign ones; the rest is dropped. */
internal fun toPageAnnotations(pageIndex: Int, natives: Array<NativeAnnotation>): PageAnnotations {
    val own = mutableListOf<Annotation>()
    val foreign = mutableListOf<ForeignAnnotation>()
    for (native in natives) {
        if (isOwnAnnotationId(native.id)) {
            native.toOwn(pageIndex)?.let(own::add)
        } else if (native.subtype in AnnotSubtype.DELETABLE) {
            foreign += ForeignAnnotation(
                pageIndex = pageIndex,
                key = native.id?.takeIf { it.isNotEmpty() } ?: "#${native.index}",
                subtype = native.subtype,
                bounds = native.bounds,
                contents = native.contents.orEmpty(),
                inkStrokes = if (native.subtype == AnnotSubtype.INK) native.strokes() else emptyList(),
            )
        }
    }
    return PageAnnotations(own, foreign)
}

private fun NativeAnnotation.toOwn(pageIndex: Int): Annotation? {
    val id = id!!
    val kind = when (subtype) {
        AnnotSubtype.HIGHLIGHT -> MarkupKind.Highlight
        AnnotSubtype.UNDERLINE -> MarkupKind.Underline
        AnnotSubtype.STRIKEOUT -> MarkupKind.StrikeOut
        else -> null
    }
    return when {
        kind != null -> TextMarkup(id, pageIndex, color, kind, quadRects(), contents.orEmpty())
        subtype == AnnotSubtype.INK -> Ink(id, pageIndex, color, width, strokes())
        subtype == AnnotSubtype.TEXT -> Note(id, pageIndex, color, PdfPoint(rect[0], rect[1]), contents.orEmpty())
        else -> null
    }
}

private val NativeAnnotation.bounds get() = PdfRect(rect[0], rect[1], rect[2], rect[3])

/** The bounding rect of each quad: corners can come in any order after rotation. */
private fun NativeAnnotation.quadRects(): List<PdfRect> = List(points.size / 8) { quad ->
    val xs = (0 until 4).map { points[quad * 8 + it * 2] }
    val ys = (0 until 4).map { points[quad * 8 + it * 2 + 1] }
    PdfRect(xs.min(), ys.min(), xs.max(), ys.max())
}

private fun NativeAnnotation.strokes(): List<List<PdfPoint>> {
    var offset = 0
    return pathSizes.map { size ->
        List(size) { i -> PdfPoint(points[(offset + i) * 2], points[(offset + i) * 2 + 1]) }
            .also { offset += size }
    }
}
