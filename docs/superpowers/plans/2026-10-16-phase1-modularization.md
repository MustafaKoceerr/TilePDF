# Phase 1 — Modularization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the single `app` module into NiA-style `core:*` / `feature:*` modules with convention plugins, without changing any behavior.

**Architecture:** An included `build-logic` build provides convention plugins. Code moves bottom-up (model/common → pdf/render → data → domain/designsystem → features), and after every task `app` still builds, all existing tests pass, and the app runs. Kotlin packages stay as they are; only file locations, Gradle files, `R` references and a few visibility modifiers change.

**Tech Stack:** AGP 9.4.1 (built-in Kotlin), Gradle 9.6, Kotlin 2.2.10, KSP 2.3.12, Hilt 2.60.1, Room 2.8.5, Compose BOM 2026.02.01.

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (Task 7 amends it; see "Spec amendments").

## Global Constraints

- `applicationId` and `app` namespace stay `com.mustafakoceerr.tilepdf`; `minSdk = 29`, `targetSdk = 37`, `compileSdk = 37`; Java/JVM target 11.
- Room database file name stays `"tilepdf.db"`, schema version and exported JSON unchanged.
- DataStore file name stays `"reader_preferences"`.
- Kotlin packages are **not** renamed in this phase. Move files with `git mv` so history follows.
- No behavior change, no new features, no dependency version bumps.
- Every module gets a unique Android namespace: `com.mustafakoceerr.tilepdf.<module path with dots>`, e.g. `:core:render` → `com.mustafakoceerr.tilepdf.core.render`.
- Dependency direction: `app → feature:* → core:domain → core:data → core:{pdf,database,datastore} → core:{model,common}`. Features also use `core:data`, `core:render` and `core:designsystem`. Feature modules never depend on each other.
- No new secrets, no network permission.

## Module map

| Module | Type / convention plugins | Receives (from `app/src/main/java/com/mustafakoceerr/tilepdf/`) |
|---|---|---|
| `core:model` | `tilepdf.jvm.library` | `core/geometry/*`, `core/pdf/PageSize.kt`, `model/*` |
| `core:common` | `tilepdf.android.library`, `tilepdf.hilt` | `core/common/*`, `di/DispatchersModule.kt` |
| `core:pdf` | `tilepdf.android.library`, `tilepdf.hilt` | `core/pdf/PdfDocument.kt`, `PdfDocumentOpener.kt`, `PdfRendererOpening.kt` |
| `core:render` | `tilepdf.android.library.compose`, `tilepdf.hilt` | `core/render/*` |
| `core:testing` | `tilepdf.android.library` | `androidTest/.../testing/TestAssets.kt`, `androidTest/assets/*.pdf` |
| `core:database` | `tilepdf.android.library`, `tilepdf.android.room`, `tilepdf.hilt` | `data/local/*`, Room part of `di/DatabaseModule.kt`, `app/schemas/` |
| `core:datastore` | `tilepdf.android.library`, `tilepdf.hilt` | DataStore part of `di/DatabaseModule.kt` → new `di/DataStoreModule.kt` |
| `core:data` | `tilepdf.android.library`, `tilepdf.hilt` | `data/{document,library,outline,pdfbox,preferences,text}/*`, `core/text/*`, `di/DataModule.kt`, `di/TextModule.kt` |
| `core:domain` | `tilepdf.android.library`, `tilepdf.hilt` | `domain/*` |
| `core:designsystem` | `tilepdf.android.library.compose` | `ui/theme/*`, `res/drawable/ic_*.xml` except launcher |
| `feature:library` | `tilepdf.android.feature` | `feature/library/**`, `library_*` strings |
| `feature:reader` | `tilepdf.android.feature` | `feature/reader/**`, `reader_*` strings, `cancel`, `navigate_back` |
| `app` | `tilepdf.android.application(.compose)`, `tilepdf.hilt` | keeps `MainActivity`, `TilePdfApp`, `navigation/`, manifest, launcher icons, `themes.xml`, `xml/` |

Why `core/text` goes to `core:data`, not `core:domain`: `TextSearcher` is used by `PdfBoxTextProvider`/`HybridTextProvider` (data), and it shares the `internal object LineMerger` with `TextSelection`. Splitting them would need `LineMerger` public and `data → domain`, which inverts the layer direction.

## Review Focus

