package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocumentOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class TileRenderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val renderer = PageRenderer(Dispatchers.IO)
    private lateinit var document: PdfDocument

    @Before
    fun setUp() = runBlocking {
        val file = File(context.cacheDir, "sample.pdf")
        instrumentation.context.assets.open("sample.pdf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        document = PdfDocumentOpener(context, Dispatchers.IO.limitedParallelism(1))
            .open(Uri.fromFile(file))
    }

    @After
    fun tearDown() = document.close()

    @Test
    fun stitchedTilesMatchWholePageRender() = runBlocking {
        // 1300 px wide A4 page: 3 x 4 tiles with cropped right and bottom edges.
        val renderWidth = 1300
        val whole = renderer.renderPage(document, 0, renderWidth)
        val grid = TileGrid(0, renderWidth, whole.height)
        val tiles = grid.tilesCovering(PixelRect(0, 0, renderWidth, whole.height), prefetch = 0)
        assertEquals(grid.columns * grid.rows, tiles.size)

        val stitched = createBitmap(whole.width, whole.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stitched)
        for (tile in tiles) {
            val bitmap = renderer.renderTile(document, tile)
            assertEquals(tile.bounds.width, bitmap.width)
            assertEquals(tile.bounds.height, bitmap.height)
            canvas.drawBitmap(bitmap, tile.bounds.left.toFloat(), tile.bounds.top.toFloat(), null)
        }

        // Anti-aliasing along tile seams may differ slightly; content must not.
        val differing = countDifferingPixels(whole, stitched, tolerance = 32)
        val total = whole.width * whole.height
        assertTrue("$differing of $total pixels differ", differing < total / 1000)
        // And the tiles really contain the page: the black rectangle is there.
        assertTrue(countDifferingPixels(stitched, blank(stitched), tolerance = 32) > 0)
    }

    private fun blank(like: Bitmap) =
        createBitmap(like.width, like.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private fun countDifferingPixels(a: Bitmap, b: Bitmap, tolerance: Int): Int {
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return pa.indices.count { i ->
            abs(Color.red(pa[i]) - Color.red(pb[i])) > tolerance ||
                abs(Color.green(pa[i]) - Color.green(pb[i])) > tolerance ||
                abs(Color.blue(pa[i]) - Color.blue(pb[i])) > tolerance
        }
    }
}
