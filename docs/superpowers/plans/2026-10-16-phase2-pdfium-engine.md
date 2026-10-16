# Phase 2 — PDFium Engine (read side) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put PDFium inside `core:pdf` behind a Kotlin API that opens (with password), measures, renders, extracts text with boxes and reads the outline, identically on API 29–37.

**Architecture:** Prebuilt `libpdfium.so` from bblanchon/pdfium-binaries `chromium/8076` is downloaded by Gradle, checked against pinned SHA-256s, and linked by a thin C++ JNI library (`tilepdf_pdfium`) built with CMake. Kotlin `Pdfium` (opener) and `PdfiumDocument` run every native call on the existing single-threaded `@PdfDispatcher`. Nothing in the app uses it yet; phase 3 swaps the reader over and deletes PdfRenderer/PdfBox.

**Tech Stack:** NDK 28.2.13676358 (AGP 9.4.1 default, auto-installed), CMake 3.22.1, PDFium chromium/8076, JNI + `jnigraphics`.

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md`

## Global Constraints

- One engine for every API level ≥ 29; no `Build.VERSION` branches in engine code.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` (the API 29 emulator is 32-bit x86).
- 64-bit `.so` files must have 16 KB-aligned LOAD segments (Play requirement). PDFium's are (`0x4000`); our JNI library must be too (NDK r28 default).
- No binaries in git. Downloads are pinned to release `chromium/8076` and verified:

| tgz | SHA-256 |
|---|---|
| `pdfium-android-arm64.tgz` | `b7a6919dc130dfdf7e409bd1762ed2a306408c4faee1173668837fc446217bf7` |
| `pdfium-android-arm.tgz` | `4cabe64372ec9a8f9a7e023ae1c2802068abdb5ed79d4ef5cd42f2bbde80927a` |
| `pdfium-android-x64.tgz` | `9d043689096c7cdd4ca98fff95599be115b2a38feba0c68d2a79ba5eedbda415` |
| `pdfium-android-x86.tgz` | `eb0aa52696094f66c86994220d45b00b63cd90b13b8e76bd9b201d7f89825214` |

  URL: `https://github.com/bblanchon/pdfium-binaries/releases/download/chromium%2F8076/<tgz>`
- All PDFium calls, for every document, go through the one `@PdfDispatcher` (PDFium has global state; it needs mutual exclusion, not thread affinity).
- Coordinates out of the engine: PDF points, top-left origin, relative to the crop box, page rotation applied — the convention `PageText` and `OutlineEntry` already document.
- Page sizes match PdfRenderer's (`PageSize(widthPt.toInt(), heightPt.toInt())` truncation of `FPDF_GetPageSizeByIndexF`).
- Existing PdfRenderer/PdfBox code is untouched in this phase.

## Review Focus

1. **Two documents rendering at once.** PDFium's state is global; per-document locks are not enough. Task 3 test `twoDocumentsRenderConcurrently` renders from two open documents in parallel.
2. **Close while a render is in flight.** Use-after-free crashes the process, not a test. Task 3 test `closeDuringRenderIsSafe` launches renders and closes immediately; the process must survive and later calls throw `IllegalStateException`.
3. **Wrong bitmap config.** Native code writing BGRA into an RGB_565 bitmap corrupts memory. Task 3 test `rejectsNonArgb8888Bitmap` expects `IllegalArgumentException`.
4. **Out-of-range page index.** Must fail in Kotlin before reaching native. Task 3 test `pageIndexOutOfRangeThrows`.
5. **Truncated file.** A cut-off download must be a `PdfCorruptException`, not a native crash. Task 2 test `truncatedFileIsCorrupt`.

---

### Task 1: Download, verify and link PDFium; JNI library loads

**Files:**
- Create: `build-logic/convention/src/main/kotlin/PdfiumConventionPlugin.kt`, `build-logic/convention/src/main/kotlin/com/mustafakoceerr/tilepdf/DownloadPdfiumTask.kt`
- Modify: `build-logic/convention/build.gradle.kts` (register `tilepdf.pdfium`), `gradle/libs.versions.toml` (plugin alias), `core/pdf/build.gradle.kts`
- Create: `core/pdf/src/main/cpp/CMakeLists.txt`, `core/pdf/src/main/cpp/pdfium_jni.cpp`
- Create: `core/pdf/src/main/java/com/mustafakoceerr/tilepdf/core/pdf/PdfiumNative.kt`
- Test: `core/pdf/src/androidTest/java/com/mustafakoceerr/tilepdf/core/pdf/PdfiumNativeTest.kt`

