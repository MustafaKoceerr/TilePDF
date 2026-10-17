package com.mustafakoceerr.tilepdf.feature.reader.annotation

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.feature.reader.components.recolor
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.TextMarkup

private const val HIGHLIGHT_ALPHA = 0.4f
private const val LINE_WIDTH_PT = 1.5f

/*
 * TilePDF's annotations as the reader draws them, over a page laid out
 * size.width pixels wide. Positions are top-left points scaled like search
 * highlights.
 */

internal fun DrawScope.drawAnnotations(annotations: List<Annotation>, pageSize: PageSize, theme: PageTheme) {
    val scale = size.width / pageSize.widthPt
    annotations.forEach { annotation ->
        val color = Color(theme.recolor(annotation.color))
        when (annotation) {
            is TextMarkup -> drawMarkup(annotation, color, scale)
            is Ink -> annotation.strokes.forEach { drawStrokePoints(it, color, annotation.widthPt * scale, scale) }
            is Note -> drawNote(annotation, color, scale)
        }
    }
}

/** A stroke still being drawn, in the pen's colour. */
internal fun DrawScope.drawLiveStroke(points: List<PdfPoint>, color: Int, widthPt: Float, pageSize: PageSize, theme: PageTheme) {
    val scale = size.width / pageSize.widthPt
    drawStrokePoints(points, Color(theme.recolor(color)), widthPt * scale, scale)
}

private fun DrawScope.drawMarkup(markup: TextMarkup, color: Color, scale: Float) {
    markup.rects.forEach { rect ->
        val left = rect.left * scale
        val right = rect.right * scale
        val top = rect.top * scale
        val bottom = rect.bottom * scale
        when (markup.kind) {
            MarkupKind.Highlight ->
                drawRect(color.copy(alpha = HIGHLIGHT_ALPHA), Offset(left, top), Size(right - left, bottom - top))
            MarkupKind.Underline -> {
                val y = bottom - (bottom - top) * 0.1f
                drawLine(color, Offset(left, y), Offset(right, y), LINE_WIDTH_PT * scale)
            }
            MarkupKind.StrikeOut -> {
                val y = (top + bottom) / 2
                drawLine(color, Offset(left, y), Offset(right, y), LINE_WIDTH_PT * scale)
            }
        }
    }
}

private fun DrawScope.drawStrokePoints(points: List<PdfPoint>, color: Color, widthPx: Float, scale: Float) {
    when (points.size) {
        0 -> Unit
        1 -> drawCircle(color, widthPx / 2, Offset(points[0].x * scale, points[0].y * scale))
        else -> {
            val path = Path().apply {
                moveTo(points[0].x * scale, points[0].y * scale)
                for (i in 1 until points.size) lineTo(points[i].x * scale, points[i].y * scale)
            }
            drawPath(path, color, style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A coloured square with a folded look: three lines of "text" on it. */
private fun DrawScope.drawNote(note: Note, color: Color, scale: Float) {
    val side = NOTE_ICON_PT * scale
    val topLeft = Offset(note.at.x * scale, note.at.y * scale)
    val corner = CornerRadius(side * 0.15f)
    drawRoundRect(color, topLeft, Size(side, side), corner)
    drawRoundRect(lerp(color, Color.Black, 0.4f), topLeft, Size(side, side), corner, style = Stroke(scale))
    val lineColor = lerp(color, Color.Black, 0.55f)
    for (i in 1..3) {
        val y = topLeft.y + side * i / 4.5f
        drawLine(lineColor, Offset(topLeft.x + side * 0.2f, y), Offset(topLeft.x + side * 0.8f, y), scale)
    }
}
