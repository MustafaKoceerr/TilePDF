package com.mustafakoceerr.tilepdf.data.outline

import com.mustafakoceerr.tilepdf.data.pdfbox.PdfBoxDocumentLoader
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Guards against malformed outlines whose sibling links loop forever; real
 * books stay far below this.
 */
private const val MAX_ENTRIES = 5_000

/**
 * Reads the outline with PdfBox. The platform PDF APIs have no outline
 * support at any API level, so this is the only source.
 */
class PdfBoxOutlineRepository @Inject constructor(
    private val loader: PdfBoxDocumentLoader,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentOutlineRepository {

    override suspend fun outline(uri: String): List<OutlineEntry> {
        val document = loader.load(uri)
        return withContext(ioDispatcher) {
            document.use { OutlineReader(it).read() }
        }
    }

    private class OutlineReader(private val document: PDDocument) {
        private val entries = mutableListOf<OutlineEntry>()
        private val pageIndices = HashMap<PDPage, Int>()

        fun read(): List<OutlineEntry> {
            val root = document.documentCatalog.documentOutline ?: return emptyList()
            addChildren(root, depth = 0)
            return entries
        }

        private fun addChildren(node: PDOutlineNode, depth: Int) {
            var item = node.firstChild
            while (item != null && entries.size < MAX_ENTRIES) {
                val target = try {
                    resolve(item.destination ?: (item.action as? PDActionGoTo)?.destination)
                } catch (_: IOException) {
                    null // A broken link skips this entry, not the whole outline.
                }
                val title = item.title?.trim().orEmpty()
                if (target != null && title.isNotEmpty()) {
                    entries += OutlineEntry(title, target.first, target.second, depth)
                }
                addChildren(item, depth + 1)
                item = item.nextSibling
            }
        }

        /** Page index and top-left y in points, or `null` if it points nowhere. */
        private fun resolve(destination: PDDestination?): Pair<Int, Float?>? {
            val pageDestination = when (destination) {
                is PDPageDestination -> destination
                is PDNamedDestination -> document.documentCatalog.findNamedDestinationPage(destination)
                else -> null
            } ?: return null

            val page = pageDestination.page
            val pageIndex = when {
                page != null -> pageIndexOf(page)
                // Remote-style destinations store a page number instead.
                else -> pageDestination.pageNumber.takeIf { it in 0 until document.numberOfPages }
            } ?: return null

            return pageIndex to (pageDestination as? PDPageXYZDestination)?.let { topOf(it, pageIndex) }
        }

        private fun pageIndexOf(page: PDPage): Int? =
            pageIndices.getOrPut(page) { document.pages.indexOf(page) }.takeIf { it >= 0 }

        /**
         * XYZ destinations give y in PDF user space (bottom-left origin);
         * the app works in top-left points relative to the crop box.
         * PdfBox reports a missing y as -1.
         */
        private fun topOf(destination: PDPageXYZDestination, pageIndex: Int): Float? {
            val top = destination.top
            if (top < 0) return null
            val cropBox = document.getPage(pageIndex).cropBox
            return (cropBox.upperRightY - top).coerceIn(0f, cropBox.height)
        }
    }
}