**Interfaces:**
- `DownloadPdfiumTask` (cacheable, configuration-cache safe): `@Input url`, `@Input sha256`, `@OutputDirectory outputDir`; downloads, fails the build with both hashes on mismatch, extracts the tgz. Outputs live in `<rootDir>/.gradle/pdfium/chromium-8076/<abi>/` so `clean` doesn't re-download.
- `tilepdf.pdfium` plugin: registers one task per ABI, sets `externalNativeBuild.cmake.path = src/main/cpp/CMakeLists.txt`, passes `-DPDFIUM_DIR=<rootDir>/.gradle/pdfium/chromium-8076` via `defaultConfig.externalNativeBuild.cmake.arguments`, sets `ndk.abiFilters` to the four ABIs, and makes every task whose name starts with `configureCMake` depend on the downloads.
- CMake: `add_library(pdfium SHARED IMPORTED)` with `IMPORTED_LOCATION ${PDFIUM_DIR}/${ANDROID_ABI}/lib/libpdfium.so`, include dir `${PDFIUM_DIR}/${ANDROID_ABI}/include`; `add_library(tilepdf_pdfium SHARED pdfium_jni.cpp)` linking `pdfium jnigraphics log`. ABI → tgz: `arm64-v8a→arm64, armeabi-v7a→arm, x86_64→x64, x86→x86`.
- `internal object PdfiumNative` — loads `tilepdf_pdfium` in `init`; `@JvmStatic external fun nativeInit()` calls `FPDF_InitLibrary()`. Later tasks add externals here.

