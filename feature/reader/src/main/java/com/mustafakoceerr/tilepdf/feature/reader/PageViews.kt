package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.feature.reader.annotation.NOTE_ICON_PT
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.DocumentView
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup

/**
 * How each page of [sizes] is shown under [view]. [crops] are the pages'
 * content boxes, null until found; a null entry leaves that page whole.
 */
internal fun pageTransforms(sizes: List<PageSize>, view: DocumentView, crops: List<PdfRect?>?): List<PageViewTransform> =
    sizes.mapIndexed { index, size ->
        val crop = if (view.cropMargins) crops?.getOrNull(index) else null
        PageViewTransform.of(size, view.rotation, crop)
    }

/**
 * [crops] grown to hold TilePDF's own annotations, which the engine doesn't
 * draw and so the margin search can't see. A page left whole stays whole.
 */
internal fun cropsWithAnnotations(crops: List<PdfRect?>, own: List<Annotation>): List<PdfRect?> {
    val byPage = own.groupBy { it.pageIndex }
    return crops.mapIndexed { page, crop ->
        crop ?: return@mapIndexed null
        byPage[page].orEmpty().fold(crop) { box, annotation -> box.union(annotation.bounds()) }
    }
}

private fun Annotation.bounds(): PdfRect = when (this) {
    is TextMarkup -> rects.reduce(PdfRect::union)
    is Ink -> {
        val points = strokes.flatten()
        val half = widthPt / 2
        PdfRect(points.minOf { it.x } - half, points.minOf { it.y } - half, points.maxOf { it.x } + half, points.maxOf { it.y } + half)
    }
    is Note -> PdfRect(at.x, at.y, at.x + NOTE_ICON_PT, at.y + NOTE_ICON_PT)
}

/** One more quarter turn clockwise. */
internal fun nextRotation(rotation: Int): Int = (rotation + 90) % 360
