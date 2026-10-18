# Phase 6b — Language, Accessibility, Typed Error Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TilePDF speaks English by default and Turkish on Turkish devices (or by choice inside the app). TalkBack can tell every control apart and every control is large enough to tap. A document that fails to open shows a screen naming the cause and offering the next step.

**Architecture:**
- **Strings:** every string moves to English in `values/`, and the current Turkish copy moves to `values-tr/`. AGP generates the locale config (`en-US`, `tr`).
- **Language choice:** made through AppCompat's per-app language API, which needs `MainActivity` to become an `AppCompatActivity`. On API 29–32 the choice is stored by `AppLocalesMetadataHolderService`; on 33+ the system stores it. The library reaches the API through a small `AppLanguageRepository` interface, so the ViewModel stays JVM-testable.
- **Open errors:** they get two more types (`PermissionLost`, `OutOfMemory`). A pure function maps each type to its copy and actions, and the reader shows the result as a full error screen.
- **Accessibility:** fixes for what the Accessibility Test Framework (ATF) and TalkBack find. These are 48 dp targets, distinct labels for colors and widths, and reader bars that stay up while TalkBack is on. They are pinned by ATF checks in UI tests.

**Tech Stack:** as before; adds `androidx.appcompat:appcompat` 1.7.1 (app) and `androidx.compose.ui:ui-test-junit4-accessibility` (UI tests, from the Compose BOM).

**Spec:**
- `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md`, which covers:
  - Kalite: TR/EN; Erişilebilirlik (TalkBack etiketleri, dokunma hedefleri, kontrast)
  - Tipli hata ekranları: bozuk dosya, yanlış şifre, bellek, izin kaybı
  - Veri akışı: `OpenError.Corrupt | PasswordRequired | WrongPassword | OutOfMemory | PermissionLost`
- `docs/superpowers/specs/2026-10-17-phase6-quality-design.md` (6b: İngilizce varsayılan + Türkçe, uygulama içi dil seçimi)

## Global Constraints

- **Default language:** English in `values/`; Turkish in `values-tr/`. Every translatable string exists in both, and lint `MissingTranslation` / `ExtraTranslation` must stay clean. Strings that are the same in every language are `translatable="false"` and live only in `values/`: `app_name`, `reader_untitled`, `reader_search_counter`, `reader_page_indicator`, `language_english` "English" and `language_turkish` "Türkçe".
- **Language names** are always shown in their own language ("English", "Türkçe"). Only "System default" / "Sistem varsayılanı" is translated.
- **Locale config:** `androidResources { generateLocaleConfig = true; localeFilters += listOf("en", "tr") }` in `app`, plus `app/src/main/res/resources.properties` with `unqualifiedResLocale=en-US`. Library locales (AppCompat, Material) must not appear in the generated config.
- **Touch targets** are at least 48 × 48 dp. Two controls that do different things never share a label.
- **Errors map as follows:**
  - `SecurityException` → `PermissionLost`
  - `FileNotFoundException` → `NotFound`
  - `PdfPasswordException` → password dialog (unchanged)
  - other `IOException` → `Corrupt`
  - `OutOfMemoryError` → `OutOfMemory`
  - anything else is a bug and is rethrown.
- **Test rules:** JVM unit tests drive TDD (`--tests`). UI tests (ATF, error screen) and the manual checks run in Task 4 with the user's approval. ATF checks need API 34+ and are skipped with `assumeTrue` below that.

### English copy for existing keys (Turkish stays as it is today, moved to `values-tr/`)

Library:
`library_title` Library · `library_open_pdf` Open PDF · `library_empty` You haven't opened a PDF yet.\nTap "Open PDF" to start. · `library_page_progress` Page %1$d of %2$d · `library_remove` Remove from library · `library_no_access` No access, open the file again · `library_all` All · `library_favorites` Favorites · `library_favorite_add` Add to favorites · `library_favorite_remove` Remove from favorites · `library_sort` Sort · `library_sort_recent` Last opened · `library_sort_name` Name · `library_sort_added` Date added · `library_search` Search · `library_search_hint` Search library · `library_search_close` Close search · `library_removed` Removed from library · `library_undo` Undo · `library_more` More options · `library_no_favorites` No favorites yet · `library_no_results` No results

