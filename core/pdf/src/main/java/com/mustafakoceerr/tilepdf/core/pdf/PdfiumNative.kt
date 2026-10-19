package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap

/**
 * Raw entry points of libtilepdf_pdfium. Callers must run on the PDF
 * dispatcher: PDFium keeps global state and is not thread-safe. Document
 * handles are only valid until [nativeCloseDocument].
 */
internal object PdfiumNative {

    init {
        System.loadLibrary("tilepdf_pdfium")
    }

    /**
     * Initializes PDFium once per process; later calls do nothing. Form fields
     * draw letters their own font lacks with [formFallbackFont], a TrueType
     * font's bytes, when given.
     */
    @JvmStatic
    external fun nativeInit(formFallbackFont: ByteArray?)

    /**
     * Opens the PDF readable through [fd] (which must stay open until the
     * document is closed). Returns a non-zero handle, or 0 with PDFium's error
     * code in `error[0]`. Handles may be negative: arm64 tags heap pointers.
     * With [hideOwn], TilePDF's annotations are hidden as pages load; such a
     * document must never be saved.
     */
    @JvmStatic
    external fun nativeOpen(fd: Int, size: Long, password: String?, hideOwn: Boolean, error: IntArray): Long

    @JvmStatic
    external fun nativeCloseDocument(document: Long)

    @JvmStatic
    external fun nativePageCount(document: Long): Int

    /** `[width, height]` in points with page rotation applied; empty on failure. */
    @JvmStatic
    external fun nativePageSize(document: Long, index: Int): FloatArray

    /**
     * Renders page [index] into an ARGB_8888 [bitmap] without clearing it.
     * [matrix] is PDFium's `{a, b, c, d, e, f}` from top-left page points to
     * bitmap pixels. Returns false if the page or bitmap couldn't be used.
     */
    @JvmStatic
    external fun nativeRender(document: Long, index: Int, bitmap: Bitmap, matrix: FloatArray): Boolean

    /**
     * The page's text and character boxes, loading its text page once; null
     * if the page has no text layer.
     */
    @JvmStatic
    external fun nativePageText(document: Long, index: Int): NativePageText?

    /** First child of [parent] (0 = outline root); 0 if there is none. */
    @JvmStatic
    external fun nativeBookmarkFirstChild(document: Long, parent: Long): Long

    /** Next sibling of [bookmark]; 0 if there is none. */
    @JvmStatic
    external fun nativeBookmarkNextSibling(document: Long, bookmark: Long): Long

    @JvmStatic
    external fun nativeBookmarkTitle(bookmark: Long): String

    /**
     * `[pageIndex, x, y]` in PDF user space; pageIndex is -1 if the bookmark
     * points nowhere, y is NaN unless it is an XYZ destination with a y.
     * Loads no page.
     */
    @JvmStatic
    external fun nativeBookmarkTarget(document: Long, bookmark: Long): FloatArray

    /**
     * Maps `[x0, y0, x1, y1, ...]` in page [index]'s user space to top-left
     * points. Loads the page; null if it can't be loaded.
     */
    @JvmStatic
    external fun nativePageToTopLeft(document: Long, index: Int, points: FloatArray): FloatArray?

    /** Closes every page the document keeps loaded; they reload on demand. */
    @JvmStatic
    external fun nativeTrim(document: Long)

    @JvmStatic
    external fun nativeLoadedPageCount(document: Long): Int

    /**
     * `[pageIndex, userX, userY]` of the GoTo link at top-left ([x], [y]) on
     * page [index]; userY is NaN without a position. Null if there is none.
     */
    @JvmStatic
    external fun nativeLinkTargetAt(document: Long, index: Int, x: Float, y: Float): FloatArray?

    /** The URI of the link at top-left ([x], [y]) on page [index], or null. */
    @JvmStatic
    external fun nativeLinkUriAt(document: Long, index: Int, x: Float, y: Float): String?

    /**
     * Every annotation on page [index] (null elements couldn't be read), or
     * null if the page can't be loaded.
     */
    @JvmStatic
    external fun nativePageAnnotations(document: Long, index: Int): Array<NativeAnnotation?>?

    /** Hides or shows the foreign annotation with [key] in memory; false if there is none. */
    @JvmStatic
    external fun nativeSetForeignHidden(document: Long, index: Int, key: String, hidden: Boolean): Boolean

    @JvmStatic
    external fun nativeAnnotationCount(document: Long, index: Int): Int

    /** `/NM` of TilePDF's annotations on page [index]; null if the page can't load. */
    @JvmStatic
    external fun nativeOwnAnnotationIds(document: Long, index: Int): Array<String>?

