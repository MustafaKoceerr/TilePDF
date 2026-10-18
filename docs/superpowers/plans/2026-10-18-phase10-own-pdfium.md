# Phase 10: Our Own PDFium Build and Turkish Letters in Forms — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** TilePDF links its own PDFium build, with V8 and XFA plus a fallback-font patch, so that letters a form field's font lacks are drawn and embedded.

**Architecture:**
- **The build.** The PDFium binaries come from the fork `MustafaKoceerr/pdfium-binaries`, branch `tilepdf`, which builds `chromium/8076` with V8 and XFA.
  - The patch `patches/tilepdf/form-fallback-font.patch` adds `FPDF_TilePdfSetFormFallbackFont(data, size)` to PDFium.
  - `CPDF_BAFontMap::GetWordFontIndex` tries that font before the Windows-only native fonts.
  - The first field that needs it embeds it as a Type0/Identity-H font under `/DR /Font /TilePdfFallback`.
- **The app.** It hands PDFium a 40 KB Noto Sans subset from `core:pdf` assets when it initialises the library.

**Tech Stack:** PDFium chromium/8076 (V8 and XFA), JNI/C++17, Kotlin, the Gradle convention plugin.

**Spec:** the roadmap at `~/.claude/plans/nifty-yawning-lark.md`, phase 10. It was approved on 2026-10-18.

## Global Constraints
- **Pinned binaries.** Builds are pinned by SHA-256, as today (`DownloadPdfiumTask`).
- **No network at runtime.** The release manifest permission check stays green.
- **A missing or broken font never stops a document from opening.** Without it, fields behave as before.
- **Licences.** Noto Sans (OFL-1.1) and the V8 licence texts show on the About screen.

## Review Focus
- **A document that already has `/DR /Font /TilePdfFallback`**, for example one saved by TilePDF: the font is reused, not embedded again.
- **A field whose own font has every letter:** its appearance does not change. The fallback is only used for missing characters.
- **Many fields filled in one document:** one embedded font copy.
- **A document without an AcroForm `/DR`:** `/DR` is created; no crash.
- **The font asset fails to load:** forms still work, as before.

---

### Task 1: The PDFium fork and release
**Files:** the fork's `patches/tilepdf/form-fallback-font.patch` and `steps/03-patch.sh` (done on branch `tilepdf`).

- [ ] Run `build-one.yml` on `--ref tilepdf` with `branch=chromium/8076`, `target_os=android` and `enable_v8=true`, once for each CPU: arm, arm64, x64, x86.
- [ ] Download the four artifacts, tar each as `pdfium-v8-android-<cpu>.tgz`, and run `gh release create tilepdf-8076-1` on the fork with the four tarballs and `SHA256SUMS`.

Expected:
- All four builds are green.
- `nm -D lib/libpdfium.so | grep FPDF_TilePdfSetFormFallbackFont` finds the symbol.
- `LOAD` segments are 16 KB aligned, as checked by `llvm-readelf -l`.

### Task 2: Link the new build
**Files:**
- Modify: `build-logic/convention/src/main/kotlin/PdfiumConventionPlugin.kt`
  - `RELEASE` becomes `tilepdf-8076-1`.
  - The base URL becomes the fork.
  - `ARCHIVES` becomes the `pdfium-v8-android-*` names with their new hashes.
- Modify: `feature/about/.../PdfiumLicenses.kt`, giving display names for the licence files the new archive adds (for example `v8`).

Expected:
- `./gradlew :core:pdf:assembleDebug` succeeds.
- The About screen's licence list (`PdfiumLicensesTest`) is still green.

### Task 3: The fallback font
**Files:**
- Create: `core/pdf/src/main/assets/fonts/NotoSans-TilePdf.ttf`.
  - Noto Sans Regular 400/100 static instance.
  - Subset to U+0020–007E, U+00A0–017F, U+0218–021B, Greek, basic Cyrillic and U+0490–0491; common punctuation, €, ₺, № and ™.
  - No hinting, no layout tables.
- Create: `core/pdf/src/main/java/.../FormFallbackFont.kt`.
  - `internal object FormFallbackFont { const val ASSET = "fonts/NotoSans-TilePdf.ttf"; fun read(open: (String) -> InputStream): ByteArray? }`
  - It returns null when reading fails.
- Modify: `PdfiumNative.nativeInit(formFallbackFont: ByteArray?)`, `Pdfium.openNow` and `pdfium_jni.cpp` `nativeInit`.
  - JNI calls `FPDF_TilePdfSetFormFallbackFont` once, after `FPDF_InitLibraryWithConfig`.
- Create: `config/aboutlibraries/libraries/noto-sans.json` and `config/aboutlibraries/licenses/ofl-1.1.json`.
- Test: `core/pdf/src/test/.../FormFallbackFontTest.kt`
  - `readsTheAsset`: the bytes come from `open(ASSET)`.
  - `missingFontIsNull`: `open` throws IOException → null.
  - `bundledFontCoversTurkish`: parse the real asset's `cmap` (a test helper); every character of `"İıŞşĞğÇçÖöÜü₺€"` and ASCII 0x20–0x7E is mapped.

### Task 4: Device test and docs
- Test: in `core/pdf/src/androidTest/.../PdfiumFormTest.kt`, add `turkishLettersInHelveticaFieldAreDrawnAndEmbedded`.
  - Fill the Helvetica text field of `forms.pdf` with `"İŞĞışğ"`, save, and reopen.
  - The value reads back.
  - The rendered field has at least 80% of the dark pixels of the same field filled with `"ISGisg"`.
- Modify: `README.md`.
  - Drop the Turkish-letters limitation.
  - Mention our own PDFium build (V8 and XFA) and where it is built.
- Measure:
  - `am start -W` cold start before and after;
  - how long the first document takes to open, from logcat.

Expected: `PdfiumFormTest` passes on emulator-5556, and the numbers are reported in the PR.

## Verification
- `./gradlew testDebugUnitTest lintDebug assembleRelease checkReleasePermissions`
- `:core:pdf:connectedDebugAndroidTest -P...class=...PdfiumFormTest` on emulator-5556.
- By hand on the emulator: type "İstanbul Şişli Ağaç ılık" into the forms.pdf text field. It shows complete on screen, and again after saving and reopening.
