# Phase 4c — Reading: Horizontal Mode, Sepia, Keep Screen On, Full Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Readers pick vertical scrolling or horizontal single pages, a light, night or sepia page, and whether the screen stays on; tapping a page hides all chrome for full-screen reading.

**Architecture:** One `ReaderPreferences` (theme, mode, keep screen on) in DataStore replaces the night-mode boolean; the old key is still read so upgrading users keep night mode. A bottom sheet, "Okuma ayarları", replaces the night-mode menu item. The page theme is a `ColorFilter` applied when tiles draw, so switching it doesn't re-render. Horizontal mode is a `HorizontalPager` of fitted pages, each zoomed with a `graphicsLayer` driven by a pure `PageZoom`; it reuses `PdfPage` (tiles, highlights, selection) and `TileGrid`. Full screen is reader UI state toggled by a tap that hits no link; the top bar overlays the content so hiding it doesn't move the pages.

**Tech Stack:** as before; `androidx.compose.foundation.pager.HorizontalPager`, `WindowInsetsControllerCompat`.

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (v1 kapsamı → Okuma: "Dikey sürekli ve yatay tek sayfa modları", "Gece ve sepya teması, ekranı açık tut, dokununca tam ekran")

## Global Constraints

- Test rules (`~/.claude/CLAUDE.md`, `rules/android.md`): TDD with JVM unit tests only, run filtered: `./gradlew :<module>:testDebugUnitTest --tests "*<Class>Test"`. No instrumented, UI or screenshot tests inside the task loop; the one UI test is written in Task 4 and `connectedDebugAndroidTest` runs only when the user says so. If it fails, report the cause and ask; don't loop on fixes.
- `ReaderViewModel` has no JVM test harness (`SavedStateHandle.toRoute` needs Android). Decisions go into pure functions that are tested; the ViewModel only wires them.
- Copy is Turkish, in `feature/reader/src/main/res/values/strings.xml`.
- Sepia paper is `#F4ECD8`; black text stays black.
- Tap order everywhere: selection active → clear it only; else a link → follow it (an unsafe link does nothing); else → toggle full screen.
- Full screen hides the top bar and the system bars (swipe from the edge shows them transiently). Leaving the reader always restores the system bars and clears keep-screen-on.
- Changing the reading mode keeps the current page.

## Review Focus

