# TilePDF

An ad-free PDF reader for Android with its own tile-based rendering engine.

TilePDF doesn't wrap a ready-made viewer (`androidx.pdf`, `PdfViewerFragment`).
It embeds PDFium, the engine behind Chrome and Android's own `PdfRenderer`,
through a small JNI layer, so every Android version from 10 up renders and
reads text identically. On top of it: viewport math, 512 px tiles, a
memory-bounded cache, and PDF-point to screen-pixel mapping for search
highlights and text selection.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Coroutines/Flow · Hilt ·
Room · DataStore · Navigation Compose (type-safe) · PDFium (C++, via JNI) ·
minSdk 29

## Features

- Open any PDF through the Storage Access Framework; recent documents are kept
  in a local library (no cloud, no accounts)
- Pinch-to-zoom (1x–5x), double-tap zoom, horizontal pan with fling
- Sharp text at every zoom level via tiles, with an instant low-resolution
  draft underneath
- Full-text search with live results, a match counter, previous/next, and
  highlights drawn over the page
- Fast-scroll thumb and a page indicator while scrolling; jump to any page
  by number
- Table of contents from the PDF outline: nested entries, jumps to the
  exact heading position (XYZ, named and GoTo destinations)
- Text selection: long-press a word, drag Material-style handles, copy or
  select all from the system text toolbar
- Night mode by colour inversion (no re-rendering)
- Reopens a document exactly where you left it: page, offset within the page,
  zoom and horizontal pan

## Architecture

