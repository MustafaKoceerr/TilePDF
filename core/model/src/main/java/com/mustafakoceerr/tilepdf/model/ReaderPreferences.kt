package com.mustafakoceerr.tilepdf.model

/** How pages are coloured: as printed, inverted, or on warm paper. */
enum class PageTheme { Light, Night, Sepia }

/** Pages scroll top to bottom, or show one at a time side by side. */
enum class ReadingMode { Vertical, Horizontal }

/** Reader settings that apply to every document. */
data class ReaderPreferences(
    val pageTheme: PageTheme = PageTheme.Light,
    val readingMode: ReadingMode = ReadingMode.Vertical,
    val keepScreenOn: Boolean = false,
    /** Volume down turns to the next page, volume up to the previous one. */
    val volumeKeysTurnPages: Boolean = false,
    /** In horizontal layout on a wide screen, two pages side by side like a book. */
    val twoPageSpreads: Boolean = true,
    /** ARGB colour of new highlights, underlines and strikeouts. */
    val markupColor: Int = 0xFFFFEB3B.toInt(),
    /** ARGB colour of new pen strokes. */
    val penColor: Int = 0xFFE53935.toInt(),
    val penWidthPt: Float = 2f,
)