1. **Existing installs keep their library and settings.** An updated app must open the same `tilepdf.db` and `reader_preferences` files. Pinned in Task 4: the moved schema JSON files are byte-identical (`git diff --stat -M` shows 100% renames) and `TilePdfDatabaseMigrationTest` passes from `core:database`; Task 4 also asserts the DataStore file name.
2. **The app still starts.** Hilt validates the graph only at `app` compile time, and nothing launches the UI today. Task 1 adds `MainActivitySmokeTest` (launches `MainActivity`, asserts the library title); it is re-run after every task.
3. **Test PDFs reach every module's test APK.** Fixtures move to `core:testing` main assets; if AGP doesn't merge them into a library's androidTest APK, tests fail with `FileNotFoundException`. Pinned in Task 3 by running `:core:pdf:connectedDebugAndroidTest` before anything else depends on the fixtures.
4. **Icons and strings resolve after the `R` split.** `nonTransitiveRClass` (AGP default) means a feature's `R` no longer contains design-system drawables. Pinned by compilation plus the smoke test rendering the library screen (FAB uses `ic_add`).
5. **`internal` across new module boundaries.** `Context.openPdfRenderer` (`core:pdf`) is used by `NativePdfTextProvider` (`core:data`); Task 3 makes it public with a KDoc line. The other `internal` members are only used within their new module (verified by grep while planning).

## Devices

Instrumented tests need a device or emulator. `adb` isn't on PATH; use `"$(grep sdk.dir local.properties | cut -d= -f2 | sed 's/\\\\/\//g; s/\\:/:/')/platform-tools/adb" devices`. If no device is attached, run JVM tests and `assembleDebug assembleDebugAndroidTest`, and report the instrumented runs as **not run** in the task's handoff — never as passed.

Standard verification used below, "**green build**":

```bash
./gradlew assembleDebug assembleDebugAndroidTest test
./gradlew connectedDebugAndroidTest   # when a device is attached
```
Expected: `BUILD SUCCESSFUL`, no test lost between tasks (JVM: 9 test classes; instrumented: 8 classes + `MainActivitySmokeTest`, + `DataStoreModuleTest` from Task 4).

---

### Task 1: build-logic and convention plugins; app smoke test

**Files:**
- Create: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/` — `AndroidApplicationConventionPlugin.kt`, `AndroidApplicationComposeConventionPlugin.kt`, `AndroidLibraryConventionPlugin.kt`, `AndroidLibraryComposeConventionPlugin.kt`, `AndroidFeatureConventionPlugin.kt`, `AndroidRoomConventionPlugin.kt`, `HiltConventionPlugin.kt`, `JvmLibraryConventionPlugin.kt`, `com/mustafakoceerr/tilepdf/KotlinAndroid.kt` (shared SDK/Java config)
- Modify: `settings.gradle.kts` (`pluginManagement { includeBuild("build-logic") }`), `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- Test: `app/src/androidTest/java/com/mustafakoceerr/tilepdf/MainActivitySmokeTest.kt`

**Interfaces:**
- Produces plugin ids (registered in `build-logic/convention/build.gradle.kts` under `gradlePlugin { plugins { … } }`):
  - `tilepdf.android.application`: `com.android.application`, compile/min/target SDK, Java 11, test runner
  - `tilepdf.android.application.compose` / `tilepdf.android.library.compose`: Kotlin Compose plugin, `buildFeatures.compose = true`, Compose BOM + `ui`, `ui-graphics`, `ui-tooling-preview`, `material3`; `debugImplementation` `ui-tooling`, `ui-test-manifest`
  - `tilepdf.android.library`: `com.android.library`, same SDK/Java values; `testImplementation` junit, coroutines-test, turbine; `androidTestImplementation` androidx-junit, espresso
  - `tilepdf.android.feature`: applies `tilepdf.android.library.compose`, `tilepdf.hilt`, Kotlin serialization; adds `:core:model`, `:core:data`, `:core:domain`, `:core:designsystem`, lifecycle-runtime-compose, lifecycle-viewmodel-compose, navigation-compose, hilt-navigation-compose, kotlinx-serialization-json
  - `tilepdf.hilt`: KSP + `com.google.dagger.hilt.android`; `hilt-android`, `ksp(hilt-compiler)`
  - `tilepdf.android.room`: `androidx.room` + KSP; room runtime/ktx, `ksp(room-compiler)`, `androidTestImplementation(room-testing)`; `room { schemaDirectory("$projectDir/schemas") }`
  - `tilepdf.jvm.library`: `org.jetbrains.kotlin.jvm`, JVM target 11, junit + coroutines-test
