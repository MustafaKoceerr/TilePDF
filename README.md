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

- Open any PDF through the Storage Access Framework, or from another app with
  "Open with" or "Share" (also when a file manager labels the file
  application/x-pdf, or sends an unknown type for a name ending in .pdf,
  and from browser downloads); recent documents are kept in a local library (no
  cloud, no accounts). Files the app couldn't reopen later (no lasting
  permission, or a stream of unknown size) are kept as a private copy
- Password-protected PDFs open after entering the password; it is never
  stored
- Pinch-to-zoom (1x–5x), double-tap zoom, horizontal pan with fling
- Sharp text at every zoom level via tiles, with an instant low-resolution
  draft underneath
- Full-text search with live results, a match counter, previous/next, and
  highlights drawn over the page
- Fast-scroll thumb and a page indicator while scrolling; jump to any page
  by number
- Table of contents from the PDF outline: nested entries, jumps to the
  exact heading position (XYZ, named and GoTo destinations)
- Links: tapping one jumps to its target page and position; web and mail
  links (`http`, `https`, `mailto` only) open in another app
- Bookmarks per document, listed next to the table of contents and removed
  with the library entry
- Page grid: every page as a thumbnail, tap one to jump there
- After a jump (contents, bookmark, link, page grid or page number), a chip
  and system Back return to where it started
- Share and print the document with its annotations, even when the file
  itself can't be saved to
- Library rows show each document's first page
- Rotate pages and crop their empty margins, kept per document; two pages
  side by side on wide screens in horizontal layout
- Fill in AcroForm fields (text, check box, radio button, combo and list
  box) and sign with a drawn signature; both are saved into the PDF. The
  form's own scripts run: totals calculate themselves, number and percent
  formats apply, validation rejects bad values with the form's message, and
  `app.alert` / `app.response` show as dialogs
- XFA forms, the dynamic ones most readers show only as "Please wait…",
  open laid out and can be filled in and saved. A tap goes to the form as a
  finger's would; a text field opens the text dialog
- Recognise text on scanned pages (OCR), on the device with Google ML Kit:
  the words go into the page as invisible text, so they can be searched,
  selected and copied, also in other apps once saved. Works offline; a chip
  offers it on pages without text
- Text selection: long-press a word, drag Material-style handles, copy or
  select all from the system text toolbar
- Vertical scrolling or horizontal single pages (swipe; each page fits the
  screen and zooms on its own); switching keeps the current page
- Light, night (colour inversion) and sepia pages, recoloured without
  re-rendering
- Keep screen on while reading
- Library of opened documents: favorites, sorting by last opened, name or
  date added (remembered), and search that ignores case and Turkish accents
  ("sozlesme" finds "Sözleşme.pdf"). Removing a document can be undone for a
  few seconds, since it also deletes its private copy and unsaved annotations
- Annotations, saved into the PDF itself so other apps show them:
  highlight, underline or strike out selected text; draw with a pen
  (colours, three widths) and erase whole strokes; sticky notes; undo and
  redo. Any markup annotation, also one made by another app, can be tapped
  and deleted. Every annotation is listed next to the table of contents. A
  file that can't be written is offered as an annotated copy
- Tap a page to hide the top bar and system bars for full-screen reading;
  the pages don't move (with TalkBack on, the bars stay)
- Large screens: on tablets and unfolded foldables (at least 840 dp wide
  and 480 dp tall) contents, bookmarks and annotations open in a panel
  beside the pages that stays open while reading; a phone in landscape
  keeps the bottom sheet. The library flows into columns on wide screens