- [ ] **Step 1: Write `PdfiumNativeTest.initializesLibrary`** — calls `PdfiumNative.nativeInit()` twice; asserts no exception.
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :core:pdf:connectedDebugAndroidTest` — Expected: FAIL to compile (`PdfiumNative` unresolved).
- [ ] **Step 3: Implement plugin, task, CMake, JNI init (`static bool initialized` guard).**
- [ ] **Step 4: Run the test on every device** (one at a time, `ANDROID_SERIAL` loop) — Expected: PASS on API 35 phone, API 35 tablet, API 29 x86.
- [ ] **Step 5: Verify packaging and alignment:** unzip `core/pdf/build/outputs/aar/pdf-debug.aar`; Expected `jni/<abi>/libpdfium.so` and `jni/<abi>/libtilepdf_pdfium.so` for all four ABIs, and every 64-bit `.so` has LOAD align `0x4000` (python ELF header check).
- [ ] **Step 6: Tamper check:** change one hex digit of one SHA-256, run `./gradlew :core:pdf:assembleDebug --rerun-tasks` — Expected: build fails naming both hashes; revert.
- [ ] **Step 7: Commit** — `"Build PDFium into core:pdf"`

### Task 2: Open documents; page sizes; typed errors

**Files:**
- Create: `core/pdf/src/main/java/com/mustafakoceerr/tilepdf/core/pdf/Pdfium.kt`, `PdfiumDocument.kt`, `PdfOpenException.kt`
- Modify: `pdfium_jni.cpp`, `PdfiumNative.kt`
- Modify: `tools/generate_test_pdfs.py` — add `encrypted.pdf`; regenerate into `core/testing/src/main/assets/`
- Test: `core/pdf/src/androidTest/java/com/mustafakoceerr/tilepdf/core/pdf/PdfiumOpenTest.kt`

**Interfaces:**
- `sealed class PdfOpenException(message: String) : IOException(message)`; `class PdfPasswordException(val wrongPassword: Boolean) : PdfOpenException`; `class PdfCorruptException(message: String) : PdfOpenException`. Missing/unreadable URI stays `FileNotFoundException`.
- `@Singleton class Pdfium @Inject constructor(@ApplicationContext context: Context, @PdfDispatcher dispatcher: CoroutineDispatcher) { suspend fun open(uri: Uri, password: String? = null): PdfiumDocument }`
- `class PdfiumDocument internal constructor(...) : Closeable { val pageSizes: List<PageSize>; val pageCount: Int; override fun close() }` — `close()` has `PdfDocument.close()` semantics (non-suspending, waits for in-flight work via the dispatcher + `Mutex`).
- Natives: `nativeOpen(fd: Int, size: Long, password: String?): Long` (custom `FPDF_FILEACCESS` reading with `pread` on the fd; the Kotlin side keeps the `ParcelFileDescriptor` open until `close()`; returns 0 on failure), `nativeLastError(): Int`, `nativeCloseDocument(doc: Long)`, `nativePageCount(doc: Long): Int`, `nativePageSize(doc: Long, index: Int): FloatArray` (`[w, h]`, empty on failure).
- Error mapping from `FPDF_GetLastError`: `FPDF_ERR_PASSWORD (4)` → `PdfPasswordException(wrongPassword = password != null)`; every other code → `PdfCorruptException("PDFium error <code>")`.
- `encrypted.pdf`: one A4 page drawing "Encrypted page" like `sample.pdf`; Standard security handler `/V 1 /R 2` (RC4 40-bit), user password `secret`, owner password `owner`, `/P -4`, trailer `/ID` of two equal 16-byte strings. Algorithms 3.1–3.4 of the PDF 1.7 spec; strings and streams encrypted with the per-object key. Output stays deterministic.

- [ ] **Step 1: Write `PdfiumOpenTest`:**
  - `readsPageCountAndSizes`: `sample.pdf` → 3 pages, `PageSize(595, 842)` at 0, `PageSize(842, 595)` at 2.
  - `sizesMatchPdfRenderer`: for `sample.pdf` and `outline.pdf`, `pageSizes` equal `PdfDocumentOpener(...).open(uri).pageSizes`.
  - `invalidFileIsCorrupt`: file containing `hello` → `PdfCorruptException`.
  - `truncatedFileIsCorrupt`: first half of `sample.pdf`'s bytes → `PdfCorruptException`.
  - `missingFileThrowsFileNotFound`: `Uri.fromFile(File(cacheDir, "nope.pdf"))` → `FileNotFoundException`.
  - `encryptedNeedsPassword`: no password → `PdfPasswordException(wrongPassword = false)`; `"wrong"` → `wrongPassword = true`; `"secret"` → 1 page.
- [ ] **Step 2: Run** — Expected: FAIL to compile.
- [ ] **Step 3: Implement natives, `Pdfium`, `PdfiumDocument`, exceptions; extend generator; run `python -I tools/generate_test_pdfs.py`** — Expected: existing three PDFs byte-identical (`git status` shows only `encrypted.pdf` new).
- [ ] **Step 4: Run the test on every device** — Expected: PASS.
- [ ] **Step 5: Commit** — `"Open PDFs with PDFium"`

### Task 3: Render

**Files:**
- Modify: `pdfium_jni.cpp`, `PdfiumNative.kt`, `PdfiumDocument.kt`
- Test: `core/pdf/src/androidTest/java/com/mustafakoceerr/tilepdf/core/pdf/PdfiumRenderTest.kt`

**Interfaces:**
- `suspend fun PdfiumDocument.render(pageIndex: Int, bitmap: Bitmap, transform: Matrix)` — same contract as `PdfDocument.render`: `transform` maps top-left page points to bitmap pixels, caller clears the bitmap. Requires `bitmap.config == ARGB_8888` (`IllegalArgumentException` otherwise) and `pageIndex in 0 until pageCount` (`IllegalArgumentException`); after `close()`, `IllegalStateException`.
- `nativeRender(doc: Long, index: Int, bitmap: Bitmap, matrix: FloatArray): Boolean` — `matrix` is `FS_MATRIX {a,b,c,d,e,f}` built from `Matrix.getValues`: `a=MSCALE_X, b=MSKEW_Y, c=MSKEW_X, d=MSCALE_Y, e=MTRANS_X, f=MTRANS_Y`. Native: `AndroidBitmap_getInfo` (reject non-RGBA_8888), lock pixels, `FPDFBitmap_CreateEx(w, h, FPDFBitmap_BGRA, pixels, stride)`, `FPDF_RenderPageBitmapWithMatrix` with clip `{0,0,w,h}` and flags `FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER`, destroy bitmap, unlock, close page.

- [ ] **Step 1: Write `PdfiumRenderTest`:**
  - `matchesPdfRenderer`: render each `sample.pdf` page at 400 px wide (`transform = scale(400/widthPt)`) with both engines into white bitmaps; per-channel difference ≤ 16 on ≥ 99.5 % of pixels.
  - `tileMatchesWholePage`: render page 0 at 800 px wide whole, and its 256×256 tile at (256, 256) with a translated transform; tile equals the matching region within the same tolerance.
  - `rejectsNonArgb8888Bitmap`: `RGB_565` bitmap → `IllegalArgumentException`.
  - `pageIndexOutOfRangeThrows`: index `3` on `sample.pdf` → `IllegalArgumentException`.
  - `twoDocumentsRenderConcurrently`: open `sample.pdf` and `outline.pdf`, 20 parallel `async` renders alternating documents; all bitmaps non-blank.
  - `closeDuringRenderIsSafe`: launch 10 renders, call `close()` without awaiting; each render either completes or throws `IllegalStateException`; a later `render` throws `IllegalStateException`.
- [ ] **Step 2: Run** — Expected: FAIL to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run on every device** — Expected: PASS.
- [ ] **Step 5: Commit** — `"Render pages with PDFium"`

### Task 4: Page text with character boxes

**Files:**
- Modify: `pdfium_jni.cpp`, `PdfiumNative.kt`, `PdfiumDocument.kt`
- Test: `core/pdf/src/androidTest/java/com/mustafakoceerr/tilepdf/core/pdf/PdfiumTextTest.kt`

**Interfaces:**
- `suspend fun PdfiumDocument.pageText(pageIndex: Int): PageText` — built with `PageText.Builder`; `'\r'` characters are dropped; generated characters (`FPDFText_IsGenerated`) and characters without a box get `null` boxes.
- `nativePageText(doc: Long, index: Int): String` (UTF-16 from `FPDFText_GetUnicode`, one Java char per PDFium char index) and `nativeCharBoxes(doc: Long, index: Int): FloatArray` (4 floats per char, same order: left, top, right, bottom in top-left points; NaN for missing). Conversion uses `FPDF_PageToDevice(page, 0, 0, round(w*64), round(h*64), 0, x, y, &dx, &dy)` and divides by 64, so crop box and `/Rotate` are handled by PDFium with 1/64 pt precision.

- [ ] **Step 1: Write `PdfiumTextTest`** with the expectations of `PdfBoxTextProviderTest`:
  - `extractsPageTextInReadingOrder`: page 0 text `.trim()` == `"TilePDF sample page 1"`.
  - `boxesAreInTopLeftPointSpace`: first boxed char left ≈ 72 (±1), bottom ≈ 100 (±2), height in 15..40.
  - `landscapePageUsesItsOwnHeight`: on page 2 the char box of `'s'` in "sample" has bottom ≈ 100 (±2).
  - `noCarriageReturns`: no page of `text-dense.pdf` contains `'\r'`.
  - `encryptedTextIsReadable`: `encrypted.pdf` with `"secret"` → text contains `"Encrypted page"`.
- [ ] **Step 2: Run** — Expected: FAIL to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run on every device** — Expected: PASS.
- [ ] **Step 5: Commit** — `"Extract page text with PDFium"`

### Task 5: Outline

**Files:**
- Modify: `pdfium_jni.cpp`, `PdfiumNative.kt`, `PdfiumDocument.kt`
- Test: `core/pdf/src/androidTest/java/com/mustafakoceerr/tilepdf/core/pdf/PdfiumOutlineTest.kt`

**Interfaces:**
- `suspend fun PdfiumDocument.outline(): List<OutlineEntry>` — same semantics as `PdfBoxOutlineRepository`: depth-first, entries with a blank title or no resolvable page are skipped but their children are still visited, at most 10 000 entries, `topPt` only for XYZ destinations with a y, coerced to `0..pageHeight`; `null` otherwise.
- Natives: `nativeBookmarkFirstChild(doc: Long, parent: Long): Long` (`parent = 0` for the root), `nativeBookmarkNextSibling(doc: Long, bookmark: Long): Long`, `nativeBookmarkTitle(bookmark: Long): String`, `nativeBookmarkTarget(doc: Long, bookmark: Long): FloatArray` → `[pageIndex, topPt]` (`pageIndex = -1` if none, `topPt = NaN` if none). Target: `FPDFBookmark_GetDest`, else `FPDFBookmark_GetAction` of type `PDFACTION_GOTO` → `FPDFAction_GetDest`; y from `FPDFDest_GetLocationInPage` converted with the Task 4 `FPDF_PageToDevice` scheme. Guard against cyclic outlines with a visited set of bookmark handles.

- [ ] **Step 1: Write `PdfiumOutlineTest`** — `flattensTreeAndResolvesEveryDestinationKind` with exactly the five `OutlineEntry` values of `PdfBoxOutlineRepositoryTest`; `documentWithoutOutlineHasNoEntries` on `sample.pdf`.
- [ ] **Step 2: Run** — Expected: FAIL to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run on every device** — Expected: PASS.
- [ ] **Step 5: Commit** — `"Read the outline with PDFium"`

### Task 6: Docs and spec amendments; full verification

**Files:**
- Modify: `README.md` (engine section: PDFium in `core:pdf`, how binaries are fetched and pinned, how to bump the version)
- Modify: spec — x86 is included (32-bit x86 emulators); links move to phase 4 and annotation writing/saving to phase 5, where they are first used; PDFium/third-party license notices are shown in-app in phase 7.
- Modify: `.gitignore` — add `.kotlin/` (phase 1 deferred minor)

- [ ] **Step 1: Update docs.**
- [ ] **Step 2: Full green build:** `./gradlew assembleDebug assembleDebugAndroidTest test lint` and `connectedDebugAndroidTest` per device — Expected: all pass; release `./gradlew :app:assembleRelease` passes.
- [ ] **Step 3: Commit** — `"Document the PDFium engine"`