Modules follow [Now in Android](https://github.com/android/nowinandroid) and
[Modern Android Development](https://developer.android.com/topic/architecture)
guidance, with unidirectional data flow. Shared Gradle setup lives in
convention plugins under `build-logic/` (`tilepdf.android.library`,
`tilepdf.android.feature`, `tilepdf.hilt`, …).

```
app                  MainActivity, Hilt application, nav host
   │
feature:library      Screen (stateful → stateless) · ViewModel · UiState
feature:reader         state flows up as StateFlow<UiState>; events go down as calls
   ▼
core:domain          OpenDocumentUseCase — open + record in library + restore position
   ▼
core:data            Repositories behind interfaces
                       DocumentRepository     SAF access, persisted grants
                       LibraryRepository      Room
                       ReaderPreferences      DataStore
                       DocumentTextRepository page text cache for search/selection
                     TextSearcher, TextSelection
   ▼
core:database        Room (schema history in core/database/schemas)
core:datastore       Preferences DataStore
core:render          TileGrid, PageRenderer, TiledPageImageSource, BitmapCache, BitmapPool
core:pdf             PdfDocument / PdfEngine, implemented with PDFium (JNI)
   ▼
core:model           Pure Kotlin: geometry (Viewport, PageLayout, CoordinateMapper) and data
core:common          Dispatcher qualifiers, application scope

core:designsystem    Theme and TilePdfIcons, used by the features
core:testing         Test PDFs and TestAssets for instrumented tests
```

Feature modules never depend on each other; `app` wires them together.
Kotlin packages predate the modules and still read `core.render`, `data.text`
and so on.

Each feature owns its type-safe route and `NavGraphBuilder` extension.
Stateless screens take `UiState` plus callbacks, so they render in previews and
tests without a ViewModel.

## PDF engine

`core:pdf` embeds [PDFium](https://pdfium.googlesource.com/pdfium/) through a
small JNI library (`src/main/cpp/pdfium_jni.cpp`), so every API level from 29
up gets the same renderer, text extraction and outline reader. `Pdfium` opens
documents (with an optional password) and `PdfiumDocument` renders pages,
extracts text with per-character boxes and reads the table of contents. Every
call goes through the single `@PdfDispatcher`, because PDFium's state is global
to the process.

The prebuilt libraries come from
[bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries) for
arm64-v8a, armeabi-v7a, x86_64 and x86. The `tilepdf.pdfium` convention plugin
downloads them into `.gradle/pdfium/`, and the build fails if an archive's
SHA-256 differs from the pinned one; no binaries are committed. To update
PDFium, change `RELEASE` and the four hashes in
`build-logic/convention/src/main/kotlin/PdfiumConventionPlugin.kt`.

Each document keeps its two most recently used pages loaded natively, so a
page drawn as many tiles is parsed once, and text extraction reuses the same
page. On a Galaxy A52 that draws a 1080 px page of a 300 dpi scan in about
300 ms instead of 1450 ms, and a page of 20 000 vector lines in about 260 ms
instead of 1650 ms. A loaded page also keeps its decoded images (about 29 MB
for a scanned page), so the cache stays at two pages and is emptied whenever
the reader trims memory. `PdfDocument` and `PdfEngine` are interfaces, so data and ViewModel code
is tested with fakes.

## Rendering pipeline

```
 pinch in progress ──► graphicsLayer transform of what's already drawn (no work)
        │ fingers up
        ▼
 commit zoom ──► pages re-laid out at the new width, scroll anchored in the
        │        same frame (requestScrollToItem) so nothing jumps
        ▼
 visible pages (LazyListLayoutInfo + zoom + pan) ──► Viewport ──► page-local
        │                                                         visible region
        ▼
 TileGrid ──► 512 px tiles covering the region + 1-tile prefetch, centre first
        │
        ▼
 TiledPageImageSource ──► LRU hit? draw. Miss? render on the PDF thread.
                          Tiles that left the screen: job cancelled.
```

- **Two layers per page.** A half-resolution draft of the whole page appears
  immediately; sharp tiles are drawn over it as they arrive. A missing tile
  never leaves a hole.
- **Fixed render levels (1, 1.5, 2, 3, 4, 5x).** Tiles render at the smallest
  level at or above the zoom, so they are only ever scaled down (always sharp),
  and small pinch adjustments reuse cached tiles.
- **Level changes don't flash.** The previous level's tiles stay under the new
  ones until the new level covers the page.
- **Tile draws happen in the draw phase.** A tile arriving redraws one page
  without recomposition. Tile edges are rounded independently so neighbours
  share edges exactly (no hairline seams).
- **One PDF thread.** PDFium isn't thread-safe and its state is global, so
  every document goes through one single-threaded dispatcher, plus a mutex per
  document so closing waits for in-flight work.

## Memory strategy

| Holder | Bound | Purpose |
|---|---|---|
| On-screen tiles | what's visible | Snapshot state the UI draws |
| Tile LRU cache | `min(maxMemory / 4, 96 MB)`, by bytes | Instant scroll-back and zoom-back |
| Draft cache | 24 MB | Whole-page placeholders |
| Bitmap pool | 16 tiles | Reuse evicted 1 MB bitmaps instead of allocating |

- An evicted bitmap that is still on screen isn't pooled until it leaves the
  screen.
- Pooled bitmaps wait 100 ms before reuse, so the RenderThread never samples a
  bitmap mid-overwrite.
- `onTrimMemory` drops everything that isn't on screen. Once the reader is
  hidden, dropped tiles are recycled immediately rather than waiting for a
  garbage collection, which the small Java heap would rarely trigger.

## Coordinate spaces

Search highlights must land on the rendered glyphs at any zoom. Everything
meets in one convention: **PDF points, top-left origin, relative to the crop
box, page rotation applied.**

- PDFium's character boxes are mapped into that space with
  `FPDF_PageToDevice`, which applies the crop box and `/Rotate`.
- `CoordinateMapper` converts points to page pixels with the same
  points-to-pixels factor the renderer uses (`PageSize.scaleForWidth`), so
  highlights and tiles can't drift apart.
- Screen placement reuses the list's own rounding (`Dp.roundToPx`,
  `round(width × zoom)`) rather than approximating it.

## Text: search and selection

The reader's open `PdfDocument` also supplies its text: PDFium extracts a page
with one box per character, and `CachingDocumentTextRepository` keeps every
page it has extracted. The first search extracts pages as it reaches them and
streams each page's matches; later searches only scan memory. Selecting text
reuses the same cache.

Search runs in Kotlin with `TextSearcher`, which is case-insensitive, folds
Turkish İ/ı/I/i together, and matches a line break as a space. Extracted text
is stored compactly as a String plus four floats per character (about 18
bytes per character), so keeping a whole book in memory stays affordable.
Nothing is extracted in the background: that would compete with rendering for
the one PDF thread.

## Testing

```bash
./gradlew testDebugUnitTest            # JVM: zoom math, viewport, tile grid,
                                       # text search and selection, coordinate
                                       # mapping, table-of-contents lookup
./gradlew connectedDebugAndroidTest    # device: PDFium open/render/text/
                                       # outline, tiles, pool, Room migration
```

Instrumented tests to note:

- **Stitched tiles match a whole-page render** pixel for pixel (within
  anti-aliasing tolerance).
- **The tile source's fallback, cache and pool behaviour** is tested on a real
  PDF, including a regression test for render jobs that finish without
  suspending.
- **PDFium matches the platform renderer**: the same page sizes, and pixels
  within anti-aliasing tolerance of `PdfRenderer` (kept only as a test-time
  reference).
- **Character boxes land at known positions** in the generated sample PDF,
  and encrypted, truncated and invalid files fail with typed errors.

Run instrumented tests one device at a time (`ANDROID_SERIAL=<serial>`): with
several devices in parallel, AGP 9.4 sometimes reports passing tests as
failed.

Test PDFs in `core/testing/src/main/assets` are small hand-built files with
known content and positions, generated by `tools/generate_test_pdfs.py`;
rerunning it reproduces them byte for byte.

## Known limitations

- Password-protected PDFs aren't supported.
- Scanned (image-only) PDFs have no text, so search finds nothing (no OCR).
- Library entries have no page thumbnails.

## Out of scope, on purpose

Cloud sync, accounts, payments and EPUB. The project is meant to go deep on
rendering rather than wide on features.
