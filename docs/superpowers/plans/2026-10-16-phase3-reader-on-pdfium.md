# Phase 3 — Reader on PDFium Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The reader opens, renders, searches, selects and shows the outline through PDFium only; PdfRenderer and PdfBox are deleted; tiles stop re-parsing their page.

**Architecture:** `PdfDocument` becomes an interface (implemented by `PdfiumDocument`) and `PdfEngine` an interface (implemented by `Pdfium`), so the render pipeline keeps its types and data/ViewModel code can be tested with fakes. The one open document serves rendering, text and outline. Text is extracted lazily per page and cached; search stays in Kotlin (`TextSearcher`, Turkish folding). Natively, each document keeps its most recently used pages loaded.

**Tech Stack:** as phases 1–2. Measurement with Perfetto / temporary timing only (no committed benchmarks).

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (phase 3 + engine decision)

## Global Constraints

- No `PdfRenderer`, no PdfBox, no `Build.VERSION` branches for engine code after this phase. Exception: test-only reference comparisons with `android.graphics.pdf.PdfRenderer` in `core/pdf/src/androidTest`.
- All PDFium calls stay on the single `@PdfDispatcher`.
- User-visible behavior unchanged except: permission loss is reported as "not found" (it was "password"), and no background text warm-up.
- `ReaderError` values and strings unchanged.

## Review Focus

1. **Password-protected file** must show `PasswordProtected`, not `Corrupt` — `PdfPasswordException` is an `IOException`. Task 1 `readerErrorFor` test.
2. **Revoked SAF permission** (`SecurityException`) must show `NotFound`. Task 1 test.
3. **Reader closed during a search**: the closed document throws `IllegalStateException`; the search flow must end quietly. Task 2 test `searchStopsWhenDocumentCloses`.
4. **Switching documents** must not serve the previous document's cached text. Task 2 test `anotherDocumentReplacesTheCache`.
5. **Native page cache vs close**: no cached `FPDF_PAGE` may outlive `FPDF_CloseDocument`. Task 4 test `cachedPagesCloseWithDocument` (open, render pages 0–2, close, reopen, render) plus every existing render/text test.

---

### Task 1: Engine interfaces; documents open with PDFium; error mapping

**Files:**
- Create: `core/pdf/src/main/java/com/mustafakoceerr/tilepdf/core/pdf/PdfEngine.kt`, `core/pdf/src/main/java/com/mustafakoceerr/tilepdf/di/PdfModule.kt`
- Modify: `PdfDocument.kt` (class → interface), `PdfiumDocument.kt`, `Pdfium.kt`, `core/data/.../document/{DocumentRepository,SafDocumentRepository}.kt`, `core/testing/.../TestAssets.kt`, `core/render/.../PageRenderer.kt` (KDoc), `core/common/.../DispatchersModule.kt` (KDoc)
- Delete: `PdfDocumentOpener.kt`
- Create: `feature/reader/src/main/java/com/mustafakoceerr/tilepdf/feature/reader/ReaderErrors.kt`; Modify `ReaderViewModel.kt`
- Move/rewrite: `core/render/src/androidTest/.../core/pdf/PdfDocumentTest.kt` opens via `TestAssets.openDocument`
- Create: `core/pdf/src/androidTest/.../PlatformReference.kt` (test-only `PdfRenderer` sizes + render) used by `PdfiumOpenTest.sizesMatchPdfRenderer` and `PdfiumRenderTest.matchesPdfRenderer`
- Test: `feature/reader/src/test/java/com/mustafakoceerr/tilepdf/feature/reader/ReaderErrorsTest.kt`

**Interfaces:**
- `interface PdfDocument : Closeable { val pageSizes: List<PageSize>; val pageCount: Int; suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix); suspend fun pageText(pageIndex: Int): PageText; suspend fun outline(): List<OutlineEntry> }` — `pageCount` default `get() = pageSizes.size`.
- `interface PdfEngine { suspend fun open(uri: Uri, password: String? = null): PdfDocument }`; `Pdfium : PdfEngine`; `PdfModule` binds it (`@Binds`, `SingletonComponent`).
- `DocumentRepository.open(uri: String, password: String? = null): PdfDocument`; KDoc lists `FileNotFoundException`, `SecurityException` (grant revoked), `PdfPasswordException`, `PdfCorruptException`.
- `internal fun readerErrorFor(error: Throwable): ReaderError?` — `PdfPasswordException → PasswordProtected`, `FileNotFoundException`/`SecurityException → NotFound`, other `IOException → Corrupt`, anything else `null` (rethrown by the caller).
- `TestAssets.openDocument(name)` uses `Pdfium` with `Dispatchers.IO.limitedParallelism(1)`.

