package com.mustafakoceerr.tilepdf.data.text

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mustafakoceerr.tilepdf.data.pdfbox.PdfBoxDocumentLoader
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@SdkSuppress(minSdkVersion = 35)
@RunWith(AndroidJUnit4::class)
class HybridTextProviderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pdfBoxFactory = PdfBoxTextProvider.Factory(PdfBoxDocumentLoader(context, Dispatchers.IO), Dispatchers.IO)
    private val nativeFactory = NativePdfTextProvider.Factory(context, Dispatchers.IO)

    private val uri: String by lazy {
        val file = File(context.cacheDir, "benchmark.pdf")
        instrumentation.context.assets.open("benchmark.pdf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        Uri.fromFile(file).toString()
    }

    private fun hybrid(warmUpDelayMs: Long) =
        runBlocking { HybridTextProvider(nativeFactory.create(uri), pdfBoxFactory, uri, Dispatchers.IO, warmUpDelayMs) }

    @Test
    fun answersNativelyBeforeWarmUpAndFromCacheAfter(): Unit = runBlocking {
        val cold = hybrid(warmUpDelayMs = Long.MAX_VALUE)
        assertEquals(0, cold.warmedPageCount)
        val nativeCounts = QUERIES.map { cold.search(3, it).size }
        cold.close()

        val warm = hybrid(warmUpDelayMs = 0)
        warm.awaitWarmUp()
        assertEquals(80, warm.warmedPageCount)
        assertEquals(nativeCounts, QUERIES.map { warm.search(3, it).size })
        warm.close()
    }

    @Test
    fun closingMidWarmUpStopsCleanly(): Unit = runBlocking {
        val provider = hybrid(warmUpDelayMs = 0)
        provider.search(0, "tile")
        // Must cancel the extraction loop and release both documents without throwing.
        provider.close()
    }

    private companion object {
        val QUERIES = listOf("tile", "render engine", "memory")
    }
}
