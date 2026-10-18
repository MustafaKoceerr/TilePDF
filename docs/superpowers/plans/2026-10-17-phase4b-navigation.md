# Phase 4b — Navigation: Links, Bookmarks, Thumbnails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a link in a PDF jumps to its target page or opens a web/mail link; users bookmark pages and see them next to the table of contents; a thumbnail grid jumps to any page.

**Architecture:** PDFium reads the link under a tap (`PdfDocument.linkAt`), returning a typed `PdfLink`. A pure `linkAction` decides what the reader does (jump, open in browser, or ignore unsafe schemes). Bookmarks are a new Room table (v3, auto-migration) behind `BookmarkRepository`. Jumps from links, bookmarks and thumbnails reuse the existing `PageJump` path. Thumbnails render through `PageRenderer.renderPage` with a small byte-bounded cache, one item at a time as the grid shows them.

**Tech Stack:** as before.

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (phase 4: küçük resim ızgarası, yer imleri, iç/dış linkler; PDFium link reading moved here)

## Global Constraints

- External links open only for `http`, `https` and `mailto`; everything else (`javascript:`, `file:`, `intent:`, `content:` …) is ignored. Opening uses `ACTION_VIEW`; no app to handle it shows a snackbar, never a crash.
- Link hit-testing uses the same top-left point space as selection (`pagePointAt`).
- A tap on a link while text is selected only clears the selection (one gesture, one effect).
- Bookmarks are per document URI; removing a document from the library removes its bookmarks (`ForeignKey` with `CASCADE`).
- Thumbnail cache ≤ 16 MB; thumbnails are 1/3 of the screen width or less.

## Review Focus

1. **Link at a zoomed/panned position** hits the right link. Task 2 manual check at 2× zoom + `LinkActionTest` geometry independent; engine test uses page points.
2. **`javascript:`/`intent:` link** is ignored. Task 2 `LinkActionTest`.
3. **Database upgrade 2→3** keeps existing library rows. Task 3 `TilePdfDatabaseMigrationTest.migrate2To3KeepsLibrary`.
4. **Bookmark on a page index beyond the document** (stale after the file changed) is dropped from the list. Task 3 repository test.
5. **Thumbnail grid on a 500-page document** renders only visible items; scrolling away cancels work. Task 4 `ThumbnailGridTest.offscreenItemsAreNotRequested`.

---

### Task 1: Read links with PDFium

**Files:**
- Modify: `tools/generate_test_pdfs.py` (new `links.pdf`), `core/pdf/src/main/cpp/pdfium_jni.cpp`, `PdfiumNative.kt`, `PdfDocument.kt`, `PdfiumDocument.kt`; fakes implementing `PdfDocument` (`FakePdfDocument`, `OpenDocumentUseCaseTest`)
- Create: `core/pdf/src/main/java/com/mustafakoceerr/tilepdf/core/pdf/PdfLink.kt`
- Test: `core/pdf/src/androidTest/.../PdfiumLinkTest.kt`

**Interfaces:**
- `sealed interface PdfLink { data class GoTo(val pageIndex: Int, val topPt: Float?) : PdfLink; data class Uri(val uri: String) : PdfLink }`
- `suspend fun PdfDocument.linkAt(pageIndex: Int, x: Float, y: Float): PdfLink?` — `x, y` in top-left points.
- Natives: `nativeLinkTargetAt(doc, index, x, y): FloatArray?` → `[pageIndex, userX, userY]` (`userY` NaN if none; `null` if no GoTo link there); `nativeLinkUriAt(doc, index, x, y): String?`. Point conversion top-left → page space with `FPDF_DeviceToPage` (same ×64 scheme). GoTo `topPt` via existing `nativePageToTopLeft`, coerced to the page height.
- `links.pdf`: 3 A4 pages. Page 1: "Go to page 3" at y 742 with a `/Link` `/Rect [72 730 320 760]` `/Dest [page3 /XYZ 0 642 0]`; "Website" `/Rect [72 630 320 660]` `/A << /S /URI /URI (https://example.com/tilepdf) >>`; "Script" `/Rect [72 530 320 560]` `/URI (javascript:alert(1))`.

