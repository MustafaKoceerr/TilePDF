# Phase 8: Features users expect (sharing, printing, going back, thumbnails, reading comfort)

## Context

v1 is merged into master. The user picked, from the list of features a PDF-reader user expects, these:
- **1:** Share, print, and "go back" after a jump.
- **3:** Library thumbnails.
- **4:** A reading-comfort package.

**2** (form filling and signing) comes in the phase after.

The aim is to close everyday gaps, not to add features for their own sake. The user does the screenshots and the Play publishing.

All of it is built on our own code, PDFium and the Android platform, with no paid SDK. That holds for phase 9 too: PDFium's form API (`fpdf_formfill.h`) is reached with our own JNI.

The work splits into two PRs by risk:
- **8a:** low risk, mostly new UI entry points.
- **8b:** risky, because it touches the coordinate pipeline (rotate, crop margins, two pages side by side).

Each opens from master on its own branch: `phase8a-everyday`, then `phase8b-reading-comfort` on top of it. The plan is copied into the repo as `docs/superpowers/plans/2026-10-18-phase8-…md` and run with a ledger (executing-plans).

Rules:
- TDD is for JVM unit tests only.
- UI and emulator tests, and manual checks, run at the end of each PR (the user approved them in advance; API 35 emulator).
- `:app` instrumented tests run through `am instrument`.

---

## 8a: Everyday expectations

### 1. Share and print (reader ⋮ menu)
- **Share:** the reader ⋮ menu gets "Share" / "Paylaş".
  - It sends `ACTION_SEND` with `type=application/pdf` and `EXTRA_STREAM`, wrapped in `Intent.createChooser`, with `FLAG_GRANT_READ_URI_PERMISSION`.
  - **Before sharing, pending annotations are flushed** with the existing `AnnotationController.flush()` path, so the shared file has the notes.
  - For a `content://` URI the original is shared. A private copy (`file://` under `copiesDir`) is shared through a new **FileProvider** (`${applicationId}.files`, path `files/copies/`, only that folder) in `app/src/main/AndroidManifest.xml` + `res/xml/file_paths.xml`.
- **Print:** the reader ⋮ menu gets "Print" / "Yazdır".
  - `PrintManager.print(title, PdfPrintAdapter, null)`. The new `PdfPrintAdapter` (feature:reader) answers `onLayout` with `PrintDocumentInfo.CONTENT_TYPE_DOCUMENT` and the page count, and copies the file's bytes to the target in `onWrite`. A cancelled write is respected.
  - Annotations are flushed first, like Share.
- **Tests:** a JVM test for a pure function `shareUriFor(documentUri, copiesDir, authority)` (content stays as it is; a private copy becomes a FileProvider URI; anything else is null). Share and print themselves are checked by hand.

### 2. Go back after a jump
- **Pure history:** `JumpHistory` in feature:reader, JVM-tested.
  - `push(origin: ReadingPosition)` keeps a single level, the most recent origin, like Acrobat and Drive.
  - `back(): ReadingPosition?` returns the origin and empties the history.
  - A jump to the same page doesn't record anything.
  - The history clears when the document reloads.
- **Which jumps count:** every jump goes through the ViewModel: `onJump(PageJump)`.
  - Today's direct `pageJump = …` assignments in `ReaderScreen.kt` (TOC, bookmarks, annotation list, page grid, go to page) are routed through it.
  - Link jumps already pass through the ViewModel (`linkTargets.jump`).
  - The ViewModel records the current `position` as the origin.
- **UI:** after a jump, a small chip at the bottom: "← Sayfa 12'ye dön" / "Back to page 12".
  - Tapping it returns to the origin.
  - While the chip is visible, system Back also returns there first (`BackHandler`).
  - Scrolling by hand farther than 3 pages from the target removes the chip; it no longer fits the context.
  - It stays out of the way of the full-screen state and the annotation toolbar.
- **Tests:**
  - `JumpHistoryTest`: `backReturnsOrigin`, `samePageJumpIsIgnored`, `newJumpReplacesOrigin`, `backEmptiesHistory`.
  - `JumpChipVisibilityTest`: a pure `chipVisible(target, current)` with the 3-page rule.

### 3. Library thumbnails
- **Storage:** `ThumbnailStore` (core:data). Files are `filesDir/thumbnails/<sha256(uri)>.png`.
  - `save(uri, bytes)`, `file(uri): File?`, `delete(uri)`.
  - Bitmap encoding is done by the caller, so the store is JVM-testable with a temp directory.
- **Creation:** when the reader opens a document, `ReaderViewModel` renders page 1 at 240 px width with the existing `PageRenderer.renderPage`, PNG-encodes it on IO, and saves it. It is refreshed on every open, so it stays current.
- **Deletion:** the thumbnail is deleted when the document is removed from the library, in `LibraryViewModel.commitPendingRemoval` next to `documentRepository.forget`.
- **UI:** `LibraryScreen` rows get a 40×56 dp page thumbnail as `leadingContent`, decoded on IO and remembered. If there is none, a page placeholder icon. An inaccessible document shows its old thumbnail dimmed.
- **Backup:** `filesDir/thumbnails` is not backed up; the rules include only the datastore and the locale file. Data safety doesn't change.
- **Tests:**
  - `ThumbnailStoreTest`: `savedFileIsFound`, `deleteRemovesIt`, `keysAreStableAndFileNameSafe` (Turkish characters and `content://` in the URI).
  - `LibraryViewModelTest`: `removingDeletesThumbnail`.
