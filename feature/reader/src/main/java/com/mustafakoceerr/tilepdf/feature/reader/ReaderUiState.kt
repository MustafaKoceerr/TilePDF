package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationEditing
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationsUiState
import com.mustafakoceerr.tilepdf.feature.reader.components.PageJump
import com.mustafakoceerr.tilepdf.model.Bookmark

import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.DocumentView
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.Signature
import com.mustafakoceerr.tilepdf.model.SignatureCheck
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.core.render.ThumbnailSource
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.ReaderPreferences
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.model.SearchMatch

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Error(val reason: ReaderError) : ReaderUiState

    /** The document is encrypted; [wrongPassword] when the last try failed. */
    data class PasswordRequired(val wrongPassword: Boolean) : ReaderUiState

    data class Ready(
        val title: String,
        /** Pages as laid out: turned and cropped as [pageTransforms] say. */
        val pageSizes: List<PageSize>,
        val pageImages: PageImageSource,
        /** Small page images for the page grid. */
        val thumbnails: ThumbnailSource,
        /** Where to start; read once when the page list is first shown. */
        val initialPosition: ReadingPosition,
        val preferences: ReaderPreferences,
        val search: SearchUiState,
        val toc: TocUiState,
        val selection: SelectionUiState?,
        /** A jump the page list should make, e.g. after tapping a link. */
        val jump: PageJump? = null,
        /** The page the last jump left; offered as a way back while reading near its target. */
        val backToPage: Int? = null,
        /** A web or mail link the reader is asked about before it leaves the app. */
        val linkToConfirm: String? = null,
        /** A web or mail link to hand to another app. */
        val externalLink: String? = null,
        /** The page at the top of the screen. */
        val currentPage: Int = 0,
        /** Whether [currentPage] is bookmarked. */
        val isCurrentPageBookmarked: Boolean = false,
        /** Bars hidden; a tap on the page brings them back. */
        val isFullScreen: Boolean = false,
        val annotations: AnnotationsUiState = AnnotationsUiState(),
        val editing: AnnotationEditing = AnnotationEditing(),
        /** "Kopya olarak kaydet" finished; the screen opens the copy or says it failed. */
        val saveCopyResult: SaveCopyResult? = null,
        /** A document ready for "Paylaş" or "Yazdır"; the screen hands it over. */
        val handOff: HandOff? = null,
        /** How each page is shown; the document's own pages when untouched. */
        val pageTransforms: List<PageViewTransform> = pageSizes.map { PageViewTransform.of(it) },
        /** This document's rotation and margin cropping, for the menu. */
        val documentView: DocumentView = DocumentView(),
        /** An XFA form: annotation tools, signing and text recognition are off. */
        val isXfa: Boolean = false,
        /** Fillable widgets of the pages near the reader, by page; empty without a form. */
        val formFields: Map<Int, List<FormField>> = emptyMap(),
        /** A text or choice field being filled in. */
        val formEditor: FormField? = null,
        /** Adding a signature: drawing it, then a tap places it. */
        val signatureStep: SignatureStep? = null,
        val recognition: RecognitionUiState = RecognitionUiState(),
        /** A dialog the document's form scripts are waiting on. */
        val scriptDialog: ScriptDialog? = null,
        val signatures: SignaturesUiState = SignaturesUiState(),
    ) : ReaderUiState
}

/** Why a document didn't open; a wrong or missing password is asked for instead. */
enum class ReaderError {
    NotFound,

    /** The read grant was revoked; picking the file again restores it. */
    PermissionLost,
    Corrupt,
    OutOfMemory,
}

sealed interface SaveCopyResult {
    data class Saved(val uri: String) : SaveCopyResult
    data object Failed : SaveCopyResult
}

/** The document, annotations included, exported for another app at [uri]. */
sealed interface HandOff {
    data class Share(val uri: String, val title: String) : HandOff
    data class Print(val uri: String, val title: String, val pageCount: Int) : HandOff
    data object Failed : HandOff
}

data class TocUiState(
    /** Table of contents; empty until loaded, or if the PDF has none. */
    val entries: List<OutlineEntry> = emptyList(),
    val isOpen: Boolean = false,
    /** Entry the reader was in when the sheet opened, or -1 before the first. */
    val currentIndex: Int = -1,
    /** Bookmarked pages, in page order. */
    val bookmarks: List<Bookmark> = emptyList(),
)

/** Selected text on one page; positions in top-left PDF points. */
data class SelectionUiState(
    val pageIndex: Int,
    val text: String,
    /** One per line, for drawing. */
    val rects: List<PdfRect>,
    /** Boxes of the first and last selected characters, where the handles sit. */
    val startBox: PdfRect,
    val endBox: PdfRect,
)

enum class SelectionHandle { Start, End }

data class SearchUiState(
    /** Whether the search bar is shown. */
    val isActive: Boolean = false,
    val query: String = "",
    /** In document order; grows while [isSearching]. */
    val matches: List<SearchMatch> = emptyList(),
    /** Index into [matches] of the match being shown, or -1. */
    val currentIndex: Int = -1,
    val isSearching: Boolean = false,
) {
    val currentMatch: SearchMatch? get() = matches.getOrNull(currentIndex)
}

/** Where adding a signature is. */
sealed interface SignatureStep {
    /** Drawing it on the pad, from the [saved] one if any. */
    data class Pad(val saved: Signature?) : SignatureStep

    /** Waiting for a tap on the page to put [signature] there. */
    data class Place(val signature: Signature) : SignatureStep
}

/** Text recognition on scanned pages. */
data class RecognitionUiState(
    /** Asking which pages to recognise. */
    val choosing: Boolean = false,
    /** How far it got while it runs; null when it isn't running. */
    val progress: RecognitionProgress? = null,
    /** The shown page has no text, as a scanned page hasn't. */
    val currentPageHasNoText: Boolean = false,
    /** The last run's outcome, until the screen has shown it. */
    val result: RecognitionResult? = null,
)

sealed interface RecognitionResult {
    data class Done(val pagesWithText: Int) : RecognitionResult
    /** Every page asked for already had text, so none was recognised. */
    data class AlreadyHasText(val allPages: Boolean) : RecognitionResult
    data object Failed : RecognitionResult
}

/** The document's digital signatures, once checked; empty without any. */
data class SignaturesUiState(
    val checks: List<SignatureCheck> = emptyList(),
    /** The list is open. */
    val open: Boolean = false,
) {
    val summary: SignatureSummary? get() = signatureSummary(checks)
}
