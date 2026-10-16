package com.mustafakoceerr.tilepdf.core.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Same expectations as PdfBoxOutlineRepositoryTest. */
@RunWith(AndroidJUnit4::class)
class PdfiumOutlineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, Dispatchers.IO.limitedParallelism(1))

    private fun outline(name: String) = runBlocking {
        pdfium.open(TestAssets.uri(name)).use { it.outline() }
    }

    @Test
    fun flattensTreeAndResolvesEveryDestinationKind() {
        assertEquals(
            listOf(
                // XYZ: y 742 from the bottom of an 842 pt page -> 100 from the top.
                OutlineEntry("1. Giriş", pageIndex = 0, topPt = 100f, depth = 0),
                OutlineEntry("1.1 Motivasyon ve Amaç", 0, 442f, 1),
                // Fit: no position, top of the page.
                OutlineEntry("2. Render Motoru", 3, null, 0),
                // Named destination through the catalog's name tree.
                OutlineEntry("2.1 Karolar", 4, 242f, 1),
                // GoTo action instead of a destination.
                OutlineEntry("3. Arama ve Işaretleme", 8, 542f, 0),
            ),
            outline("outline.pdf"),
        )
    }

    @Test
    fun documentWithoutOutlineHasNoEntries() {
        assertTrue(outline("sample.pdf").isEmpty())
    }
}