- Settings (library menu): theme (system, light or dark, with optional
  Material You colours on Android 12+) and language (English or Turkish,
  following the device by default; also in Android 13+ app settings). Both
  come from the developer's own libraries,
  [compose-theme-manager](https://github.com/MustafaKoceerr/compose-theme-manager) and
  [compose-language-manager](https://github.com/MustafaKoceerr/compose-language-manager),
  served by JitPack
- Accessible: every control has its own TalkBack label (colours by name,
  widths in points) and a 48 dp touch target, checked with the
  Accessibility Test Framework in UI tests; layouts hold at 200% font size
- A document that doesn't open says why, with the next step: choose the
  file again (moved, deleted or permission lost), try again (out of
  memory), or back to the library (damaged); passwords are asked for
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
                     AnnotationHistory (undo/redo), SaveAsCopyUseCase, ResumePendingSavesUseCase
   ▼
core:data            Repositories behind interfaces
                       DocumentRepository     SAF access, persisted grants
                       LibraryRepository      Room
                       AnnotationRepository   annotation drafts in Room
                     AnnotationSaver + SaveAnnotationsWorker (WorkManager)
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

Annotations are read and written by PDFium as well. TilePDF's own carry
`/NM tilepdf-<uuid>` and are never edited in place: a change replaces one with
a new id, so saving reduces to a diff of id sets (`annotationChanges`). The
reader's document hides TilePDF's annotations in memory, because the reader
draws them itself, and can hide a deleted foreign one until it is saved.
`PdfEngine.openForEditing` returns an `EditablePdfDocument`, which hides
nothing. It removes annotations (any markup annotation, together with its
popup), adds highlights, underlines, strikeouts, ink and notes, lets PDFium
write their appearance streams, and saves incrementally: the original bytes
followed by the changes.

TilePDF uses its own PDFium build: PDFium with V8 and XFA, and with TilePDF's
patches (`patches/tilepdf/` on the `tilepdf` branch of
[MustafaKoceerr/pdfium-binaries](https://github.com/MustafaKoceerr/pdfium-binaries),
a fork of [bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries)).
It is built there by GitHub Actions for arm64-v8a, armeabi-v7a, x86_64 and x86
and published as a release. The patches:

- add `FPDF_TilePdfSetFormFallbackFont`, a font form fields use for letters
  their own font lacks; upstream PDFium finds such fonts only on Windows.
  TilePDF hands it a 40 KB Noto Sans subset (Latin, Greek, Cyrillic), so
  Turkish İ, ş and ğ show in Helvetica fields, and the font is embedded once
  per document;
- turn on `AFNumber_Format` and `AFPercent_Format` in form scripts, which
  upstream leaves out on Android;
- add `FORM_TilePdfGetFocusedXfaWidgetType`, which tells what kind of XFA
  widget a tap focused.

The
`tilepdf.pdfium` convention plugin downloads the archives into
`.gradle/pdfium/`, and the build fails if an archive's SHA-256 differs from the
pinned one; no binaries are committed. To update PDFium, rebuild on the fork,
then change `RELEASE` and the four hashes in
`build-logic/convention/src/main/kotlin/PdfiumConventionPlugin.kt`.

Each document keeps its two most recently used pages loaded natively, so a
page drawn as many tiles is parsed once, and text extraction reuses the same
page. On a Galaxy A52 that draws a 1080 px page of a 300 dpi scan in about
300 ms instead of 1450 ms, and a page of 20 000 vector lines in about 260 ms
instead of 1650 ms. A loaded page also keeps its decoded images (about 29 MB
for a scanned page), so the cache stays at two pages and is emptied whenever
the reader trims memory. `PdfDocument` and `PdfEngine` are interfaces, so data and ViewModel code
is tested with fakes.

## Saving annotations

Every annotation edit goes into a Room draft first: one row per document with
TilePDF's full annotation list, the other apps' annotations the user deleted,
a `dirty` flag, and the size and last-modified time of the file the draft is
based on. A crash loses nothing but the undo history, which lives only in
memory (`AnnotationHistory`) and is cleared after each save.

When a document closes or the app goes to the background, `SaveAnnotationsWorker`
runs `AnnotationSaver`, one save at a time per document:

1. If the file's stamp no longer matches the draft, another app changed it:
   nothing is written (`FileChanged`).
2. PDFium opens the file for editing, applies the difference to the draft, and
   saves incrementally into a temp file in the cache.
3. The temp file is reopened and must hold exactly the draft's annotations;
   otherwise the original isn't touched (`Failed`).
4. The temp file overwrites the document through SAF (`"wt"`, or `"w"` for
   providers that reject it, which is safe because an incremental save never
   shrinks the file). This step can't be cancelled.
5. The draft is marked saved, unless the user edited meanwhile. Deletions
   addressed by position (`#index`) are dropped once saved, because positions
   shift.

Without write access the draft is kept (`NoWritePermission`) and
`SaveAsCopyUseCase` can write a copy instead, which then takes over the draft.
Passwords of encrypted PDFs stay in memory for the life of the process
(`SessionPasswords`). They are never written anywhere, so a save that finds no
password waits (`NeedsPassword`) until the document is opened again. Drafts a
killed process left unsaved are saved at the next start.

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
  every document goes through one dispatcher, plus a mutex per document so
  closing waits for in-flight work. It is one dedicated thread (`PdfThread`),
  not just one call at a time: V8, which runs form scripts, is bound to the
  thread that started it, and scripts fail silently on any other.

## Memory strategy

| Holder | Bound | Purpose |
|---|---|---|
| On-screen tiles | what's visible | Snapshot state the UI draws |
| Tile LRU cache | `min(maxMemory / 4, 96 MB)`, by bytes | Instant scroll-back and zoom-back |
| Draft cache | 24 MB | Whole-page placeholders |
| Thumbnail cache | 16 MB | Page grid; only visible items render |
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
                                       # outline/annotations, tiles, pool,
                                       # Room migration and drafts, saving,
                                       # save worker
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
- **Annotations round-trip** through an incremental save (also on a page with
  `/Rotate 90`), keep other apps' annotations, take a deleted annotation's popup
  along, and come back with appearance streams that a viewer hiding nothing
  draws. Android's `PdfRenderer` draws no annotations, so it can't serve as
  that viewer.

Run instrumented tests one device at a time (`ANDROID_SERIAL=<serial>`): with
several devices in parallel, AGP 9.4 sometimes reports passing tests as
failed.

Test PDFs in `core/testing/src/main/assets` are small hand-built files with
known content and positions, generated by `tools/generate_test_pdfs.py`;
rerunning it reproduces them byte for byte.

## Release

- Release builds are shrunk with R8; `core:pdf` ships keep rules for the
  classes its C++ code builds through JNI.
- Signing reads the Play upload key from four environment variables and never
  from the repository; see [docs/RELEASING.md](docs/RELEASING.md).
- Text recognition uses ML Kit's bundled Latin model (`core:ocr`), so it
  needs no download and runs offline. ML Kit would send usage metrics to
  Google when online; without the `INTERNET` permission it can't.
  Recognised words wait per page in the `recognized_text` table (Room v8)
  and are saved as invisible text (render mode 3) in the Noto Sans subset,
  inside `TilePdfOcr` marked content, so a page is never recognised into the
  file twice.
- XFA values can't be read or set by name, so an XFA form's draft keeps its
  edits instead: each tap point and the text written there, in order
  (`annotation_drafts.xfaEdits`, Room v9). The engine is deterministic, so
  replaying them on the file, on open and when saving, fills it in the same
  way; the save checks the last text at each point by tapping it in the
  saved file. Whether a tap focused a text field comes from
  `FORM_TilePdfGetFocusedXfaWidgetType`, a patch in our PDFium build.
- TilePDF stays offline: the manifest removes `INTERNET`, and
  `checkReleasePermissions` fails the release build if the merged manifest
  asks for any permission outside an allowlist.
- Android backup keeps preferences only (reading settings, theme, language).
- Settings → About shows the version and every third-party license: PDFium's
  components, copied from the pinned pdfium-binaries archive at build time,
  the Maven libraries, collected by AboutLibraries, and Noto Sans (OFL).
- Store material lives in `fastlane/metadata/android/` (EN and TR texts, with
  length limits checked by `StoreListingTest`). The Data safety answers and
  privacy policy are in [docs/play/](docs/play/).

## Known limitations

- Text recognition reads Latin script only (Turkish and English included).
  Search inside a document folds İ/ı/I/i but not other Turkish letters, so
  "sisli" doesn't find "Şişli" there, unlike the library search.
- Forms: multi-select list boxes show only their first choice. XFA pages
  aren't PDF pages, so they take no annotations, signature or recognised
  text, and search finds nothing there; XFA support in PDFium is still
  marked experimental. Scripts can't mail or submit a form (TilePDF is offline), and
  timers and page open/close scripts don't run.
- The signature is a drawing, not a cryptographic (PAdES) signature.

## Out of scope, on purpose

Cloud sync, accounts, payments and EPUB. The project is meant to go deep on
rendering rather than wide on features.
