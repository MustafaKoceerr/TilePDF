package com.mustafakoceerr.tilepdf.model

/** Where the user left off in a document. */
data class ReadingPosition(
    val pageIndex: Int = 0,
    /** How far the top of the screen is into [pageIndex], `0..1`. */
    val pageOffsetFraction: Float = 0f,
    val zoom: Float = 1f,
    /**
     * Horizontal position when zoomed, as a fraction of how far the content
     * can pan (`0..1`), so it survives a change of screen width.
     */
    val panFraction: Float = 0f,
)