Reader:
`navigate_back` Back · `cancel` Cancel · `reader_page_description` Page %1$d · `reader_settings` Reading settings · `reader_layout` Page layout · `reader_layout_vertical` Vertical · `reader_layout_horizontal` Horizontal · `reader_theme` Theme · `reader_theme_light` Light · `reader_theme_night` Night · `reader_theme_sepia` Sepia · `reader_keep_screen_on` Keep screen on · `reader_search` Search · `reader_search_hint` Search document · `reader_search_close` Close search · `reader_search_previous` Previous result · `reader_search_next` Next result · `reader_search_no_results` No results · `reader_fast_scroll` Fast scroll · `reader_go_to_page` Go to page · `reader_go_to_page_range` A page from 1 to %1$d · `reader_go_to_page_confirm` Go · `reader_toc` Contents · `reader_more_options` More options · `reader_no_app_for_link` No app can open this link · `reader_bookmarks` Bookmarks · `reader_bookmark_add` Add bookmark · `reader_bookmark_remove` Remove bookmark · `reader_no_bookmarks` No bookmarks yet · `reader_pages` Pages · `reader_close` Close · `reader_copied` Copied · `reader_password_title` Password required · `reader_password_hint` Password · `reader_password_wrong` Wrong password · `reader_password_open` Open · `reader_annotate` Annotate · `reader_annotate_done` Done

Annotation:
`annotation_tool_select` Select · `annotation_tool_pen` Pen · `annotation_tool_eraser` Eraser · `annotation_tool_note` Note · `annotation_undo` Undo · `annotation_redo` Redo · `annotation_color` Color · `annotation_width` Width · `annotation_highlight` Highlight · `annotation_underline` Underline · `annotation_strikeout` Strikethrough · `annotation_delete` Delete · `annotation_edit` Edit · `annotation_note_title` Note · `annotation_note_hint` Your note · `annotation_save` Save · `reader_annotations` Annotations · `reader_no_annotations` No annotations yet · `annotation_kind_highlight` Highlight · `annotation_kind_underline` Underline · `annotation_kind_strikeout` Strikethrough · `annotation_kind_ink` Drawing · `annotation_kind_note` Note · `annotation_kind_other` Annotation · `annotation_page` Page %1$d

Saving:
`reader_save_copy` Save as copy · `save_error_no_permission` Annotations can't be saved to this file. · `save_error_file_changed` The file was changed in another app; annotations can be saved to a copy. · `save_error_failed` Couldn't save annotations. · `save_retry` Retry · `save_copy_done` Copy saved

`reader_error_not_found` and `reader_error_corrupt` are replaced in Task 3. Until then they read "File not found or no access." and "Couldn't open the PDF; the file may be damaged."

### New copy (EN / TR)

Language:
- `library_language` Language / Dil
- `language_system` System default / Sistem varsayılanı

Errors:
- `error_not_found_title` File not found / Dosya bulunamadı
- `error_not_found_message` It may have been moved, renamed or deleted. / Taşınmış, adı değişmiş veya silinmiş olabilir.
- `error_permission_title` Can't access this file / Bu dosyaya erişilemiyor
- `error_permission_message` TilePDF no longer has permission to open it. Choose the file again to continue. / TilePDF'in bu dosyayı açma izni kalmadı. Devam etmek için dosyayı yeniden seç.
- `error_corrupt_title` Can't open this PDF / Bu PDF açılamıyor
- `error_corrupt_message` The file may be damaged or may not be a PDF. / Dosya bozuk olabilir veya PDF olmayabilir.
- `error_memory_title` Not enough memory / Yeterli bellek yok
- `error_memory_message` Close other apps and try again. / Diğer uygulamaları kapatıp tekrar dene.

Error actions:
- `error_choose_file` Choose file / Dosya seç
- `error_try_again` Try again / Tekrar dene
- `error_back_to_library` Back to library / Kütüphaneye dön

Annotation colors and width:
- `annotation_color_yellow` Yellow / Sarı
- `annotation_color_green` Green / Yeşil
- `annotation_color_blue` Blue / Mavi
- `annotation_color_pink` Pink / Pembe
- `annotation_color_red` Red / Kırmızı
- `annotation_color_black` Black / Siyah
- `annotation_width_value` Width %1$d pt / Kalınlık %1$d pt