1. **Upgrading with night mode on** still opens in night. Task 1 `legacyNightModeReadsAsNight`.
2. **A link tapped in full screen** follows the link and doesn't toggle the chrome; a `javascript:` link does neither. Task 2 `LinkActionTest`.
3. **Zoomed page in horizontal mode**: panning stops at the page edges, and a tap at 2× lands on the right page point (links, long press). Task 3 `PageZoomTest`.
4. **Landscape page on a portrait screen, or rotation**: the page fits whole and the fit is recomputed for the new viewport. Task 3 `PageZoomTest.fit*`.
5. **Leaving the reader in full screen** brings the system bars back, and the screen times out again. Task 4 manual check (window flags can't be unit-tested).

---

### Task 1: Reading settings: page theme and keep screen on

**Files:**
- Create: `core/model/src/main/java/com/mustafakoceerr/tilepdf/model/ReaderPreferences.kt`; `feature/reader/.../components/PageTheme.kt` (filters + paper colours, moved out of `PdfPageList.kt`); `feature/reader/.../components/ReaderSettingsSheet.kt`
- Modify: `core/data/.../data/preferences/{ReaderPreferencesRepository,DataStoreReaderPreferencesRepository}.kt`; `ReaderViewModel.kt`, `ReaderUiState.kt` (`nightMode` → `pageTheme`, + `readingMode`, `keepScreenOn`), `ReaderScreen.kt` (menu item "Okuma ayarları" opens the sheet; keep-screen-on effect), `PdfPageList.kt` (takes `pageTheme`), reader strings
- Test: `core/data/src/test/.../data/preferences/DataStoreReaderPreferencesRepositoryTest.kt`, `feature/reader/src/test/.../components/PageThemeTest.kt`

**Interfaces:**
- `enum class PageTheme { Light, Night, Sepia }`; `enum class ReadingMode { Vertical, Horizontal }`; `data class ReaderPreferences(val pageTheme: PageTheme = PageTheme.Light, val readingMode: ReadingMode = ReadingMode.Vertical, val keepScreenOn: Boolean = false)`.
- `interface ReaderPreferencesRepository { val preferences: Flow<ReaderPreferences>; suspend fun setPageTheme(theme: PageTheme); suspend fun setReadingMode(mode: ReadingMode); suspend fun setKeepScreenOn(enabled: Boolean) }`. `nightMode`/`toggleNightMode` are removed.
- Keys: `page_theme` and `reading_mode` hold the enum `name`, and an unknown value reads as the default. `keep_screen_on` is a boolean. When `page_theme` is absent, the legacy `night_mode == true` reads as `Night`. `setPageTheme` removes `night_mode` in the same `edit`.
- `internal fun PageTheme.colorFilter(): ColorFilter?` (Light → `null`, Night → existing inversion matrix, Sepia → channel multiply by `#F4ECD8`); `internal val PageTheme.paperColor: Color` (white / black / `#F4ECD8`).
- ViewModel: `onPageThemeSelected(PageTheme)`, `onReadingModeSelected(ReadingMode)` (used in Task 3), `onKeepScreenOnChange(Boolean)`; `onToggleNightMode` is removed.
- `@Composable internal fun ReaderSettingsSheet(preferences: ReaderPreferences, onPageThemeSelected: (PageTheme) -> Unit, onKeepScreenOnChange: (Boolean) -> Unit, onDismiss: () -> Unit)`: a `ModalBottomSheet` with a "Tema" row of single-choice segmented buttons ("Açık" / "Gece" / "Sepya") and an "Ekranı açık tut" switch. Task 3 adds the "Sayfa düzeni" row.
- Keep screen on: in `ReaderScreen`, `DisposableEffect(keepScreenOn) { view.keepScreenOn = keepScreenOn; onDispose { view.keepScreenOn = false } }` with `LocalView.current`.
- Strings: `reader_settings` = "Okuma ayarları", `reader_theme` = "Tema", `reader_theme_light` = "Açık", `reader_theme_night` = "Gece", `reader_theme_sepia` = "Sepya", `reader_keep_screen_on` = "Ekranı açık tut". `reader_night_mode` is deleted.

- [ ] **Step 1: Write failing tests.** `DataStoreReaderPreferencesRepositoryTest` uses a real `PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("reader.preferences_pb") })` (JUnit `TemporaryFolder`): `defaultsWhenEmpty`, `setPageThemeIsRead` (Sepia), `setReadingModeIsRead` (Horizontal), `setKeepScreenOnIsRead`, `legacyNightModeReadsAsNight` (write `night_mode = true` raw → `pageTheme == Night`), `pageThemeWinsOverLegacy` (legacy true, then `setPageTheme(Light)` → Light), `unknownThemeReadsAsLight` (raw `page_theme = "Purple"`). `PageThemeTest` applies the 4×5 matrix to RGB in a test helper: `lightHasNoFilter`, `nightTurnsWhiteBlack` (255,255,255 → 0,0,0), `sepiaTurnsWhitePaper` (→ 244,236,216 ±1), `sepiaKeepsBlackBlack`.
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest --tests "*DataStoreReaderPreferencesRepositoryTest"` and `:feature:reader:testDebugUnitTest --tests "*PageThemeTest"`. Expected: FAIL to compile.
- [ ] **Step 3: Implement.** Add the model, repository, theme filter and sheet, then wire the ViewModel and screen. `PdfPage` takes `pageTheme` and uses `colorFilter()`/`paperColor`.
- [ ] **Step 4: Run** the two filtered tests plus `LinkActionTest`. Expected: PASS. Then `./gradlew assembleDebug`.
- [ ] **Step 5: Commit** `"Sepia theme and keep screen on"`.

### Task 2: Full screen on tap

**Files:**
- Modify: `LinkAction.kt` (+ `NoLink`), `ReaderViewModel.kt` (`isFullScreen`, `onTapOutsidePage()`), `ReaderUiState.kt` (`Ready.isFullScreen: Boolean = false`), `ReaderScreen.kt` (top bar overlays content; system bars effect), `PdfPageList.kt` (gap tap → `onTapOutsidePage`; top/bottom content padding for the bars)
- Test: `feature/reader/src/test/.../LinkActionTest.kt`

**Interfaces:**
- `LinkAction` gains `data object NoLink`. `linkAction(null, n)` returns `NoLink`, and unsafe or out-of-range links still return `Ignore`.
- ViewModel: in `onTap`, `NoLink` → `isFullScreen = !isFullScreen`. `onTapOutsidePage()` clears a selection if there is one, otherwise toggles the same way. `onOpenSearch()`, `onOpenToc()` and opening the settings sheet or page grid set `isFullScreen = false`.
- Screen layout: content fills the window behind the bars. The top bar (or search bar) sits in an `AnimatedVisibility(!isFullScreen, fadeIn + slideInVertically, fadeOut + slideOutVertically)` over it. The vertical list's `contentPadding` gets `top = statusBar + 64.dp + PAGE_GAP` and `bottom = navigationBar + PAGE_GAP` once, so the layout doesn't change when the chrome hides (`imePadding` stays). Horizontal padding stays as it is.
- System bars: `DisposableEffect(isFullScreen)` on `LocalActivity.current?.window` via `WindowCompat.getInsetsController(window, view)`. Full screen calls `hide(systemBars())` with `systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`; otherwise `show(systemBars())`. `onDispose` always shows them.

- [ ] **Step 1: Update `LinkActionTest`.** `nullIsNoLink` replaces the `null → Ignore` case, and `javaScriptStaysIgnore` asserts `Ignore`, not `NoLink`.
- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest --tests "*LinkActionTest"`. Expected: FAIL.
- [ ] **Step 3: Implement** the ViewModel, screen and list changes above.
- [ ] **Step 4: Run** `LinkActionTest`. Expected: PASS. Then `assembleDebug`.
- [ ] **Step 5: Commit** `"Full screen on tap"`.

### Task 3: Horizontal single-page mode

**Files:**
- Create: `feature/reader/.../components/PageZoom.kt`, `feature/reader/.../components/PdfPagePager.kt`, `feature/reader/.../components/PdfPage.kt` (move `PdfPage`, `drawTile`, `drawHighlight`, `SelectionToolbarEffect` and highlight colours out of `PdfPageList.kt` unchanged, made `internal`; `PdfPage` gains `modifier`)
- Modify: `PageNavigation.kt` (`PageIndicator` → `internal`), `ReaderSettingsSheet.kt` ("Sayfa düzeni" row: "Dikey" / "Yatay"), `ReaderScreen.kt` (`key(readingMode)` chooses `PdfPageList` or `PdfPagePager`), `ReaderViewModel.kt` (start position on mode change), reader strings (`reader_layout` = "Sayfa düzeni", `reader_layout_vertical` = "Dikey", `reader_layout_horizontal` = "Yatay")
- Test: `feature/reader/src/test/.../components/PageZoomTest.kt`

**Interfaces:**
- `internal fun fitPage(pageSize: PageSize, viewport: IntSize): PixelRect` returns the largest rect with the page's aspect ratio inside `viewport`, centred, in whole pixels.
- `class PageZoom(val fitted: PixelRect, val viewport: IntSize)`. All positions are viewport pixels. The page's screen bounds are `fitted.topLeft + offset` with size `fitted.size * scale` (`graphicsLayer` with `TransformOrigin(0f, 0f)`, `translationX/Y = offset`, `scaleX/Y = scale` on the fitted page box).
  - `var scale: Float` (1..5) and `var offset: Offset` are Compose state with private setters.
  - `fun transform(centroid: Offset, pan: Offset, zoom: Float)`: keeps the content under the centroid fixed, then follows `pan`, then clamps.
  - `fun doubleTap(focus: Offset)`: back to 1× if zoomed, else 2.5× around `focus`.
  - `fun panBy(delta: Offset): Offset` returns the delta consumed.
  - `fun pageBounds(): PixelRect`: width and height are `(fitted.width * scale).roundToInt()`, like `TileGrid.forZoom`.
  - `fun toPagePixels(position: Offset): Offset?` returns the point in fitted-page pixels, or `null` off the page.
  - Clamp: on each axis where the zoomed page is no bigger than the viewport it is centred; where it is bigger, its edges can't come inside the viewport.
- Pager: `HorizontalPager(rememberPagerState(initialPage) { pageCount }, key = { it }, beyondViewportPageCount = 0, userScrollEnabled = zoom.scale == 1f)`. One `PageZoom` per page, `remember(pageIndex, viewport)`, so it resets when the page or the screen size changes.
  - Gestures on each page: pinch on `PointerEventPass.Initial`, as in `zoomable`. `detectTapGestures`: tap → `toPagePixels` → `CoordinateMapper.toPdfPoints(…, fitted.width)` → `onTapPage`, or off the page `onTapOutsidePage`; long press → selection; double tap → `doubleTap`. A drag while zoomed → `panBy`.
  - Tiles: `snapshotFlow` of the current page's `Viewport(viewport).visiblePage(page, zoom.pageBounds())` → `TileGrid.forZoom(page, size, fitted.width, zoom.scale).tilesFor(it)` → debounce 80 ms → `pageImages.requestTiles`.
  - Jumps, go-to-page and the current search match call `scrollToPage(index)`; `topPt` is ignored. `onPositionChanged(ReadingPosition(pagerState.currentPage))`. `PageIndicator` shows for 1.5 s after a page change; tapping it opens `GoToPageDialog`.
- Mode change: `onReadingModeSelected` first sets `startPosition` to the latest position, then saves the mode. `Ready.initialPosition` is `startPosition ?: opened.position`, and `ReaderScreen` keys the page view on `readingMode` so it starts there.

- [ ] **Step 1: Write `PageZoomTest`.** `fitPortraitOnPortraitFillsWidth` (A4 595×842 in 1080×2000 → width 1080, centred vertically), `fitLandscapeOnPortraitFillsWidth`, `fitPortraitOnLandscapeFillsHeight` (in 2000×1080 → height 1080, centred horizontally), `pinchKeepsCentroidFixed`, `scaleIsClamped` (×20 → 5, ×0.01 → 1), `unzoomedPageIsCentred` (offset after `transform(…, pan = (500, 0), zoom = 1)` stays 0), `panStopsAtEdges` (2× then `panBy(10000, 0)` → left edge at 0, consumed < 10000), `doubleTapTogglesZoom`, `tapAt2xMapsToPagePoint` (2× at the fitted centre: viewport centre → fitted-page centre), `tapOffPageIsNull`, `pageBoundsMatchesTileWidth`.
- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest --tests "*PageZoomTest"`. Expected: FAIL to compile.
- [ ] **Step 3: Implement** `PageZoom` and `fitPage` until the tests pass, then move `PdfPage`, build the pager, add the sheet row and wire the screen and ViewModel.
- [ ] **Step 4: Run** `PageZoomTest`, `ZoomStateTest`, `VisiblePagesTest`, `LinkActionTest`, `PageThemeTest`. Expected: PASS. Then `assembleDebug`.
- [ ] **Step 5: Commit** `"Horizontal page mode"`.

### Task 4: UI test, docs, full verification

**Files:**
- Create: `feature/reader/src/androidTest/.../components/ReaderSettingsSheetTest.kt`
- Modify: `README.md` (Features: horizontal mode, sepia, keep screen on, full screen)

- [ ] **Step 1: Write `ReaderSettingsSheetTest`** (written, not run yet): `selectingSepiaReportsIt`, `selectedThemeIsMarked` (semantics `selected`), `keepScreenOnSwitchReportsChange`, `selectingHorizontalReportsIt`.
- [ ] **Step 2: Full JVM run.** `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Manual check on emulator-5554** (the user started it for this): open `outline.pdf`. Sepia, then night, then light: the pages recolour without reloading. Turn on keep screen on, then confirm `adb shell dumpsys power | grep -i "SCREEN_BRIGHT\|FULL_WAKE"` shows the lock while reading and drops it after going back. Tap a page: bars hide and the page doesn't move; tap again: they return. Tap a link in full screen: it jumps. Go back while in full screen: the library has its system bars. Switch to horizontal on page 5: page 5 shows fitted; swipe to 6; 2× zoom pans to the edges and doesn't page; double tap returns to 1×. Search "Chapter" in horizontal: it goes to the match page with the highlight. Rotate: the page refits. Switch back to vertical: it is on the same page.
- [ ] **Step 4: Ask the user** before running `connectedDebugAndroidTest`. Run it only if they say yes. On failure, report the cause and ask.
- [ ] **Step 5: Commit** `"Document reading modes and themes"`, push, open PR #6 stacked on #5.