    /** Keys (`/NM` or `#index`) of deletable foreign annotations; null if the page can't load. */
    @JvmStatic
    external fun nativeForeignKeys(document: Long, index: Int): Array<String>?

    /**
     * Removes TilePDF's annotations [ownIds] and the foreign ones with
     * [foreignKeys] from page [index], each with its popup.
     */
    @JvmStatic
    external fun nativeRemoveAnnotations(document: Long, index: Int, ownIds: Array<String>, foreignKeys: Array<String>): Boolean

    /** [quads]: 8 top-left floats per line rect (TL, TR, BL, BR corners). */
    @JvmStatic
    external fun nativeAddMarkup(
        document: Long, index: Int, subtype: Int, color: Int, quads: FloatArray, id: String, contents: String,
    ): Boolean

    /** [points]: every path's top-left points in turn; [pathSizes]: points per path. */
    @JvmStatic
    external fun nativeAddInk(
        document: Long, index: Int, color: Int, width: Float, points: FloatArray, pathSizes: IntArray, id: String,
    ): Boolean

    /** A note whose 20 pt icon has its top-left corner at ([x], [y]). */
    @JvmStatic
    external fun nativeAddNote(document: Long, index: Int, color: Int, x: Float, y: Float, id: String, contents: String): Boolean

    /** Renders page [index] once so PDFium writes missing appearance streams. */
    @JvmStatic
    external fun nativeGenerateAppearances(document: Long, index: Int)

    /** Writes the original bytes plus the changes to [fd]. */
    @JvmStatic
    external fun nativeSaveIncremental(document: Long, fd: Int): Boolean

    /** PDFium's `FPDF_ERR_PASSWORD`. */
    const val ERROR_PASSWORD = 4

    /** Whether the document has an AcroForm. */
    external fun nativeHasForm(document: Long): Boolean

    /** The fillable widgets of page [index]; null if it can't be loaded. */
    external fun nativeFormFields(document: Long, index: Int): Array<NativeFormField?>?

    /** Replaces a text field widget's text as typing would. */
    external fun nativeSetFormText(document: Long, index: Int, annotIndex: Int, text: String): Boolean

    /** Turns a check box or radio button widget on or off with a click. */
    external fun nativeSetFormChecked(document: Long, index: Int, annotIndex: Int, checked: Boolean): Boolean

    /** Chooses option [optionIndex] of a combo or list box widget. */
    external fun nativeSetFormChoice(document: Long, index: Int, annotIndex: Int, optionIndex: Int): Boolean

    /**
     * Adds recognised words to page [index] as invisible text in the
     * fallback font. [quads] holds each word's four top-left corners in
     * reading order as x, y pairs, 8 floats per word.
     */
    @JvmStatic
    external fun nativeAddRecognizedText(document: Long, index: Int, texts: Array<String>, quads: FloatArray): Boolean

    /**
     * Lets [host] answer the document's form scripts (null: default answers).
     * With [openAction], the document's open action runs then, once.
     */
    @JvmStatic
    external fun nativeSetScriptHost(document: Long, host: FormScriptHost?, openAction: Boolean)

    /**
     * Adds a stamp named [name] that looks like a signature box: a frame
     * around [rect] (left, top, right, bottom), [texts] at [textPlaces]
     * (x, baseline, size each) and strokes ([strokeSizes] points each), all
     * in top-left points.
     */
    @JvmStatic
    external fun nativeAddSignatureStamp(
        document: Long,
        index: Int,
        rect: FloatArray,
        name: String,
        texts: Array<String>,
        textPlaces: FloatArray,
        strokes: FloatArray,
        strokeSizes: IntArray,
        strokeWidth: Float,
    ): Boolean

    /** The document's digital signatures; empty without any. */
    @JvmStatic
    external fun nativeSignatures(document: Long): Array<NativeSignature?>?

    /** Whether the document is an XFA form whose pages PDFium lays out itself. */
    @JvmStatic
    external fun nativeIsXfa(document: Long): Boolean

    /**
     * Taps top-left point ([x], [y]) of page [index] as a finger would.
     * Returns the text of the field it focused if that's a text field.
     */
    @JvmStatic
    external fun nativeFormTap(document: Long, index: Int, x: Float, y: Float): String?

    /** Replaces the text of the focused field on page [index] and commits it. */
    @JvmStatic
    external fun nativeFormReplaceText(document: Long, index: Int, text: String): Boolean

    /** Whether page [index] has text TilePDF recognised. */
    @JvmStatic
    external fun nativeHasRecognizedText(document: Long, index: Int): Boolean
}
