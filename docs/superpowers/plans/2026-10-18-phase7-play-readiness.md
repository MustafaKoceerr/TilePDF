# Phase 7 — Play Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A release build that is shrunk by R8, signed from environment variables and provably offline. It shows every third-party license, including PDFium's, inside the app, and it comes with a real icon, store texts, graphics, a Data Safety answer sheet and a privacy policy, ready for the user to upload to Play.

**Architecture:**
- **R8:** turned on through AGP 9's `optimization {}` block. `core:pdf` ships consumer keep rules for the classes its C++ code builds through JNI.
- **Signing:** a pure function in `build-logic` turns the four environment variables into "signed", "unsigned" or "incomplete", and it is unit-tested there.
- **Offline promise:** the manifest removes `INTERNET`, and a Gradle check fails the build if the merged release manifest asks for any permission outside an allowlist. Backups keep preferences only.
- **Licenses:**
  - Maven dependencies: the AboutLibraries plugin collects them at build time.
  - PDFium: the convention plugin copies the license texts that ship inside the pinned pdfium-binaries archive into `core:pdf`'s generated assets, so updating PDFium updates them too.
  - A new `feature:about` module shows the version, the offline note and both license lists.
- **Store files:**
  - Texts live under `fastlane/metadata/android/{en-US,tr-TR}/` (the layout Play tooling reads), and a JVM test checks Play's length limits.
  - The icon is a new adaptive vector icon.
  - The 512 px icon and the feature graphic are rendered by an instrumented export test.
  - Screenshots are taken on the emulator at a 2:1 size.