- [ ] **Step 1: Write `ReaderErrorsTest`**: one test per mapping above, including `PdfPasswordException(wrongPassword = true)` and a plain `IOException`, and `IllegalStateException → null`.
- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest` — Expected: FAIL to compile.
- [ ] **Step 3: Implement** interfaces, binding, repository, `readerErrorFor` and the ViewModel catch (`catch (e: Exception) { readerErrorFor(e)?.let { Failed(it) } ?: throw e }`, keeping `CancellationException` propagating); rewrite the PdfRenderer-comparison tests on `PlatformReference`.
- [ ] **Step 4: Green build** `./gradlew assembleDebug assembleDebugAndroidTest test` and connected tests per device (`connected-each.sh`) — Expected: all pass; `MainActivitySmokeTest` passes.
- [ ] **Step 5: Commit** — `"Open documents with PDFium"`

### Task 2: Search and selection text from the open document

**Files:**
- Modify: `core/data/.../text/DocumentTextRepository.kt`, `CachingDocumentTextRepository.kt`, `core/data/.../di/DataModule.kt`, `ReaderViewModel.kt`
- Delete: `core/data/.../text/{PdfTextProvider,PdfBoxTextProvider,NativePdfTextProvider,HybridTextProvider}.kt`, `core/data/.../di/TextModule.kt`, `core/pdf/.../PdfRendererOpening.kt`, and tests `PdfBoxTextProviderTest`, `HybridTextProviderTest`
- Create: `core/data/src/test/java/com/mustafakoceerr/tilepdf/data/text/FakePdfDocument.kt`
- Test: `core/data/src/test/java/com/mustafakoceerr/tilepdf/data/text/CachingDocumentTextRepositoryTest.kt`

**Interfaces:**
- `interface DocumentTextRepository { fun search(document: PdfDocument, query: String): Flow<List<SearchMatch>>; suspend fun pageText(document: PdfDocument, pageIndex: Int): PageText; fun release(document: PdfDocument) }` — `prepare` is removed.
- `CachingDocumentTextRepository`: caches `PageText` per page for the current document (identity); a different document replaces the cache; `search` walks pages in order, extracting uncached ones via `document.pageText`, emitting each page's `TextSearcher.search` matches when non-empty; blank query emits nothing. Runs on `@IoDispatcher` for searching; extraction itself is on the PDF dispatcher inside the document.
- `FakePdfDocument(pages: List<String>)` (test source set): `pageText` builds the page with `PageText.Builder`, giving char `i` the box `PdfRect(i, 0, i + 1, 10)` so `TextSearcher` yields rects; `pageTextCalls` counts calls; after `close()`, every call throws `IllegalStateException`; `render` and `outline` are unused (`error("unused")`).

- [ ] **Step 1: Write `CachingDocumentTextRepositoryTest`** (Turbine for flows):
  - `searchEmitsMatchesPerPage`: pages `["a tile", "none", "tile tile"]`, query `"TILE"` → emissions `[1 match on page 0]`, `[2 matches on page 2]`.
  - `textIsExtractedOncePerPage`: two searches → `pageText` called once per page.
  - `anotherDocumentReplacesTheCache`: search doc A, then doc B with different text → B's matches only, and B's pages extracted.
  - `searchStopsWhenDocumentCloses`: close the fake mid-search → flow completes with an `IllegalStateException` the collector can catch (assert type).
  - `blankQueryEmitsNothing`.
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest` — Expected: FAIL to compile.
- [ ] **Step 3: Implement; update `ReaderViewModel` (pass `opened.document`; drop `prepareSearch`; `release(document)` in `onCleared`); delete the provider stack and `PdfRendererOpening.kt`.**
- [ ] **Step 4: Green build + connected per device** — Expected: pass. Manual: search "Motivation" in `outline.pdf` finds 1, Turkish `"ışaretleme"` matches `"Işaretleme"`.
- [ ] **Step 5: Commit** — `"Search and select text with PDFium"`

### Task 3: Outline from the document; remove PdfBox