- [ ] **Step 1: Tests** — `goToLinkResolvesPageAndPosition` (tap (100, 97) top-left on page 0 → `GoTo(2, 200f ±1)`), `uriLinkReturnsUri` (→ `Uri("https://example.com/tilepdf")`), `javascriptUriIsReturnedRaw` (engine doesn't filter), `noLinkReturnsNull` (tap (500, 800)).
- [ ] **Step 2: Run** — FAIL to compile. **Step 3: Implement**; regenerate fixtures (existing ones byte-identical). **Step 4: Run per device** — PASS. **Step 5: Commit** `"Read links with PDFium"`.

### Task 2: Follow links from the reader

**Files:**
- Create: `feature/reader/src/main/java/.../LinkAction.kt`; Test: `feature/reader/src/test/.../LinkActionTest.kt`
- Modify: `ReaderViewModel.kt` (`onTap(pageIndex, x, y)`, `ReaderUiState.Ready.jump: PageJump?`, `externalLink: String?`, `onJumpHandled()`, `onExternalLinkHandled()`), `ReaderScreen.kt`, `PdfPageList.kt` (`onTap` reports page point via `pagePointAt`), reader strings (`reader_no_app_for_link` = "Bu bağlantıyı açacak uygulama yok")

**Interfaces:**
- `internal sealed interface LinkAction { data class Jump(val jump: PageJump); data class Open(val uri: String); data object Ignore }`; `internal fun linkAction(link: PdfLink?, pageCount: Int): LinkAction` — GoTo in range → Jump; Uri with scheme `http`/`https`/`mailto` (case-insensitive) → Open; else Ignore.
- Tap order: selection active → clear only; else `linkAt` → action.

- [ ] **Step 1: `LinkActionTest`** — http/https/mailto open; `JavaScript:`/`intent:`/`file:`/`content:`/blank ignored; GoTo out of range ignored; GoTo in range jumps with topPt; `null` ignored.
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** (screen launches `Intent.ACTION_VIEW` for Open; `ActivityNotFoundException` → snackbar). **Step 4:** JVM + per-device green; **manual** on emulator-5554: `links.pdf` tap "Go to page 3" → page 3 near its top; at 2× zoom panned right the tap still hits; "Website" opens the browser; "Script" does nothing. **Step 5: Commit** `"Follow links in PDFs"`.

### Task 3: Bookmarks

**Files:**
- Create: `core/database/.../data/local/BookmarkEntity.kt`, `BookmarkDao.kt`; `core/data/.../data/bookmark/{BookmarkRepository,OfflineBookmarkRepository}.kt`; `core/model/.../model/Bookmark.kt`
- Modify: `TilePdfDatabase.kt` (version 3, `AutoMigration(2, 3)`, new entity), `DatabaseModule.kt`, `DataModule.kt`; `ReaderViewModel.kt`, `ReaderUiState.kt`, `ReaderScreen.kt`, `TableOfContentsSheet.kt` (two tabs: "İçindekiler" / "Yer imleri"), reader strings (`reader_bookmarks` = "Yer imleri", `reader_bookmark_add` = "Yer imi ekle", `reader_bookmark_remove` = "Yer imini kaldır", `reader_no_bookmarks` = "Henüz yer imi yok", `reader_contents` = "İçindekiler")
- Test: `core/database/src/androidTest/.../TilePdfDatabaseMigrationTest.kt` (+`migrate2To3KeepsLibrary`), `BookmarkDaoTest.kt`; `core/data/src/test/.../OfflineBookmarkRepositoryTest.kt` (fake DAO)

**Interfaces:**
- `BookmarkEntity(uri: String, pageIndex: Int, createdAt: Long)`, primary key `(uri, pageIndex)`, `ForeignKey(DocumentEntity, uri → uri, onDelete = CASCADE)`, index on `uri`.
- `BookmarkDao`: `observe(uri): Flow<List<BookmarkEntity>>` ordered by `pageIndex`; `insert`; `delete(uri, pageIndex)`.
- `data class Bookmark(val pageIndex: Int)`; `BookmarkRepository.observe(uri: String, pageCount: Int): Flow<List<Bookmark>>` (drops `pageIndex >= pageCount`); `toggle(uri: String, pageIndex: Int)`.
- Reader: menu item toggles the bookmark of the current page (label depends on state); sheet "Yer imleri" tab lists "Sayfa N", tap → `PageJump(N-1)`.

- [ ] **Step 1: Tests** — migration 2→3 keeps rows; DAO cascade on document delete; repository drops out-of-range and toggles add/remove.
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**; schema `3.json` exported. **Step 4:** green; manual: bookmark page 5 of `outline.pdf`, reopen app, tab lists "Sayfa 5", tap jumps. **Step 5: Commit** `"Bookmark pages"`.

### Task 4: Thumbnail grid

**Files:**
- Create: `feature/reader/.../components/ThumbnailGrid.kt`, `core/render/.../ThumbnailSource.kt`; Test: `feature/reader/src/androidTest/.../components/ThumbnailGridTest.kt`, `core/render/src/androidTest/.../ThumbnailSourceTest.kt`
- Modify: `ReaderScreen.kt` (top bar action "Sayfalar" opens a full-screen dialog with the grid), `ReaderViewModel.kt` (exposes a `ThumbnailSource` in `Ready`), `core:designsystem` icon `ic_grid` + `TilePdfIcons.Grid`, reader string `reader_pages` = "Sayfalar"

**Interfaces:**
- `class ThumbnailSource(document: PdfDocument, renderer: PageRenderer, widthPx: Int, cacheBytes: Int = 16 MB) { suspend fun thumbnail(pageIndex: Int): ImageBitmap }` — cached by page; `trimMemory()` clears.
- `@Composable internal fun ThumbnailGrid(pageSizes: List<PageSize>, currentPage: Int, thumbnail: suspend (Int) -> ImageBitmap, onSelect: (Int) -> Unit)` — `LazyVerticalGrid(GridCells.Adaptive(112.dp))`, each item loads in its own `LaunchedEffect(pageIndex)` (cancelled when scrolled away), shows "N" under it, current page outlined; initial scroll to `currentPage`.

- [ ] **Step 1: Tests** — `ThumbnailGridTest`: `tappingSelectsPage`, `currentPageIsMarked` (semantics `selected`), `offscreenItemsAreNotRequested` (500 pages; requested indices ⊆ first screenful + a row); `ThumbnailSourceTest`: `secondRequestIsCached` (renderer called once), `sizeMatchesWidth`.
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**. **Step 4:** green per device; manual: open grid on `outline.pdf`, tap page 9 → reader at page 9. **Step 5: Commit** `"Thumbnail grid"`.

### Task 5: Docs; full verification

- [ ] README Features (links, bookmarks, thumbnails). Full run: build, JVM, lint, release, connected per device (phone if unlocked). Commit `"Document links, bookmarks and thumbnails"`.