- The README's "Known limitations" entry for library thumbnails is removed.

### 4. Turning pages with the volume keys (from the reading-comfort package; low risk, so it goes in 8a)
- `ReaderPreferences.volumeKeysTurnPages`, default false, in DataStore. The reading settings sheet gets a switch: "Ses tuşlarıyla sayfa çevir" / "Turn pages with volume keys".
- **Key routing:** `MainActivity.dispatchKeyEvent` asks a key handler registered by the reader through an app-scoped `HardwareKeys` singleton (Hilt):
  - The handler is set only while the reader is visible and the setting is on.
  - Volume down goes to the next page, volume up to the previous one, and the event is consumed. Otherwise it reaches the system, which changes the volume.
- **Tests:** `VolumeKeyPagingTest`, for a pure `pageForVolumeKey(key, current, count)` at the boundaries (it doesn't go past the last page).

### 8a verification
- JVM: the new tests plus the full `testDebugUnitTest`, `lintDebug` and `assembleRelease` (R8: the FileProvider and the print adapter work in the release build).
- Emulator (API 35):
  - share opens the chooser, and the shared PDF opens with its notes in another app (e.g. Drive);
  - print opens the print preview with the right page count, and "Save as PDF" works;
  - TOC jump → chip → return; system Back returns too;
  - the library shows thumbnails, and a removed document's thumbnail is deleted;
  - the volume keys turn pages when the setting is on, and change the volume when it's off.
- PR (base master).

---

## 8b: Reading comfort (coordinate pipeline)

### 5. Rotate a page and crop margins: one "view transform"
- **Transform model:** `PageViewTransform(rotation: 0/90/180/270, crop: PdfRect?)` in core:model, JVM-tested.
  - `size(pageSize)` and the forward/inverse mapping `toView(point)` / `toPage(point)`.
  - The existing convention stays as it is: the model space is the document's page space (crop box + `/Rotate`).
  - So annotation drafts in Room aren't affected; the transform exists only at the view layer.
- **Where it applies:**
  - `CoordinateMapper` (core/model): search highlights, selection handles, links and annotation hits already go through it.
  - The render matrix in `PageRenderer` (rotate + translate for the crop).
  - Page sizes (`pageSizes`, swapped).
  - At execution, a grep for "every layer goes through CoordinateMapper" is done; any layer that doesn't is converted.
- **Crop:** a new JNI `nativeContentBounds(page)` returns the union of `FPDFPageObj_GetBounds` over the visible page objects, in page space. It is computed per page and cached. "Crop margins" applies it with a small padding (a few points).
- **Storage:** per document, in Room: `DocumentEntity.rotation`, `cropMargins` (v6 auto-migration + migration test).
- **UI:** the reader ⋮ menu or settings sheet gets "Sayfayı döndür" (90° steps) and "Kenar boşluklarını kırp" (switch).
- **Tests:**
  - `PageViewTransformTest`: round trip for every rotation, size swap, the crop offset, and that a point inside the crop maps back to itself.
  - The `CoordinateMapper` tests extended with a rotated and a cropped page.
  - The migration test `migrate5To6`.

### 6. Two pages side by side on a tablet
- **When:** in horizontal mode, when the reader is landscape with width ≥ 840 dp. A reading-settings toggle "Çift sayfa" (on by default).
- **Layout:** pages are paired the book way: the cover alone, then (2,3), (4,5)….
  - A pure `spreads(pageCount, coverAlone)` is JVM-tested.
  - The pager shows spreads, each half zooming with its own `PdfPage`.
  - The current page and `ReadingPosition` keep the left page of a spread.
- **Tests:** `SpreadsTest`: odd/even page counts, a single page, the cover; spread index ↔ page index.

### 8b verification
- JVM: the new tests and the full suite; the migration test on the device.
- Emulator:
  - rotate a page 90/180/270: search highlights, text selection, link taps and annotations land in the right place, and drawing works;
  - crop margins on a margin-heavy page;
  - two pages in tablet emulation (`wm size 2560x1600`, `density 320`) in horizontal mode;
  - restore the size and density afterwards.
- PR (base `phase8a-everyday`).

---

## Next: phase 9, form filling + signature (planned separately)
- PDFium's own form API, with our own JNI: `FPDFDOC_InitFormFillEnvironment`, `FORM_OnLButtonDown`, `FORM_OnChar`, `FORM_GetFocusedText`, `FPDF_FFLDraw`, `FORM_ForceToKillFocus`. Saving goes through the existing safe-save path. No paid SDK (Nutrient/PSPDFKit, Apryse, Foxit) is needed.
- Signature: drawn with the existing pen, then placed as an ink annotation or an image object. A cryptographic signature with a certificate (PAdES) is a separate subject; PDFium can't create one, so it's not included unless asked.