## Review Focus

1. **Revoked grant vs. deleted file.** A library entry whose grant is gone must show "Can't access this file" with Choose file, and must not show "Corrupt". Covered by Task 3 `revokedPermissionIsPermissionLost`.
2. **Out of memory while opening** must not crash. It shows the memory screen with Try again. Covered by Task 3 `outOfMemoryIsOutOfMemory` and `memoryErrorRetries`.
3. **Device in Turkish, app set to English** (and the reverse). The choice wins, and it survives a restart on API 29–32 as well as on 33+. Covered by Task 2 `AppLanguageTest` (tag mapping), with persistence checked by hand in Task 4.
4. **Two annotation colors read the same to TalkBack.** Each swatch has its own name. Covered by Task 4 `paletteColorsHaveDistinctNames`.
5. **A missing Turkish string** would quietly show English. Lint `MissingTranslation` fails the build; Task 1 and Task 4 run `lintDebug`.

Out of scope: render-time memory pressure. Tile and draft caches already have budgets (Phase 3) and trim on `onTrimMemory`, so this phase types only the open-time `OutOfMemoryError`.

---

### Task 1: English default, Turkish translation

**Files:**
- Modify: `feature/library/src/main/res/values/strings.xml`, `feature/reader/src/main/res/values/strings.xml` (English, per the copy table above) and `app/build.gradle.kts` (`androidResources` per Global Constraints)
- Create: `feature/library/src/main/res/values-tr/strings.xml` and `feature/reader/src/main/res/values-tr/strings.xml` (today's Turkish, with the `translatable="false"` keys left out), and `app/src/main/res/resources.properties`

**Interfaces:** Produces the `values/` + `values-tr/` layout that every later string follows.

- [ ] **Step 1: Move and translate the strings.** Mark the untranslatable keys as Global Constraints lists.
- [ ] **Step 2: Verify.** Run `./gradlew assembleDebug lintDebug`. Expected: BUILD SUCCESSFUL and no `MissingTranslation`. `app/build/**/res/xml/_generated_res_locale_config.xml` lists exactly `en-US` and `tr`.
- [ ] **Step 3: Commit** `"English by default, Turkish translation"`.

### Task 2: Choosing the language in the app

**Files:**
- Create: `core/model/.../model/AppLanguage.kt`, `core/data/.../preferences/AppLanguageRepository.kt` and `app/.../language/AppCompatLanguageRepository.kt` (+ a Hilt `@Binds` in a new `app/.../di/LanguageModule.kt`)
- Modify:
  - `gradle/libs.versions.toml` (+ `appcompat = "1.7.1"`, `androidx-appcompat`) and `app/build.gradle.kts` (+ appcompat)
  - `MainActivity.kt` (`AppCompatActivity`), `app/src/main/res/values/themes.xml` (parent `Theme.AppCompat.DayNight.NoActionBar`) and `AndroidManifest.xml` (+ `AppLocalesMetadataHolderService` with `autoStoreLocales` `true`, `enabled="false"`, `exported="false"`)
  - `LibraryUiState.kt` (+ `language: AppLanguage = AppLanguage.System` in `Success`), `LibraryViewModel.kt` (+ `AppLanguageRepository`, `onLanguageSelected`) and `LibraryScreen.kt` (top bar ⋮ "Language" → dialog of three radio rows: System default, English, Türkçe), with strings in both languages
- Test: `core/model/src/test/.../AppLanguageTest.kt`; `LibraryViewModelTest.kt` (+ fake repository, `languageIsShownAndSet`)

**Interfaces:**
- Produces:
  - `enum class AppLanguage(val tag: String?) { System(null), English("en"), Turkish("tr"); companion object { fun fromTag(tag: String?): AppLanguage } }`. `fromTag` matches on the primary language subtag; null, blank or unknown gives `System`.
  - `interface AppLanguageRepository { fun current(): AppLanguage; fun set(language: AppLanguage) }`
  - `AppCompatLanguageRepository`: `current()` reads `AppCompatDelegate.getApplicationLocales().toLanguageTags()`; `set` calls `setApplicationLocales` with `LocaleListCompat.forLanguageTags(tag)`, or `getEmptyLocaleList()` for System.
  - `LibraryViewModel.onLanguageSelected(language: AppLanguage)`

- [ ] **Step 1: Write the failing tests.**

```kotlin
// AppLanguageTest
@Test fun regionIsIgnored() { assertEquals(AppLanguage.Turkish, AppLanguage.fromTag("tr-TR")); assertEquals(AppLanguage.English, AppLanguage.fromTag("en-GB")) }
@Test fun emptyOrUnknownIsSystem() { assertEquals(AppLanguage.System, AppLanguage.fromTag(null)); assertEquals(AppLanguage.System, AppLanguage.fromTag("")); assertEquals(AppLanguage.System, AppLanguage.fromTag("de")) }
@Test fun tagsRoundTrip() { AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTag(it.tag)) } }

// LibraryViewModelTest
@Test fun languageIsShownAndSet() // repo starts at Turkish → success().language == Turkish; onLanguageSelected(English) → repo.set called with English and the state shows English
```

- [ ] **Step 2: Run** `./gradlew :core:model:testDebugUnitTest --tests "*AppLanguageTest"` and `./gradlew :feature:library:testDebugUnitTest --tests "*LibraryViewModelTest"`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement.** The ViewModel keeps the language in a `MutableStateFlow` seeded from `current()`, since AppCompat has no flow. The activity recreates on `set`, so the new screen reads it again anyway.
- [ ] **Step 4: Run** both commands, then `./gradlew assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `"Choose the app language"`.

### Task 3: Typed open errors and the error screen

**Files:**
- Modify:
  - `feature/reader/.../ReaderUiState.kt` (`enum class ReaderError { NotFound, PermissionLost, Corrupt, OutOfMemory }`) and `ReaderErrors.kt` (mapping per Global Constraints)
  - `ReaderViewModel.kt`: the open path catches `Throwable` and rethrows `CancellationException` and unmapped errors; adds `fun onRetryOpen()`, which opens again with no password
  - `ReaderScreen.kt`: `ReaderUiState.Error` shows `ReaderErrorContent`. "Choose file" launches `OpenDocument(application/pdf)` and passes the result to the existing `onOpenDocument(uri)`, which replaces the reader route; the grant is taken when the document is prepared. "Back to library" calls `onBack`.
  - Strings in both languages; remove `reader_error_not_found` / `reader_error_corrupt`
- Create: `feature/reader/.../components/ReaderErrorContent.kt` (icon `TilePdfIcons.ErrorOutline` with a new `ic_error_outline`, title, message, a filled primary button and a text secondary button, centered and scrollable)
- Test: `ReaderErrorsTest.kt`, plus the new JVM `ErrorCopyTest.kt`; UI test `feature/reader/src/androidTest/.../components/ReaderErrorContentTest.kt` (runs in Task 4)

**Interfaces:**
- Consumes: `openFailureFor(error: Throwable): OpenFailure?` (existing).
- Produces:
  - `enum class ErrorAction { ChooseFile, Retry, Back }`
  - `internal data class ErrorCopy(@StringRes val title: Int, @StringRes val message: Int, val primary: ErrorAction, val secondary: ErrorAction?)`
  - `internal fun ReaderError.errorCopy(): ErrorCopy`
  - `ReaderViewModel.onRetryOpen()`

| Error | Primary | Secondary |
|---|---|---|
| NotFound | ChooseFile | Back |
| PermissionLost | ChooseFile | Back |
| Corrupt | Back | — |
| OutOfMemory | Retry | Back |

- [ ] **Step 1: Write the failing tests.**

```kotlin
// ReaderErrorsTest: revokedPermissionIsNotFound becomes
@Test fun revokedPermissionIsPermissionLost() { assertEquals(OpenFailure.Failed(ReaderError.PermissionLost), openFailureFor(SecurityException("grant revoked"))) }
@Test fun outOfMemoryIsOutOfMemory() { assertEquals(OpenFailure.Failed(ReaderError.OutOfMemory), openFailureFor(OutOfMemoryError())) }
@Test fun otherErrorsAreNotMapped() { assertNull(openFailureFor(StackOverflowError())) }

// ErrorCopyTest (JVM)
@Test fun lostFilesOfferChoosingAgain() { listOf(ReaderError.NotFound, ReaderError.PermissionLost).forEach { assertEquals(ErrorAction.ChooseFile, it.errorCopy().primary); assertEquals(ErrorAction.Back, it.errorCopy().secondary) } }
@Test fun corruptOnlyGoesBack() { assertEquals(ErrorAction.Back, ReaderError.Corrupt.errorCopy().primary); assertNull(ReaderError.Corrupt.errorCopy().secondary) }
@Test fun memoryErrorRetries() { assertEquals(ErrorAction.Retry, ReaderError.OutOfMemory.errorCopy().primary) }
@Test fun eachErrorHasItsOwnText() { assertEquals(ReaderError.entries.size, ReaderError.entries.map { it.errorCopy().title }.distinct().size) }
```

UI test (written now, run in Task 4):
- `permissionLostOffersChooseFile`: the title and "Choose file" are displayed.
- `retryCallsBack`: clicking "Try again" on OutOfMemory invokes `onRetry`.

- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest --tests "*ReaderErrorsTest" --tests "*ErrorCopyTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command and `./gradlew assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `"Typed error screens for documents that don't open"`.

### Task 4: Accessibility, then verify and ship

**Files:**
- Modify:
  - `feature/reader/.../annotation/AnnotationToolbar.kt`:
    - swatches and width choices are 48 dp;
    - `internal fun colorNameRes(color: Int): Int` maps `AnnotationPalette` in order to the six `annotation_color_*` names, with `annotation_color` for a color outside the palette;
    - widths are labeled with `annotation_width_value`.
  - `ReaderScreen.kt`: bars never hide while touch exploration is on. Add `rememberTouchExplorationEnabled()`, which listens on `AccessibilityManager`; the screen hides the bars only when `isFullScreen && !touchExploration`.
  - Anything the ATF checks below report.
  - `README.md`: languages and accessibility.
  - `feature/library/build.gradle.kts` and `feature/reader/build.gradle.kts`: + `androidTestImplementation("androidx.compose.ui:ui-test-junit4-accessibility")` via the version catalog.
- Test:
  - JVM `feature/reader/src/test/.../annotation/AnnotationColorNamesTest.kt`
  - UI `feature/library/src/androidTest/.../LibraryAccessibilityTest.kt`
  - UI `feature/reader/src/androidTest/.../ReaderAccessibilityTest.kt`

- [ ] **Step 1: Write the failing JVM test.**

```kotlin
@Test fun paletteColorsHaveDistinctNames() {
    val names = AnnotationPalette.map(::colorNameRes)
    assertEquals(AnnotationPalette.size, names.distinct().size)
    assertFalse(R.string.annotation_color in names)
}
```

- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest --tests "*AnnotationColorNamesTest"`. Expected: FAIL. Then implement and run it again. Expected: PASS.
- [ ] **Step 3: Write the ATF UI tests.** Use `runAndroidComposeUiTest<ComponentActivity>`, `assumeTrue(Build.VERSION.SDK_INT >= 34)`, `enableAccessibilityChecks()` and `onRoot().tryPerformAccessibilityChecks()`.
  - Library: a list with a favorite and an inaccessible row; the empty state; the language dialog open.
  - Reader: each `ReaderError` screen; `AnnotationToolbar` in pen mode (swatches and widths).
- [ ] **Step 4: Run the UI tests** (with approval), one class at a time: `LibraryAccessibilityTest`, `ReaderAccessibilityTest`, `ReaderErrorContentTest`, and the existing `LibraryScreenTest`. Expected: PASS. Fix what ATF reports (a target, label or contrast) and re-run that class. A failure that isn't about the app goes to the user per the test rules.
- [ ] **Step 5: Manual check on the emulator** (with approval):
  1. With the device in English, the app shows English. Choose Türkçe and the app turns Turkish; it is still Turkish after force-stop and relaunch. Choose System default and it is English again.
  2. Delete a library document's source file (`adb shell rm`), then open it from the library: the "File not found" screen appears, and Choose file opens the picker.
  3. Set font scale to 2.0 (`adb shell settings put system font_scale 2.0`; reset afterwards). The library rows, the error screen and the language dialog are not clipped.
- [ ] **Step 6: Full run.** Run `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** `"Accessibility: distinct labels, 48 dp targets, bars stay for TalkBack"`. Push, then open the PR stacked on `phase6a-library`.
