package com.mustafakoceerr.tilepdf.data.text

import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.mustafakoceerr.tilepdf.core.text.TextSearcher
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Let the first pages render before background extraction competes for CPU. */
private const val WARM_UP_DELAY_MS = 1_000L

/**
 * Combines both strategies on API 35+, based on their measured strengths
 * (see TextProviderBenchmarkTest):
 * - **Native** search needs no extraction, so the first search is fast.
 * - **PdfBox** searches cached text, so repeated searches are much faster,
 *   and its [TextSearcher] also folds Turkish dotted/dotless i.
 *
 * Native answers right away while PdfBox extracts the whole document in the
 * background. [pageText] always goes through PdfBox, extracting on demand,
 * because only it reports character boxes (needed to select text). Each page switches to the PdfBox cache as soon as it has been
 * extracted, so a search during warm-up mixes both sources page by page.
 * Their boxes agree (both use top-left, crop-box-relative points), so
 * highlights look the same either way.
 *
 * If PdfBox can't read a document PDFium can, search stays native.
 */
@RequiresApi(35)
class HybridTextProvider(
    private val native: PdfTextProvider,
    pdfBoxFactory: PdfBoxTextProvider.Factory,
    uri: String,
    ioDispatcher: CoroutineDispatcher,
    warmUpDelayMs: Long = WARM_UP_DELAY_MS,
) : PdfTextProvider {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * Opened on first need: by the background warm-up, or earlier when a
     * caller needs character boxes. `null` if PdfBox can't read the file.
     */
    private val pdfBox: Deferred<PdfBoxTextProvider?> = scope.async(start = CoroutineStart.LAZY) {
        val provider = try {
            // Not cancellable halfway: a document opened but never stored
            // would leak its file handle and temp files.
            withContext(NonCancellable) { pdfBoxFactory.create(uri) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return@async null
        }
        if (!isActive) {
            withContext(NonCancellable) { provider.close() }
            throw CancellationException("Closed while opening")
        }
        provider
    }

    private val warmUp: Job = scope.launch {
        delay(warmUpDelayMs)
        val provider = pdfBox.await() ?: return@launch
        for (pageIndex in 0 until provider.pageCount) provider.pageText(pageIndex)
    }

    /** PdfBox when it's already open; never waits for it. */
    private val openPdfBox: PdfBoxTextProvider?
        get() = if (pdfBox.isCompleted && !pdfBox.isCancelled) {
            @OptIn(ExperimentalCoroutinesApi::class)
            pdfBox.getCompleted()
        } else {
            null
        }

    /** Uses PdfBox, extracting the page now if needed: only it has character boxes. */
    override suspend fun pageText(pageIndex: Int): PageText =
        pdfBox.await()?.pageText(pageIndex) ?: native.pageText(pageIndex)

    override suspend fun search(pageIndex: Int, query: String): List<SearchMatch> {
        val cached = openPdfBox?.cachedPageText(pageIndex)
        return if (cached != null) TextSearcher.search(cached, query) else native.search(pageIndex, query)
    }

    override suspend fun close() {
        warmUp.cancelAndJoin()
        pdfBox.cancel()
        val provider = try {
            pdfBox.await()
        } catch (_: CancellationException) {
            null // Never opened, or it closed itself when cancelled.
        }
        provider?.close()
        scope.cancel()
        native.close()
    }

    @VisibleForTesting
    internal suspend fun awaitWarmUp() = warmUp.join()

    @VisibleForTesting
    internal val warmedPageCount: Int
        get() = openPdfBox?.let { provider ->
            (0 until provider.pageCount).count { provider.cachedPageText(it) != null }
        } ?: 0

    @RequiresApi(35)
    class Factory @Inject constructor(
        private val nativeFactory: NativePdfTextProvider.Factory,
        private val pdfBoxFactory: PdfBoxTextProvider.Factory,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PdfTextProvider.Factory {

        override suspend fun create(uri: String): PdfTextProvider =
            HybridTextProvider(nativeFactory.create(uri), pdfBoxFactory, uri, ioDispatcher)
    }
}