- Catalog additions: `[libraries]` `android-gradlePlugin` (`com.android.tools.build:gradle`, ref `agp`), `kotlin-gradlePlugin` (ref `kotlin`), `compose-gradlePlugin` (`org.jetbrains.kotlin:compose-compiler-gradle-plugin`, ref `kotlin`), `ksp-gradlePlugin`, `room-gradlePlugin`, `hilt-gradlePlugin` — all `compileOnly` in build-logic; `[plugins]` `kotlin-jvm` (ref `kotlin`), `android-library` (ref `agp`), plus one entry per convention id with `version = "unspecified"`.
- Root `build.gradle.kts` adds `android.library` and `kotlin.jvm` with `apply false`, so convention plugins resolve them by id.

AGP 9 uses built-in Kotlin: do **not** apply `org.jetbrains.kotlin.android`. Use `com.android.build.api.dsl.ApplicationExtension` / `LibraryExtension`. Consult the `agp-9-upgrade` skill for DSL questions.

- [ ] **Step 1: Write the failing smoke test**

```kotlin
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesIntoLibrary() {
        compose.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.library_title)
        ).assertIsDisplayed()
    }
}
```
This one is a regression guard, not test-first: it should pass on the current code. Run it on a device (`./gradlew :app:connectedDebugAndroidTest --tests '*MainActivitySmokeTest'`) and confirm PASS. If no device, record it as not run. In Task 6, `R` here becomes `com.mustafakoceerr.tilepdf.feature.library.R`.

- [ ] **Step 2: Create `build-logic` and the plugins above; switch `app/build.gradle.kts` to** `tilepdf.android.application`, `tilepdf.android.application.compose`, `tilepdf.android.room`, `tilepdf.hilt`, `kotlin.serialization`, keeping only app-specific config (`applicationId`, `versionCode`, `versionName`, `buildTypes`) and the remaining dependencies.
- [ ] **Step 3: Green build.** Expected: `BUILD SUCCESSFUL`, all existing tests + smoke test pass.
- [ ] **Step 4: Commit** — `git commit -m "Add build-logic convention plugins"`

### Task 2: `core:model` and `core:common`

**Files:**
- Create: `core/model/build.gradle.kts`, `core/common/build.gradle.kts`; register both in `settings.gradle.kts`
- Move: see Module map; tests `CoordinateMapperTest.kt`, `ViewportTest.kt` → `core/model/src/test/java/...` (same package path)
- Modify: `app/build.gradle.kts` (add `implementation(projects.core.model)`, `implementation(projects.core.common)`; enable `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` in `settings.gradle.kts`)

**Interfaces:**
- Produces: `@IoDispatcher`, `@PdfDispatcher`, `@ApplicationScope` (package `com.mustafakoceerr.tilepdf.di`), `runCatchingCancellable`, `PageSize`, `PdfRect`, `PixelRect`, `PageLayout`, `Viewport`, `VisiblePage`, `CoordinateMapper`, `LibraryDocument`, `OutlineEntry`, `PageText`, `SearchMatch`, `ReadingPosition` — unchanged names and packages.
- `core:model` has no Android dependencies; if a moved file fails to compile there, stop and report it rather than adding an Android dependency.

- [ ] **Step 1: Move files with `git mv`, create Gradle files, wire `app`.**
- [ ] **Step 2: Green build.** Expected: `:core:model:test` runs 2 test classes, passes.
- [ ] **Step 3: Commit** — `"Extract core:model and core:common"`

### Task 3: `core:pdf`, `core:testing`, `core:render`

**Files:**
- Create: Gradle files for the three modules; register in settings.
- Move: `core/pdf/{PdfDocument,PdfDocumentOpener,PdfRendererOpening}.kt` → `core/pdf/src/main/java/...`; `core/render/*` → `core/render/src/main/java/...`
- Move: `app/src/androidTest/assets/*.pdf` → `core/testing/src/main/assets/`; `TestAssets.kt` → `core/testing/src/main/java/com/mustafakoceerr/tilepdf/testing/`
- Move tests: `PdfDocumentTest` → `core/pdf/src/androidTest`; `BitmapPoolTest`, `TileRenderTest`, `TiledPageImageSourceTest` → `core/render/src/androidTest`; `TileGridTest` → `core/render/src/test`
- Modify: `PdfRendererOpening.kt:18` — `internal fun Context.openPdfRenderer` → public; KDoc: "Public only for NativePdfTextProvider; removed with PdfRenderer in phase 3."
- Modify: `tools/generate_test_pdfs.py` output dir and docstring → `core/testing/src/main/assets/`
- Modify: `README.md` Testing section paths

