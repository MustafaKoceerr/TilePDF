# TilePDF

An ad-free PDF reader for Android with its own tile-based rendering engine.

TilePDF doesn't wrap a ready-made viewer (`androidx.pdf`, `PdfViewerFragment`).
It builds the viewer on top of the platform's low-level `PdfRenderer`:
viewport math, 512 px tiles, a memory-bounded cache, PDF-point to screen-pixel
mapping for search highlights, and two interchangeable text-extraction
strategies chosen at runtime.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Coroutines/Flow · Hilt ·
Room · DataStore · Navigation Compose (type-safe) · PdfBox-Android ·
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

Single module, layered by package, following
[Modern Android Development](https://developer.android.com/topic/architecture)
guidance with unidirectional data flow:

```
feature/   UI layer      Screen (stateful → stateless) · ViewModel · UiState
   │         state flows up as StateFlow<UiState>; events go down as calls
   ▼
domain/    OpenDocumentUseCase — open + record in library + restore position
   ▼
data/      Repositories behind interfaces
             DocumentRepository     SAF access, persisted grants
             LibraryRepository      Room
             ReaderPreferences      DataStore
             TextSearchRepository   PdfTextProvider strategy + text cache
   ▼
core/      Rendering engine and geometry, UI-framework independent
             pdf/       PdfDocument: PdfRenderer serialized on one thread
             render/    TileGrid, PageRenderer, TiledPageImageSource,
                        BitmapCache, BitmapPool
             geometry/  Viewport, PageLayout, CoordinateMapper
             text/      TextSearcher
model/     Plain data shared across layers
```

Each feature owns its type-safe route and `NavGraphBuilder` extension.
Stateless screens take `UiState` plus callbacks, so they render in previews and
tests without a ViewModel.

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
- **One PDF thread.** `PdfRenderer` isn't thread-safe and allows one open page,
  so all access goes through a single-threaded dispatcher plus a mutex.

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

- Text providers produce boxes in that space. PdfBox's `*DirAdj` coordinates
  already are; PDFium's `searchText` bounds already are.
- `CoordinateMapper` converts points to page pixels with the same
  points-to-pixels factor the renderer uses (`PageSize.scaleForWidth`), so
  highlights and tiles can't drift apart.
- Screen placement reuses the list's own rounding (`Dp.roundToPx`,
  `round(width × zoom)`) rather than approximating it.

## Text extraction: strategies behind one interface

`PdfTextProvider` has three implementations; `TextModule` picks one by API
level:

- **`PdfBoxTextProvider`** (all API levels) extracts every character with
  its box once and caches it. It searches with `TextSearcher`, which is
  case-insensitive, folds Turkish İ/ı/I/i together, and matches a line break
  as a space.
- **`NativePdfTextProvider`** (API 35+) calls `PdfRenderer.Page.searchText`
  (PDFium). It needs no extraction but caches nothing.
- **`HybridTextProvider`** (API 35+, **the default there**) answers with
  native search immediately while PdfBox extracts the document in the
  background. Each page switches to the PdfBox cache once it has been
  extracted.

Below API 35, PdfBox handles everything.

The text provider opens together with the document, so warm-up starts before
the first search. Extracted text is stored compactly as a String plus four
floats per character (about 18 bytes per character), so keeping a whole book
in memory stays affordable.

## Testing

```bash
./gradlew testDebugUnitTest            # JVM: zoom math, viewport, tile grid,
                                       # text search and selection, coordinate
                                       # mapping, table-of-contents lookup
./gradlew connectedDebugAndroidTest    # device: PdfRenderer, tiles, pool,
                                       # PdfBox extraction, outline, Room
                                       # migration
```

Instrumented tests to note:

- **Stitched tiles match a whole-page render** pixel for pixel (within
  anti-aliasing tolerance).
- **The tile source's fallback, cache and pool behaviour** is tested on a real
  PDF, including a regression test for render jobs that finish without
  suspending.
- **PdfBox boxes land at known positions** in the generated sample PDF.

Test PDFs in `app/src/androidTest/assets` are small hand-built files with
known content and positions, generated by `tools/generate_test_pdfs.py`;
rerunning it reproduces them byte for byte.

## Known limitations

- Password-protected PDFs aren't supported.
- Scanned (image-only) PDFs have no text, so search finds nothing (no OCR).
- Library entries have no page thumbnails.

## Out of scope, on purpose

Cloud sync, accounts, payments and EPUB. The project is meant to go deep on
rendering rather than wide on features.
