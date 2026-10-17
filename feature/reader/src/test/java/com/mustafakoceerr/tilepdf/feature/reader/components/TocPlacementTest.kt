package com.mustafakoceerr.tilepdf.feature.reader.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TocPlacementTest {

    @Test
    fun tabletLandscapeUsesSide() = assertEquals(TocPlacement.Side, tocPlacement(1280f, 800f))

    @Test
    fun unfoldedLandscapeUsesSide() = assertEquals(TocPlacement.Side, tocPlacement(841f, 673f))

    @Test
    fun phoneLandscapeKeepsSheet() = assertEquals(TocPlacement.Sheet, tocPlacement(915f, 411f))

    @Test
    fun tabletPortraitKeepsSheet() = assertEquals(TocPlacement.Sheet, tocPlacement(800f, 1280f))

    @Test
    fun boundaries() {
        assertEquals(TocPlacement.Side, tocPlacement(840f, 480f))
        assertEquals(TocPlacement.Sheet, tocPlacement(839.9f, 480f))
        assertEquals(TocPlacement.Sheet, tocPlacement(840f, 479.9f))
    }
}