**Interfaces:**
- `core:pdf` depends on `core:model`, `core:common`, `androidx.core.ktx`.
- `core:testing` depends on `core:pdf`, `androidx.test:core`/`runner` (`api`), coroutines; consumed only as `androidTestImplementation(projects.core.testing)`.
- `core:render` depends on `core:pdf`, `core:model`, `core:common`, `androidx.core.ktx`, `androidx.annotation`.
- `TestAssets.uri(name)`, `uriString(name)`, `openDocument(name)` unchanged.

- [ ] **Step 1: Move `core:pdf` + `core:testing`, wire both; run** `./gradlew :core:pdf:connectedDebugAndroidTest`. Expected: `PdfDocumentTest` PASS — proves assets from `core:testing` reach a library test APK (Review Focus 3). If it fails with `FileNotFoundException`, keep `TestAssets` in `core:testing` but stop and report before choosing another asset mechanism.
- [ ] **Step 2: Run** `python -I tools/generate_test_pdfs.py && git status --short core/testing` — Expected: no changes (generator writes identical bytes to the new location).
- [ ] **Step 3: Move `core:render` and its tests; wire `app`.**
- [ ] **Step 4: Green build.**
- [ ] **Step 5: Commit** — `"Extract core:pdf, core:render and core:testing"`

### Task 4: `core:database`, `core:datastore`, `core:data`

**Files:**
- Create: Gradle files; `core/datastore/src/main/java/com/mustafakoceerr/tilepdf/di/DataStoreModule.kt` (the `providesPreferencesDataStore` provider moved verbatim from `DatabaseModule`)
- Move: `data/local/*`, `di/DatabaseModule.kt` (Room providers only) → `core:database`; `app/schemas/**` → `core/database/schemas/**`; `TilePdfDatabaseMigrationTest` → `core/database/src/androidTest`
- Move: `data/{document,library,outline,pdfbox,preferences,text}/*`, `core/text/*`, `di/DataModule.kt`, `di/TextModule.kt` → `core:data`
- Move tests: `TextSearcherTest`, `TextSelectionTest` → `core/data/src/test`; `PdfBoxOutlineRepositoryTest`, `PdfBoxTextProviderTest`, `HybridTextProviderTest` → `core/data/src/androidTest`
- Test: `core/datastore/src/androidTest/java/com/mustafakoceerr/tilepdf/di/DataStoreModuleTest.kt`

**Interfaces:**
- `core:database` exposes `TilePdfDatabase`, `DocumentDao`, `DocumentEntity` (depends on `core:model`).
- `core:datastore` exposes `DataStore<Preferences>` binding (`api(libs.androidx.datastore.preferences)`).
- `core:data` depends on `core:pdf`, `core:database`, `core:datastore`, `core:model`, `core:common`, `pdfbox-android`; exposes all repository interfaces, `PdfTextProvider.Factory`, `TextSearcher`, `TextSelection`.

- [ ] **Step 1: Write `DataStoreModuleTest`** (Review Focus 1 — settings survive the update):

```kotlin
@Test
fun storesPreferencesInTheExistingFile() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = DataStoreModule.providesPreferencesDataStore(context)
    runBlocking { store.edit { it[booleanPreferencesKey("smoke")] = true } }
    assertTrue(context.preferencesDataStoreFile("reader_preferences").exists())
}
```
Clean up the key in `@After`.
- [ ] **Step 2: Move the files and wire modules and `app`; remove `tilepdf.android.room` and the `room {}` block from `app`.**
- [ ] **Step 3: Check schemas moved unchanged:** `git diff --cached -M --stat -- app/schemas core/database/schemas` — Expected: two `=>` renames, `0` insertions/deletions.
- [ ] **Step 4: Green build.** Expected: `TilePdfDatabaseMigrationTest` and `DataStoreModuleTest` PASS on device.
- [ ] **Step 5: Commit** — `"Extract core:database, core:datastore and core:data"`

### Task 5: `core:domain` and `core:designsystem`

**Files:**
- Move: `domain/*` → `core:domain`; `OutlineLocatorTest` → `core/domain/src/test`
- Move: `ui/theme/*` → `core:designsystem`; `res/drawable/ic_{add,arrow_back,close,dark_mode,delete,keyboard_arrow_down,keyboard_arrow_up,more_vert,search,tag,toc}.xml` → `core/designsystem/src/main/res/drawable/`
- Create: `core/designsystem/src/main/java/com/mustafakoceerr/tilepdf/core/designsystem/TilePdfIcons.kt`
- Modify: every `R.drawable.ic_*` use in `feature/**` → `TilePdfIcons.*`

