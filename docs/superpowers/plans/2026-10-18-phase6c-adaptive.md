# Phase 6c — Adaptive Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On tablets and unfolded foldables, contents, bookmarks and annotations open in a side panel next to the pages. That panel stays open while the user reads and jumps around. On wide screens the library flows into columns.

**Architecture:**
- A pure function decides where the contents go (`TocPlacement.Sheet` or `TocPlacement.Side`), based on the size the reader is given. It does not use the device type, so split screen and freeform windows behave correctly.
- The tabs and lists of today's bottom sheet move into a shared `TocContent`, which both the sheet and the new `TableOfContentsPanel` use.
- In side mode the reader becomes a row: the panel at the start, then the page pane. The page pane keeps everything it has today: the top bar over the pages, the bottom toolbars and the dialogs.
- The library list becomes an adaptive grid.

**Tech Stack:** as before; no new dependencies. UI tests force a window size with `DeviceConfigurationOverride.ForcedSize` from `androidx.compose.ui.test`.

**Spec:**
- `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (Kalite: Tablet ve katlanabilir (TOC yan panelde))
- `docs/superpowers/specs/2026-10-17-phase6-quality-design.md` (6c: tablet ve katlanabilirde içindekiler/yer imleri/açıklamalar yan panelde, geniş kütüphane düzeni)

## Global Constraints

- **Side placement:** the side panel is used when the available width is ≥ 840 dp and the available height is ≥ 480 dp. That is the Material 3 "expanded" width with a height that isn't compact, so a phone in landscape (about 915 × 411 dp) keeps the bottom sheet. Everything else uses the bottom sheet.
- **Panel layout:** the panel is 360 dp wide, at the start edge, and full height. It has its own status-bar inset, a close button (`reader_toc_close`, "Close contents" / "İçindekileri kapat"), and the same three tabs as the sheet.
- **Panel behaviour:**
  - In side mode, choosing an outline entry, bookmark or annotation jumps to it and the panel stays open. In sheet mode the sheet closes, as today.
  - Full-screen reading (tap a page) hides the bars but not an open panel.
  - Back closes an open panel before it leaves the reader.
  - While the panel is open, the highlighted outline entry follows the reading position.
- **When the window changes size:** an open panel stays open, so it turns into the sheet when the window becomes too small, and back into the panel when it grows. The reading position is kept when the page pane changes width.
- **Library:** the list becomes `LazyVerticalGrid(GridCells.Adaptive(360.dp))`. A phone in portrait keeps one column.
- **Out of scope:** hinge-aware layouts (tabletop posture, splitting the panel at the fold) and the language or theme work, which the user's own libraries will cover.
- **Test rules:** JVM unit tests drive TDD (`--tests`). UI tests and the manual check run in Task 3 with the user's approval.

## Review Focus

1. **Phone in landscape** must not get the side panel, which would leave about 550 dp for pages at 411 dp height. Covered by Task 1 `phoneLandscapeKeepsSheet`.
2. **Jumping from the panel** must not close it, and the highlighted entry must follow the page. Covered by Task 1 UI test `selectingKeepsPanelOpen`, with the highlight checked by hand in Task 3.
3. **Opening or closing the panel** shrinks or grows the page pane; the current page must stay on screen. Checked by hand in Task 3.
4. **Annotation toolbar, selection bar and save-error banner** must stay inside the page pane and not slide under the panel. Checked by hand in Task 3.
5. **Library on a tablet** shows several columns, while the phone portrait list is unchanged. Covered by Task 3 UI test `wideLibraryUsesColumns`, with the existing `LibraryScreenTest` still passing.

---

### Task 1: Placement rule and the side panel component

**Files:**
- Create: `feature/reader/.../components/TocPlacement.kt`:
  - `enum class TocPlacement { Sheet, Side }`
  - `internal fun tocPlacement(widthDp: Float, heightDp: Float): TocPlacement`
  - `internal val TocPanelWidth = 360.dp`
- Modify: `feature/reader/.../components/TableOfContentsSheet.kt`:
  - Extract `@Composable private fun TocContent(...)` with the tabs and the three lists. Its parameters are the sheet's minus `onDismiss`, and its `listModifier` is used to fill the panel's height.
  - `TableOfContentsSheet` keeps its signature and wraps `TocContent` in `ModalBottomSheet`.
  - Add `internal fun TableOfContentsPanel(...)`: the sheet's parameters, with `onDismiss` renamed to `onClose`. It is a `Surface(tonalElevation = 1.dp)` of `TocPanelWidth`, `fillMaxHeight`, with `statusBarsPadding` and `navigationBarsPadding`. A header row puts the tab row next to an `IconButton` that closes the panel (`TilePdfIcons.Close`, `reader_toc_close`).
  - Strings `reader_toc_close` in `values/` and `values-tr/`.
- Test: JVM `feature/reader/src/test/.../components/TocPlacementTest.kt`; UI `feature/reader/src/androidTest/.../components/TableOfContentsPanelTest.kt` (runs in Task 3)

**Interfaces:**
- Produces: `TocPlacement`, `tocPlacement(widthDp, heightDp)`, `TocPanelWidth`, and `TableOfContentsPanel(outline, currentIndex, bookmarks, onSelect, onSelectPage, onClose, annotations, onSelectAnnotation, onDeleteAnnotation)`.

- [ ] **Step 1: Write the failing JVM test.**

```kotlin
@Test fun tabletLandscapeUsesSide() = assertEquals(TocPlacement.Side, tocPlacement(1280f, 800f))
@Test fun unfoldedLandscapeUsesSide() = assertEquals(TocPlacement.Side, tocPlacement(841f, 673f))
@Test fun phoneLandscapeKeepsSheet() = assertEquals(TocPlacement.Sheet, tocPlacement(915f, 411f))
@Test fun tabletPortraitKeepsSheet() = assertEquals(TocPlacement.Sheet, tocPlacement(800f, 1280f))
@Test fun boundaries() {
    assertEquals(TocPlacement.Side, tocPlacement(840f, 480f))
    assertEquals(TocPlacement.Sheet, tocPlacement(839.9f, 480f))
    assertEquals(TocPlacement.Sheet, tocPlacement(840f, 479.9f))
}
```

- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest --tests "*TocPlacementTest"`. Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement** the placement rule, `TocContent`, `TableOfContentsPanel` and the strings.
- [ ] **Step 4: Run** the same command, then `./gradlew assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5: Write the UI test** `TableOfContentsPanelTest` (runs in Task 3):
  - `selectingKeepsPanelOpen`: click an outline entry. `onSelect` gets it, `onClose` is not called, and the tab row is still displayed.
  - `closeButtonCloses`: clicking `reader_toc_close` calls `onClose`.

  Compile it with `./gradlew :feature:reader:assembleDebugAndroidTest`.
- [ ] **Step 6: Commit** `"Side panel for contents, bookmarks and annotations"`.

### Task 2: The reader uses the side panel

**Files:**
- Modify: `feature/reader/.../ReaderScreen.kt`:
  - Wrap the body in `BoxWithConstraints`; `placement = tocPlacement(maxWidth.value, maxHeight.value)`.
  - **Side mode:** `Row { if (toc open) TableOfContentsPanel(...); Box(Modifier.weight(1f)) { today's page pane: pages, bottom bars, top bar } }`.
    - The panel's selection callbacks jump without calling `onCloseToc`.
    - Add `BackHandler(enabled = side && toc open, onBack = onCloseToc)`.
    - The top bar's TOC button toggles: it calls `onCloseToc` when the panel is open.
  - **Sheet mode:** unchanged.
  - Dialogs and sheets other than the TOC stay where they are.
- Modify: `ReaderViewModel.kt`: while `toc.isOpen`, `onPositionChanged` recomputes `toc.currentIndex` with the same `OutlineLocator.currentIndex(...)` that `onOpenToc` uses. Extract `private fun currentOutlineIndex(entries, position): Int` so that both use it.
- Test: none new at JVM level. The placement rule is Task 1's; `OutlineLocator` is already tested. The wiring is checked by hand in Task 3.

**Interfaces:**
- Consumes: Task 1's `tocPlacement` and `TableOfContentsPanel`.

- [ ] **Step 1: Implement** as above.
- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest` and `./gradlew assembleDebug`. Expected: PASS and BUILD SUCCESSFUL (existing reader JVM tests are unchanged).
- [ ] **Step 3: Commit** `"Reader keeps contents beside the pages on large screens"`.

### Task 3: Wide library, then verify and ship

**Files:**
- Modify: `feature/library/.../LibraryScreen.kt`: `DocumentList` becomes `LazyVerticalGrid(GridCells.Adaptive(360.dp))` with the same rows and keys.
- Modify: `README.md`, to describe the large-screen layout.
- Test: UI `feature/library/src/androidTest/.../LibraryScreenTest.kt`:
  - add `wideLibraryUsesColumns`: inside `DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1000.dp, 800.dp)))`, two items share the same top bound;
  - add `phoneLibraryIsOneColumn`: at 400 × 800 dp the second item is below the first.

- [ ] **Step 1: Implement** the grid and write the two UI tests.
- [ ] **Step 2: Run the UI tests** (with approval), one class at a time: `LibraryScreenTest`, `TableOfContentsPanelTest`, `TableOfContentsSheetTest`. Expected: PASS. A failure that isn't about the app goes to the user per the test rules.
- [ ] **Step 3: Manual check on the emulator** (with approval):
  1. **Phone landscape** (rotate): the TOC button opens the bottom sheet.
  2. **Tablet landscape**: run `adb shell wm density 240` in landscape for about 1616 × 720 dp. Then:
     - the TOC button opens the side panel and the pages narrow, with the same page on screen;
     - choosing an entry jumps and the panel stays;
     - scrolling moves the highlight;
     - tapping a page hides the bars but not the panel;
     - Back closes the panel;
     - the annotation toolbar and selection bar sit inside the page pane;
     - the library shows columns.
  3. **Portrait** at the same density (720 dp wide): the sheet is back, and an open panel became the sheet.

  Reset with `adb shell wm density reset` and portrait rotation.
- [ ] **Step 4: Full run.** Run `./gradlew assembleDebug assembleRelease testDebugUnitTest :core:model:test lintDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `"Library flows into columns on wide screens"`. Push, then open the PR stacked on `phase6b-language-a11y`.
