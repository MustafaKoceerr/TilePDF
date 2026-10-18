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
)
