package com.mustafakoceerr.tilepdf.data.text

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mustafakoceerr.tilepdf.data.pdfbox.PdfBoxDocumentLoader
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlin.math.abs
import kotlin.system.measureNanoTime

private const val TAG = "TextBenchmark"
private const val PAGES = 80
private val QUERIES = listOf("tile", "render engine", "memory", "coordinate mapper", "native")

/**
 * Compares the two text strategies on the same 80-page document (45 lines of
 * text per page): cold search (includes extraction), warm search, and
 * whether both find the same matches in the same places.
 *
 * Results go to logcat under [TAG]; run on an API 35+ device or emulator.
 */
@SdkSuppress(minSdkVersion = 35)
@RunWith(AndroidJUnit4::class)
class TextProviderBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val uri: String by lazy { TestAssets.uriString("benchmark.pdf") }

    @Test
    fun providersAgreeAndReportTimings(): Unit = runBlocking {
        val pdfBoxFactory = PdfBoxTextProvider.Factory(PdfBoxDocumentLoader(context, Dispatchers.IO), Dispatchers.IO)
        val nativeFactory = NativePdfTextProvider.Factory(context, Dispatchers.IO)
        val pdfBox = measure("PdfBox", pdfBoxFactory)
        val native = measure("Native", nativeFactory)
        val hybrid = measure("Hybrid", HybridTextProvider.Factory(nativeFactory, pdfBoxFactory, Dispatchers.IO)) {
            (it as HybridTextProvider).awaitWarmUp()
        }

        for (other in listOf(native, hybrid)) {
            for (query in QUERIES) {
                val a = pdfBox.matches.getValue(query)
                val b = other.matches.getValue(query)
                assertEquals("${other.name} match count for '$query'", a.size, b.size)
                a.zip(b).forEach { (x, y) ->
                    assertEquals(x.pageIndex, y.pageIndex)
                    assertTrue("'$query' p${x.pageIndex}: $x vs $y", samePlace(x.rects.first(), y.rects.first()))
                }
            }
        }
        Log.i(TAG, "| Provider | Open | Cold search (5 queries) | Warm search (5 queries) |")
        Log.i(TAG, "|---|---|---|---|")
        listOf(pdfBox, native, hybrid).forEach {
            Log.i(TAG, "| ${it.name} | ${it.openMs} ms | ${it.coldMs} ms | ${it.warmMs} ms |")
        }
        Log.i(TAG, "Hybrid fully warmed ${hybrid.warmUpMs} ms after opening")
        Log.i(TAG, "matches per query: " + QUERIES.joinToString { "$it=${pdfBox.matches.getValue(it).size}" })
    }

    private class Result(
        val name: String,
        val openMs: Long,
        val coldMs: Long,
        val warmMs: Long,
        val matches: Map<String, List<SearchMatch>>,
        val warmUpMs: Long,
    )

    /**
     * Cold = first searches right after opening. [awaitReady] runs between
     * cold and warm, for providers that finish preparing in the background.
     */
    private suspend fun measure(
        name: String,
        factory: PdfTextProvider.Factory,
        awaitReady: suspend (PdfTextProvider) -> Unit = {},
    ): Result {
        lateinit var provider: PdfTextProvider
        val openedAt = System.nanoTime()
        val open = measureNanoTime { provider = factory.create(uri) }
        val matches = LinkedHashMap<String, List<SearchMatch>>()
        val cold = measureNanoTime { QUERIES.forEach { matches[it] = searchAll(provider, it) } }
        awaitReady(provider)
        val warmUp = System.nanoTime() - openedAt
        val warm = measureNanoTime { QUERIES.forEach { searchAll(provider, it) } }
        provider.close()
        return Result(name, open.ms, cold.ms, warm.ms, matches, warmUp.ms)
    }

    private suspend fun searchAll(provider: PdfTextProvider, query: String) =
        (0 until PAGES).flatMap { provider.search(it, query) }

    /**
     * Engines disagree slightly on glyph heights, so compare where the match
     * starts and which line it's on rather than exact boxes.
     */
    private fun samePlace(a: PdfRect, b: PdfRect): Boolean =
        abs(a.left - b.left) < 3f && abs(a.bottom - b.bottom) < 4f

    private val Long.ms get() = this / 1_000_000
}