**Files:**
- Modify: `ReaderViewModel.kt` (`opened.document.outline()`), `core/data/.../di/DataModule.kt`, `core/data/build.gradle.kts`, `gradle/libs.versions.toml`
- Delete: `core/data/.../outline/{DocumentOutlineRepository,PdfBoxOutlineRepository}.kt`, `core/data/.../pdfbox/PdfBoxDocumentLoader.kt`, `PdfBoxOutlineRepositoryTest.kt`; catalog `pdfbox` version and `pdfbox-android` library

- [ ] **Step 1: Delete and rewire.**
- [ ] **Step 2: Verify removal:** `grep -rn "pdfbox\|PdfBox\|PdfRenderer\|tom_roush" --include=*.kt --include=*.kts --include=*.toml . | grep -v /build/ | grep -v androidTest` — Expected: no matches. `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i pdfbox` — Expected: none.
- [ ] **Step 3: Green build + connected per device**; lint — Expected: the bouncycastle `TrustAllX509TrustManager` warnings are gone.
- [ ] **Step 4: Commit** — `"Read the outline with PDFium and remove PdfBox"`

### Task 4: Keep pages loaded; load text pages once

**Files:**
- Modify: `core/pdf/src/main/cpp/pdfium_jni.cpp`, `PdfiumNative.kt`, `PdfiumDocument.kt`
- Test: add to `core/pdf/src/androidTest/.../PdfiumRenderTest.kt` and `PdfiumTextTest.kt`

**Interfaces:**
- `NativeDocument` gains an LRU of up to **4** loaded `FPDF_PAGE`s (index → page); `nativeRender`, text extraction and `nativePageToTopLeft` take pages from it; `nativeCloseDocument` closes every cached page before `FPDF_CloseDocument`.
- `nativePageText(document, index): NativePageText?` replaces the two text natives: one `FPDFText_LoadPage` per call; `class NativePageText(val text: String, val boxes: FloatArray)` constructed from JNI (class and constructor cached in `JNI_OnLoad`).

- [ ] **Step 1: Measure before** on emulator-5554, not committed: a throwaway instrumented loop rendering all 12 tiles (256 px) of `text-dense.pdf` page 0 at 1080 px ×4, 20 iterations, median ms per page; and `pageText` for all 80 pages. Record numbers in the ledger.
- [ ] **Step 2: Write tests:** `cachedPagesCloseWithDocument` (Review Focus 5); `rendersEveryPageAfterCacheEviction` (render pages 0,1,2 of `outline.pdf` 0..11 then 0 again; all non-blank and page 0 equals its first render); `textMatchesAfterRender` (render page 0 then `pageText(0)` equals a fresh document's).
- [ ] **Step 3: Run** — Expected: new tests PASS already (behavioral guards; the cache must keep them passing) — this task's RED is the measurement baseline.
- [ ] **Step 4: Implement the cache and single-load text.**
- [ ] **Step 5: Run all core:pdf tests per device** — Expected: PASS.
- [ ] **Step 6: Measure after** with the same throwaway loop; Expected: lower median tile time. Record before/after in the ledger and the commit message; delete the loop.
- [ ] **Step 7: Commit** — `"Keep recently used pages loaded in PDFium"`

### Task 5: Docs; full verification

**Files:** `README.md` (rendering, text and testing sections: PDFium only; remove PdfBox/Hybrid strategy text), spec (phase 3 done notes if any wording changed), `DispatchersModule.kt` KDoc already in Task 1.

- [ ] **Step 1: Update docs.**
- [ ] **Step 2: Full green run:** `./gradlew assembleDebug assembleDebugAndroidTest test lint :app:assembleRelease` + connected per device.
- [ ] **Step 3: Manual on API 35 and API 29 emulators:** open `outline.pdf` via SAF, zoom-free scroll, TOC jump to "2.1 Karolar" lands on page 5, search, select-and-copy a word, night mode, back; library keeps the page. Open `encrypted.pdf` → password error screen.
- [ ] **Step 4: Commit** — `"Document the PDFium-only reader"`

## Deferred (with reasons)

- Progressive, interruptible native render: queued tile renders are already cancelled when tiles leave the screen (`TiledPageImageSource` cancels stale jobs; `Mutex.withLock` waiting is cancellable). Revisit only if Task 4's measurement shows single renders long enough to matter.
- Password entry UI: phase 4.
