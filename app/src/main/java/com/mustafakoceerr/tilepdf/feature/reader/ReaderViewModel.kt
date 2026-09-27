package com.mustafakoceerr.tilepdf.feature.reader

import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocumentOpener
import com.mustafakoceerr.tilepdf.core.render.BitmapCache
import com.mustafakoceerr.tilepdf.core.render.PageRenderer
import com.mustafakoceerr.tilepdf.navigation.ReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/** Budget for low-resolution whole-page drafts. */
private const val DRAFT_CACHE_BYTES = 24 * 1024 * 1024

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentOpener: PdfDocumentOpener,
    private val pageRenderer: PageRenderer,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReaderRoute>()

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var document: PdfDocument? = null
    private val draftCache = BitmapCache<Int>(DRAFT_CACHE_BYTES)

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val opened = documentOpener.open(route.uri.toUri())
                document = opened
                ReaderUiState.Ready(opened.pageSizes)
            } catch (_: FileNotFoundException) {
                ReaderUiState.Error(ReaderError.NotFound)
            } catch (_: SecurityException) {
                ReaderUiState.Error(ReaderError.PasswordProtected)
            } catch (_: IOException) {
                ReaderUiState.Error(ReaderError.Corrupt)
            }
        }
    }

    /** Returns an already rendered draft without suspending, if one is cached. */
    fun cachedDraft(pageIndex: Int, widthPx: Int): Bitmap? =
        draftCache.get(pageIndex)?.takeIf { it.width == widthPx }

    suspend fun draft(pageIndex: Int, widthPx: Int): Bitmap? {
        cachedDraft(pageIndex, widthPx)?.let { return it }
        val document = document ?: return null
        return pageRenderer.render(document, pageIndex, widthPx)
            .also { draftCache.put(pageIndex, it) }
    }

    override fun onCleared() {
        document?.close()
    }
}