**Tech Stack:** as before; adds AboutLibraries 15.2.0 (`com.mikepenz.aboutlibraries.plugin.android`, `aboutlibraries-compose-m3`).

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md`, which covers:
- Aşama 7: R8, imzalama (anahtarlar env'den), Data Safety, mağaza metinleri, PDFium ve diğer üçüncü taraf lisanslarının uygulama içinde gösterimi
- Amaç: ücretsiz, ağ bağlantısı olmayan okuyucu

## Global Constraints

- **Secrets stay out of the repo.** Keystore, passwords and alias come only from environment variables. They are never written to a file in the repo or a log line, and the build never prints them, even in an error message.
- **Signing variables:**
  - The four variables are `TILEPDF_UPLOAD_KEYSTORE` (path to the .jks), `TILEPDF_UPLOAD_KEYSTORE_PASSWORD`, `TILEPDF_UPLOAD_KEY_ALIAS` and `TILEPDF_UPLOAD_KEY_PASSWORD`.
  - All four set: release is signed. None set: release builds unsigned and the build logs one warning. Some set: the build fails and names the missing ones.
- **No network:**
  - The release manifest has no `INTERNET` permission.
  - Merged release permissions must be a subset of the allowlist: `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (WorkManager's), and `com.mustafakoceerr.tilepdf.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx.core).
  - Anything else fails `checkReleasePermissions`, which `assembleRelease` and `bundleRelease` depend on.
- **Version:** `versionCode = 1`, `versionName = "1.0.0"`.
- **Backups:** only DataStore preferences are backed up, through both `backup_rules.xml` and `data_extraction_rules.xml` (cloud backup and device transfer). The Room database, private PDF copies and caches are excluded, because restored URIs have no grants.
- **Store text limits:** `title.txt` ≤ 30, `short_description.txt` ≤ 80, `full_description.txt` ≤ 4000 and `changelogs/1.txt` ≤ 500 characters, for both `en-US` and `tr-TR`. Screenshots must be no more than twice as long as they are wide.
- **Outward-facing steps are the user's:** creating the real upload key, hosting the privacy policy and anything in Play Console. This plan produces the files and the instructions, `docs/RELEASING.md`.
- **Language and theme:** none of this work touches them (the user's own libraries will). The About screen's strings get EN and TR like every other screen.
- **Test rules:** JVM unit tests drive TDD (`--tests`). UI tests, the asset export, the release smoke test and screenshots run in Task 5 with the user's approval.

## Review Focus

1. **R8 strips a class that JNI builds** (`NativePageText`, `NativeAnnotation`), which would crash the first page with text or annotations. The Task 1 release smoke test opens, searches, selects and annotates a document.
2. **A WorkManager save after R8:** `SaveAnnotationsWorker` is created through Hilt's worker factory. The Task 1 smoke test saves annotations, leaves the reader and reopens the document.
3. **Half-configured signing,** for example in CI where one secret is missing, must fail loudly and name the missing variables, not ship an unsigned build silently. Covered by Task 2 `partialEnvFailsNamingMissing`, which also checks that no secret value appears in the message.
4. **A new dependency quietly adds `INTERNET`.** Covered by Task 3 `checkReleasePermissions`, checked by temporarily adding the permission and watching the build fail.
5. **PDFium is updated but its license texts are not.** Covered by Task 4 `pdfiumLicensesComeFromTheArchive`: the assets are generated from the pinned archive, and the test checks names come from file names.

---

### Task 1: R8 for release

**Files:**
- Modify: `app/build.gradle.kts`: release `optimization { enable = true; keepRules { files.add(file("proguard-rules.pro")) } }`, plus the version values.
- Create:
  - `app/proguard-rules.pro`, for app-only rules if the smoke test needs any (empty with a comment otherwise);
  - `core/pdf/consumer-rules.pro`, which keeps `NativePageText` and `NativeAnnotation` with their constructors, plus the class and native methods of the JNI bridge;
  - wire it in `core/pdf/build.gradle.kts` through `consumerProguardFiles` or the library `optimization { keepRules }` equivalent in AGP 9.4.

**Interfaces:** none.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Build** with `./gradlew assembleRelease`. Expected: BUILD SUCCESSFUL, and `app/build/outputs/mapping/release/mapping.txt` exists. Record the APK size before and after.
- [ ] **Step 3: Release smoke test** (with approval). Sign the release APK with the debug key for this check only, using a local `-P` flag that never ships (`-Ptilepdf.signReleaseWithDebug=true`, read only in `app/build.gradle.kts`). Install it and:
  1. open a PDF from the library;
  2. search for a word and select text;
  3. highlight, draw and add a note;
  4. leave the reader and reopen: the annotations are there;
  5. open the TOC, bookmarks and the password fixture;
  6. switch the page theme.

  Check `logcat` for crashes. A crash means a missing keep rule: add the narrowest rule and repeat.
- [ ] **Step 4: Commit** `"Shrink release builds with R8"`.

### Task 2: Signing from the environment

**Files:**
- Create: `build-logic/convention/src/main/kotlin/com/mustafakoceerr/tilepdf/ReleaseSigning.kt`, `build-logic/convention/src/test/kotlin/com/mustafakoceerr/tilepdf/ReleaseSigningTest.kt` (+ `testImplementation(libs.junit)` in `build-logic/convention/build.gradle.kts`), and `docs/RELEASING.md`. The doc covers:
  - creating an upload key with `keytool` outside the repo;
  - the four variables;
  - `./gradlew bundleRelease`;
  - Play App Signing;
  - where the bundle lands.
- Modify: `app/build.gradle.kts`: `signingConfigs.create("release")` only when the result is `Configured`; release uses it; `Incomplete` throws `GradleException(result.message)`; `Unsigned` logs one warning. Task 1's local `-Ptilepdf.signReleaseWithDebug=true` applies only when the result is `Unsigned`, and never overrides real signing.

**Interfaces:**
- Produces:

```kotlin
sealed interface ReleaseSigning {
    data class Configured(val storeFile: String, val storePassword: String, val keyAlias: String, val keyPassword: String) : ReleaseSigning {
        override fun toString() = "Configured(storeFile=$storeFile)" // never the secrets
    }
    data object Unsigned : ReleaseSigning
    data class Incomplete(val missing: List<String>) : ReleaseSigning { val message: String }
}
fun releaseSigning(env: (String) -> String?): ReleaseSigning
```

Blank counts as missing.

- [ ] **Step 1: Write the failing tests.**

```kotlin
@Test fun allFourSignRelease()       // all set → Configured with those values
@Test fun noneLeavesUnsigned()       // none set → Unsigned
@Test fun blankCountsAsMissing()     // password "" → Incomplete(listOf("TILEPDF_UPLOAD_KEYSTORE_PASSWORD"))
@Test fun partialEnvFailsNamingMissing() // only keystore + alias → Incomplete with both password names; message contains both names and neither the keystore path's secret values nor any password
@Test fun configuredHidesSecrets()   // toString() contains no password and no alias
```

- [ ] **Step 2: Run** `./gradlew -p build-logic :convention:test`, or the root-relative equivalent the build accepts. Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement**, and wire it into `app/build.gradle.kts`.
- [ ] **Step 4: Run** the tests. Expected: PASS. Then:
  1. With no variables: `./gradlew bundleRelease` succeeds and prints one warning.
  2. With a throwaway keystore made by `keytool` in the scratchpad (never in the repo), all four variables set: `bundleRelease` succeeds and `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` reports it verified.
  3. With only two variables set: the build fails and names the other two.

  Delete the throwaway keystore afterwards.
- [ ] **Step 5: Commit** `"Sign release builds from environment variables"`.

### Task 3: Offline guarantee, backups, Data Safety, privacy policy

**Files:**
- Modify:
  - `app/src/main/AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`;
  - `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`: include `domain="file" path="datastore/"` only; replace the template comments.
- Create:
  - `build-logic/convention/src/main/kotlin/com/mustafakoceerr/tilepdf/ReleasePermissions.kt`, holding the pure function `unexpectedPermissions(manifestXml: String, allowed: Set<String>): List<String>`, and its test `ReleasePermissionsTest.kt`;
  - a task `checkReleasePermissions` registered by `AndroidApplicationConventionPlugin`. It reads the merged release manifest through the variant API's `artifacts.get(SingleArtifact.MERGED_MANIFEST)`, and `assembleRelease` and `bundleRelease` depend on it.
- Create documents:
  - `docs/play/data-safety.md` gives the answers: no data collected, no data shared, no network access; it lists each permission with the reason the library needs it and notes that files stay on the device.
  - `docs/play/privacy-policy.md` and `docs/play/privacy-policy-tr.md` say that TilePDF has no network access and collects, stores remotely and shares nothing. Opened files, annotations and preferences stay on the device. Android backup copies preferences only. A contact line uses the developer's GitHub profile URL, never an e-mail.

**Interfaces:**
- Produces: `unexpectedPermissions(manifestXml, allowed)` and the `RELEASE_PERMISSION_ALLOWLIST` constant (Global Constraints).

- [ ] **Step 1: Write the failing tests.**

```kotlin
@Test fun allowedPermissionsPass()   // manifest with WAKE_LOCK + ACCESS_NETWORK_STATE → emptyList()
@Test fun internetIsReported()       // manifest with INTERNET → listOf("android.permission.INTERNET")
@Test fun removedNodesDontCount()    // a merged manifest never contains tools:node="remove" entries; one with uses-permission-sdk-23 INTERNET is still reported
```

- [ ] **Step 2: Run** the build-logic tests. Expected: FAIL.
- [ ] **Step 3: Implement**, including the manifest, the backup rules and the documents.
- [ ] **Step 4: Verify.**
  1. `./gradlew assembleRelease` runs `checkReleasePermissions` and passes.
  2. Temporarily add a `<uses-permission android:name="android.permission.CAMERA"/>` and see the build fail with that name, then revert.
  3. `aapt2 dump permissions` (or `apkanalyzer manifest permissions`) on the release APK lists no `INTERNET`.
- [ ] **Step 5: Commit** `"Prove the release stays offline; back up preferences only; Data Safety and privacy policy"`.

### Task 4: Licenses and the About screen

**Files:**
- Modify:
  - `build-logic/.../PdfiumConventionPlugin.kt`: register `syncPdfiumLicenses` (a `Sync`) that copies `licenses/*` from the arm64-v8a archive directory, plus its root `LICENSE` as `pdfium-binaries.txt`, into `build/generated/pdfiumLicenses/licenses/pdfium/`. Add that directory as an assets source of the module through the variant API, after the downloads.
  - `gradle/libs.versions.toml`: AboutLibraries 15.2.0 (plugin + `aboutlibraries-compose-m3`).
  - Root `build.gradle.kts` (plugin `apply false`) and `app/build.gradle.kts` (apply the Android plugin):
    - `aboutLibraries { offlineMode = true; export { excludeFields.addAll("developers", "funding") } }`;
    - collect only release dependencies.
  - `settings.gradle.kts`: + `:feature:about`.
  - App navigation: an `AboutRoute` from the library's ⋮ menu item "About" / "Hakkında".
  - The library's `LibraryActions` and screen: `onAbout`.
- Create: `feature/about`, a feature module with these files:
  - `AboutScreen.kt`: the app name and `versionName` (from `PackageManager`); the line "TilePDF works without an internet connection. Your files and notes stay on this device." / "TilePDF internet bağlantısı olmadan çalışır. Dosyaların ve notların bu cihazda kalır."; a section "PDFium and its components" / "PDFium ve bileşenleri" listing the asset files, where tapping one opens its text in a dialog; and a section "Open source libraries" / "Açık kaynak kütüphaneler" rendered by AboutLibraries' `LibrariesContainer`.
  - `PdfiumLicenses.kt`: `internal fun componentName(fileName: String): String` and `internal fun pdfiumLicenseFiles(names: List<String>): List<String>`, which sorts them, puts `pdfium.txt` first and drops anything that isn't `.txt`, `.md` or `.ijg`.
  - Strings in `values/` and `values-tr/`.
- Test: JVM `feature/about/src/test/.../PdfiumLicensesTest.kt`; UI `feature/about/src/androidTest/.../AboutScreenTest.kt` (runs in Task 5).

**Interfaces:**
- Produces: `AboutRoute`, `aboutScreen(onBack)` (NavGraphBuilder) and `navigateToAbout()`, following the existing library and reader route pattern.

- [ ] **Step 1: Write the failing tests.**

```kotlin
@Test fun namesComeFromFiles() {
    assertEquals("libjpeg-turbo", componentName("libjpeg_turbo.md"))
    assertEquals("FreeType", componentName("freetype.txt"))      // known names keep their usual spelling
    assertEquals("pdfium-binaries", componentName("pdfium-binaries.txt"))
}
@Test fun pdfiumComesFirst() = assertEquals("pdfium.txt", pdfiumLicenseFiles(listOf("zlib.txt", "pdfium.txt", "abseil.txt")).first())
@Test fun pdfiumLicensesComeFromTheArchive() // other files (".DS_Store", "README") are dropped; ".ijg" kept
```

  Known display names live in a map: abseil→Abseil, agg23→AGG, freetype→FreeType, icu→ICU, lcms→Little CMS, libjpeg_turbo→libjpeg-turbo, libopenjpeg→OpenJPEG, libpng→libpng, pdfium→PDFium, zlib→zlib, simdutf→simdutf, fast_float→fast_float, cpu_features→cpu_features, catapult→Catapult, libunwind→libunwind, llvm-libc→LLVM libc. An unknown name falls back to the file name without its extension.
- [ ] **Step 2: Run** `./gradlew :feature:about:testDebugUnitTest --tests "*PdfiumLicensesTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** The screen reads the asset list with `context.assets.list("licenses/pdfium")`.
- [ ] **Step 4: Run** the test. Expected: PASS. Then run `./gradlew assembleRelease` and confirm:
  - the APK contains `assets/licenses/pdfium/pdfium.txt`, all 17 archive license files and `pdfium-binaries.txt`;
  - the generated `aboutlibraries.json` lists AndroidX and Kotlin libraries with license text.
- [ ] **Step 5: Write the UI test** `AboutScreenTest`: `showsVersionAndPdfium` checks that the version text and "PDFium" are displayed, and tapping PDFium shows text containing "The PDFium Authors". Compile with `assembleDebugAndroidTest`.
- [ ] **Step 6: Commit** `"About screen with PDFium and open source licenses"`.

### Task 5: Icon, store listing, graphics, verify and ship

**Files:**
- Replace the template launcher icon with:
  - `app/src/main/res/drawable/ic_launcher_foreground.xml`: a page with a folded top-right corner and a 2×2 tile grid on its lower half, inside the 66 dp safe zone;
  - `ic_launcher_background.xml`: a solid `#3F51B5`;
  - `ic_launcher_monochrome.xml`: the same shapes in one colour, used by `mipmap-anydpi/ic_launcher*.xml` for themed icons.
- Create:
  - `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt` and `changelogs/1.txt`, plus the same under `tr-TR/`.
  - Title "TilePDF – PDF Reader" / "TilePDF – PDF Okuyucu".
  - Short descriptions: "Fast, private PDF reader with notes. No internet, no ads, no account." / "Hızlı ve gizli PDF okuyucu, notlarla. İnternet yok, reklam yok, hesap yok."
  - The full description covers reading (zoom, search, TOC, bookmarks, themes), annotations saved into the PDF, the library, large screens and privacy (no network permission).
- Test:
  - JVM `app/src/test/java/com/mustafakoceerr/tilepdf/StoreListingTest.kt` checks every limit for both locales and that each file exists and isn't blank. It reads `../fastlane/...` relative to the module directory.
  - The instrumented `app/src/androidTest/.../StoreGraphicsExport.kt` renders the adaptive icon at 512 × 512 px and a feature graphic at 1024 × 500 px (icon, "TilePDF" and the short description on the icon's background colour) with `captureToImage()`. It writes PNGs to the app's external files directory.
- Outputs: `fastlane/metadata/android/{locale}/images/{icon.png,featureGraphic.png,phoneScreenshots/*.png,tenInchScreenshots/*.png}`.

- [ ] **Step 1: Write** `StoreListingTest`. Run `./gradlew :app:testDebugUnitTest --tests "*StoreListingTest"`. Expected: FAIL (files missing).
- [ ] **Step 2: Write the texts and the icon.** Run the test. Expected: PASS.
- [ ] **Step 3: UI tests** (with approval), one class at a time: `AboutScreenTest`, `LibraryScreenTest`, `MainActivitySmokeTest`. Expected: PASS.
- [ ] **Step 4: Graphics** (with approval):
  - Run `StoreGraphicsExport` and `adb pull` the PNGs into `images/`.
  - Phone screenshots (4): library, reader, annotations, search, taken with `adb shell wm size 1080x2160`.
  - Tablet screenshots (2): side panel, library columns, taken with `wm size 2560x1600` and `wm density 320`.
  - Reset size and density afterwards.
  - Check that each image's long side is ≤ 2 × its short side.
- [ ] **Step 5: Full run.** `./gradlew assembleDebug assembleRelease testDebugUnitTest :core:model:test lintDebug` plus the build-logic tests. Expected: BUILD SUCCESSFUL.
- [ ] **Step 6:** Update the README with release notes and links to `docs/RELEASING.md` and `docs/play/`. Commit `"Launcher icon, store listing and graphics"`, push, then open the PR stacked on `phase6c-adaptive`.