**Interfaces:**
- `core:domain` depends on `core:data`, `core:pdf`, `core:model`; exposes `OpenDocumentUseCase`, `OpenedDocument`, `OutlineLocator`.
- `TilePdfIcons`: `object TilePdfIcons` with one `@DrawableRes val` per icon, named in PascalCase from the file name: `Add, ArrowBack, Close, DarkMode, Delete, KeyboardArrowDown, KeyboardArrowUp, MoreVert, Search, Tag, Toc`.
- `TilePDFTheme` keeps its package `com.mustafakoceerr.tilepdf.ui.theme`.

- [ ] **Step 1: Move files, create `TilePdfIcons`, replace drawable references.**
- [ ] **Step 2: Green build.**
- [ ] **Step 3: Commit** — `"Extract core:domain and core:designsystem"`

### Task 6: `feature:library` and `feature:reader`; slim `app`

**Files:**
- Move: `feature/library/**` → `feature/library/src/main/java/...`; `feature/reader/**` → `feature/reader/src/main/java/...`
- Move tests: `PageNavigationTest`, `VisiblePagesTest`, `ZoomStateTest`, `testing/FakeLazyList.kt` → `feature/reader/src/test/java/...`
- Split `app/src/main/res/values/strings.xml`: `library_*` → `feature/library/src/main/res/values/strings.xml`; `reader_*`, `cancel`, `navigate_back` → `feature/reader/...`; `app_name` stays in `app`
- Modify: `ReaderScreen.kt` uses `R.string.app_name` → add `reader_untitled` = `TilePDF` to reader strings and use it (same text on screen)
- Modify: `import com.mustafakoceerr.tilepdf.R` → the feature's own `R` (`com.mustafakoceerr.tilepdf.feature.library.R` / `...feature.reader.R`)
- Modify: `app/build.gradle.kts` — depend on both features, `core:designsystem`, `core:data`; drop dependencies only the moved code used (Room, DataStore, PdfBox, navigation-compose stays for `TilePdfNavHost`)
- Modify: `MainActivitySmokeTest` `R` import (see Task 1)

**Interfaces:**
- `feature:library` exposes `LibraryRoute`, `NavGraphBuilder.libraryScreen(onOpenDocument: (uri: String) -> Unit)`.
- `feature:reader` exposes `ReaderRoute`, `NavController.navigateToReader(uri: String)`, `NavGraphBuilder.readerScreen(onBack: () -> Unit)`.
- `feature:reader` additionally depends on `core:render`.

- [ ] **Step 1: Move code, tests and strings; fix `R` imports.**
- [ ] **Step 2: Confirm `app` is slim:** `git ls-files app/src/main/java` — Expected exactly `MainActivity.kt`, `TilePdfApp.kt`, `navigation/TilePdfNavHost.kt`.
- [ ] **Step 3: Confirm no stray resources:** `./gradlew lint` — Expected: no `UnusedResources`/`MissingTranslation` errors introduced by the split (compare against `master` if it already had warnings).
- [ ] **Step 4: Green build**, including `MainActivitySmokeTest`.
- [ ] **Step 5: Manual check on device:** install, open a PDF from the library, zoom, search, toggle night mode, go back; the previously opened document is still listed with its page.
- [ ] **Step 6: Commit** — `"Extract feature:library and feature:reader"`

### Task 7: Docs and spec amendments

**Files:**
- Modify: `README.md` — Architecture section: module list and dependency direction (replace the package-based description)
- Modify: `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` — apply "Spec amendments" below

- [ ] **Step 1: Update both documents.**
- [ ] **Step 2: Run** `grep -rn "app/src/androidTest/assets\|core:pdfium" README.md docs tools` — Expected: no matches.
- [ ] **Step 3: Commit** — `"Document the module structure"`

## Spec amendments (applied in Task 7)

- `core:pdfium` → **`core:pdf`**: the engine module. It wraps `PdfRenderer` in phase 1; phase 2 puts the PDFium JNI bridge and `PdfEngine` in this same module, so features and data never see a rename.
- **`core:common`** added (dispatcher qualifiers, `ApplicationScope`, `runCatchingCancellable`), as in NiA.
- `core/text` lives in **`core:data`**, not `core:domain` (reason in Module map).
- `core:ui` and `feature:annotation` are created when they first get code (phases 5–6), not as empty modules now.
