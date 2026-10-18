package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.feature.reader.SelectionUiState
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup
import com.mustafakoceerr.tilepdf.model.newAnnotationId
import kotlin.math.hypot

/*
 * Where annotations are, in top-left page points: what a tap hits and what
 * the eraser crosses. Zoom doesn't matter here, so tolerances are in points.
 */

/** Side of a note's icon, as PDFium sizes it. */
internal const val NOTE_ICON_PT = 20f

/** PDFium's ink subtype, the only foreign kind the eraser removes. */
private const val INK_SUBTYPE = 15

/** An annotation a tap landed on: TilePDF's, or another app's. */
sealed interface AnnotationTarget {
    data class Own(val annotation: Annotation) : AnnotationTarget
    data class Foreign(val annotation: ForeignAnnotation) : AnnotationTarget
}

/** What one eraser gesture removes. */
data class Erased(val ids: Set<String>, val refs: Set<ForeignRef>) {
    operator fun plus(other: Erased) = Erased(ids + other.ids, refs + other.refs)

    val isEmpty: Boolean get() = ids.isEmpty() && refs.isEmpty()

    companion object {
        val None = Erased(emptySet(), emptySet())
    }
}

/** A highlight, underline or strikeout over [selection]. */
fun markupFor(selection: SelectionUiState, kind: MarkupKind, color: Int, id: String = newAnnotationId()): TextMarkup =
    TextMarkup(id, selection.pageIndex, color, kind, selection.rects, selection.text)

/** The topmost annotation at [point] on [pageIndex]: the newest of ours first, then other apps'. */
fun annotationAt(
    own: List<Annotation>,
    foreign: List<ForeignAnnotation>,
    pageIndex: Int,
    point: PdfPoint,
    tolerance: Float = 6f,
): AnnotationTarget? {
    own.lastOrNull { it.pageIndex == pageIndex && it.contains(point, tolerance) }
        ?.let { return AnnotationTarget.Own(it) }
    foreign.lastOrNull { it.pageIndex == pageIndex && it.contains(point, tolerance) }
        ?.let { return AnnotationTarget.Foreign(it) }
    return null
}

/** The pen strokes, ours and other apps', that the eraser moving [from] → [to] crosses. */
fun erasedBy(
    own: List<Annotation>,
    foreign: List<ForeignAnnotation>,
    pageIndex: Int,
    from: PdfPoint,
    to: PdfPoint,
    radius: Float = 8f,
): Erased = Erased(
    ids = own.filterIsInstance<Ink>()
        .filter { it.pageIndex == pageIndex && it.strokes.any { s -> s.comesWithin(radius + it.widthPt / 2, from, to) } }
        .mapTo(HashSet()) { it.id },
    refs = foreign
        .filter { it.pageIndex == pageIndex && it.subtype == INK_SUBTYPE && it.inkStrokes.any { s -> s.comesWithin(radius, from, to) } }
        .mapTo(HashSet()) { it.ref },
)

private fun Annotation.contains(point: PdfPoint, tolerance: Float): Boolean = when (this) {
    is TextMarkup -> rects.any { it.inflate(tolerance).contains(point) }
    is Ink -> strokes.any { stroke -> stroke.distanceTo(point) <= widthPt / 2 + tolerance }
    is Note -> PdfRect(at.x, at.y, at.x + NOTE_ICON_PT, at.y + NOTE_ICON_PT).inflate(tolerance).contains(point)
}

private fun ForeignAnnotation.contains(point: PdfPoint, tolerance: Float): Boolean =
    if (subtype == INK_SUBTYPE && inkStrokes.isNotEmpty()) {
        inkStrokes.any { it.distanceTo(point) <= 1 + tolerance }
    } else {
        bounds.inflate(tolerance).contains(point)
    }

private fun PdfRect.inflate(by: Float) = PdfRect(left - by, top - by, right + by, bottom + by)

private fun PdfRect.contains(point: PdfPoint) = point.x in left..right && point.y in top..bottom

/** Shortest distance from [point] to this polyline; a single point is a dot. */
private fun List<PdfPoint>.distanceTo(point: PdfPoint): Float = when (size) {
    0 -> Float.MAX_VALUE
    1 -> distance(point, first())
    else -> zipWithNext { a, b -> segmentDistance(point, a, b) }.min()
}

/** Whether this polyline comes within [radius] of segment [from] → [to]. */
private fun List<PdfPoint>.comesWithin(radius: Float, from: PdfPoint, to: PdfPoint): Boolean = when (size) {
    0 -> false
    1 -> segmentDistance(first(), from, to) <= radius
    else -> zipWithNext().any { (a, b) -> segmentsDistance(a, b, from, to) <= radius }
}

private fun distance(a: PdfPoint, b: PdfPoint) = hypot(a.x - b.x, a.y - b.y)

private fun segmentDistance(p: PdfPoint, a: PdfPoint, b: PdfPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return distance(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return distance(p, PdfPoint(a.x + t * dx, a.y + t * dy))
}

/** Distance between two segments: 0 if they cross, else the closest endpoint distance. */
private fun segmentsDistance(a: PdfPoint, b: PdfPoint, c: PdfPoint, d: PdfPoint): Float {
    if (cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0) return 0f
    return minOf(segmentDistance(a, c, d), segmentDistance(b, c, d), segmentDistance(c, a, b), segmentDistance(d, a, b))
}

private fun cross(o: PdfPoint, a: PdfPoint, b: PdfPoint) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
