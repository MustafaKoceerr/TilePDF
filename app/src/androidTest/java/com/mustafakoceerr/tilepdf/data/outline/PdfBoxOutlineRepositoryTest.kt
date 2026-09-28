package com.mustafakoceerr.tilepdf.data.outline

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.data.pdfbox.PdfBoxDocumentLoader
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfBoxOutlineRepositoryTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = PdfBoxOutlineRepository(PdfBoxDocumentLoader(context, Dispatchers.IO), Dispatchers.IO)

    private fun asset(name: String): String {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file).toString()
    }

    @Test
    fun flattensTreeAndResolvesEveryDestinationKind() = runBlocking {
        val outline = repository.outline(asset("outline.pdf"))

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
            outline,
        )
    }

    @Test
    fun documentWithoutOutlineHasNoEntries() = runBlocking {
        assertTrue(repository.outline(asset("sample.pdf")).isEmpty())
    }
}
