# Phase 5c — Annotation UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the reader, users highlight, underline and strike out selected text, draw with a pen, erase strokes, add and edit sticky notes, pick colours and pen width, undo and redo, list every annotation (tap → page, delete), and delete any markup annotation by tapping it. Changes save into the PDF on leaving, and the reader offers "Kopya olarak kaydet" when the file can't be written.

**Architecture:** Annotation UI lives in `feature:reader` under `feature.reader.annotation`. It is part of the reader screen, and feature modules can't depend on each other, so the spec's `feature:annotation` module isn't created (ruling; the design note is updated). Pure functions decide geometry: markup from a selection, hit testing, the eraser, stroke building and theme recolouring, all JVM-tested. An `AnnotationController` (plain class, JVM-tested with fakes) owns the `AnnotationHistory`, writes every change to `AnnotationRepository`, hides and shows deleted foreign annotations in the reader's document, requests saves, and reports when a background save changed the file under the open reader. `ReaderViewModel` wires the controller and the tool state. Pages draw TilePDF's annotations in Compose on top of the tiles. A live pen stroke stays in page-local state until the finger lifts, so drawing never recomposes the screen.

**Tech Stack:** as before (Compose Material 3, `ActivityResultContracts.CreateDocument`).

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (Açıklama) and `docs/superpowers/specs/2026-10-17-phase5-annotations-design.md` (deletion as in other readers, object eraser, Compose draws ours, history cleared after a save, passwords in memory)

## Global Constraints

- Tap order: selection active → clear it. Note tool → place a note. On an annotation → its menu. A link → follow it. Else → toggle full screen. In pen and eraser modes, a long press does nothing.
- Pen and eraser: one finger draws or erases, two fingers pan and zoom as before. The list and the pager don't scroll with one finger in these modes.
- Undo steps: one per markup, stroke, note add/edit/delete, annotation delete, and **eraser gesture** (everything it erased in one step).
- Editing waits for the draft (`AnnotationController.ready`). Until then the annotation button and the markup actions are disabled.
- Colours: palette `0xFFFFEB3B` yellow, `0xFF66BB6A` green, `0xFF42A5F5` blue, `0xFFEC407A` pink, `0xFFE53935` red, `0xFF212121` black. Pen widths: 1, 2, 4 pt. Defaults: markup yellow, pen red, 2 pt. They are kept in `ReaderPreferences`.
- Note icon: 20 pt square (5a), top-left at the tap point.
- Hit tolerance 6 pt, eraser radius 8 pt, both in page points (independent of zoom).
- Our annotations drawn by Compose use the page theme: colours go through `PageTheme.recolor`. Highlights are drawn at 40 % alpha.
- Copy (Turkish, `strings.xml`): `reader_annotate` "Açıklama ekle", `reader_annotate_done` "Bitti", `annotation_tool_select` "Seç", `annotation_tool_pen` "Kalem", `annotation_tool_eraser` "Silgi", `annotation_tool_note` "Not", `annotation_undo` "Geri al", `annotation_redo` "Yinele", `annotation_color` "Renk", `annotation_width` "Kalınlık", `annotation_highlight` "Vurgula", `annotation_underline` "Altını çiz", `annotation_strikeout` "Üstünü çiz", `annotation_delete` "Sil", `annotation_edit` "Düzenle", `annotation_note_title` "Not", `annotation_note_hint` "Notunuz", `annotation_save` "Kaydet", `reader_annotations` "Açıklamalar", `reader_no_annotations` "Henüz açıklama yok", `annotation_kind_highlight` "Vurgu", `annotation_kind_underline` "Altı çizili", `annotation_kind_strikeout` "Üstü çizili", `annotation_kind_ink` "Çizim", `annotation_kind_note` "Not", `annotation_kind_other` "Açıklama", `reader_save_copy` "Kopya olarak kaydet", `save_error_no_permission` "Açıklamalar bu dosyaya kaydedilemiyor.", `save_error_file_changed` "Dosya başka bir uygulamada değişti; açıklamalar kopyaya kaydedilebilir.", `save_error_failed` "Açıklamalar kaydedilemedi.", `save_retry` "Tekrar dene", `save_copy_done` "Kopya kaydedildi".
- Test rules: JVM unit tests drive TDD (`--tests`). The UI and render instrumented tests are written in their task. They run only with the user's approval for this plan, otherwise once at the end with their OK. Manual checks run on emulator-5554 at the end. Failures: report the cause and ask.

## Review Focus

1. **A background save while the document is open** (app sent to background, then back). The reader reloads on the new file version: annotations still show once, and a foreign deletion made after reload hits the right annotation. Task 2 `fileSavedIsReported`, and the Task 9 manual check.
2. **An eraser gesture across several strokes, then undo**: everything comes back in one step. Task 2 `eraseIsOneUndoStep`.
3. **Undo of a foreign deletion**: the annotation is drawn again by PDFium (shown and tiles invalidated). Task 2 `undoShowsForeignAgain`, Task 4 `invalidateRerendersPage`.
4. **Pen stroke at 2× zoom and in horizontal mode** lands under the finger. Task 1 (`StrokeBuilder` is page-point based) plus the Task 9 manual check at 2×.
5. **Read-only file**: editing still works, and on return the banner offers "Kopya olarak kaydet"; the copy opens with the annotations. Task 2 `saveErrorIsExposed`, and the Task 9 manual check with a file from `/sdcard/Download` opened through "Open with" (read-only grant).

---

### Task 1: Annotation geometry

**Files:**
- Create: `feature/reader/src/main/java/com/mustafakoceerr/tilepdf/feature/reader/annotation/AnnotationGeometry.kt`, `StrokeBuilder.kt`, `AnnotationListItems.kt`
- Modify: `feature/reader/.../components/PageTheme.kt` (+ `recolor`)
- Test: `feature/reader/src/test/.../annotation/AnnotationGeometryTest.kt`, `StrokeBuilderTest.kt`, `AnnotationListItemsTest.kt`; `components/PageThemeTest.kt` (+ recolor cases)

**Interfaces:**
- `fun markupFor(selection: SelectionUiState, kind: MarkupKind, color: Int, id: String = newAnnotationId()): TextMarkup` (page, rects = `selection.rects`, text = `selection.text`).
- `sealed interface AnnotationTarget { data class Own(val annotation: Annotation); data class Foreign(val annotation: ForeignAnnotation) }`
- `fun annotationAt(own: List<Annotation>, foreign: List<ForeignAnnotation>, pageIndex: Int, point: PdfPoint, tolerance: Float = 6f): AnnotationTarget?` checks only `pageIndex`, latest own first, then foreign in reverse.
  - Markup: inside any rect inflated by `tolerance`.
  - Ink: distance to a segment ≤ `widthPt / 2 + tolerance` (a single-point stroke is a dot).
  - Note: inside its 20 pt square inflated.
  - Foreign ink: distance to its strokes ≤ `1 + tolerance`; other foreign: bounds inflated.
- `data class Erased(val ids: Set<String>, val refs: Set<ForeignRef>)`; `fun erasedBy(own: List<Annotation>, foreign: List<ForeignAnnotation>, pageIndex: Int, from: PdfPoint, to: PdfPoint, radius: Float = 8f): Erased`. Ink only, own and foreign: any stroke segment within `radius (+ widthPt/2 for own)` of segment from→to.
- `class StrokeBuilder(private val minDistance: Float = 0.75f) { fun add(point: PdfPoint): Boolean; val points: List<PdfPoint> }`. The first point is always kept; later ones only once `minDistance` from the last kept one.
- `fun PageTheme.recolor(color: Int): Int` applies `colorMatrix()` to the RGB of an ARGB colour (alpha kept; Light = identity).
- `enum class AnnotationKind { Highlight, Underline, StrikeOut, Ink, Note, Other }`; `data class AnnotationListItem(val pageIndex: Int, val kind: AnnotationKind, val text: String, val target: AnnotationTarget)`; `fun annotationListItems(own: List<Annotation>, foreign: List<ForeignAnnotation>): List<AnnotationListItem>`, ordered by page, then by top. Foreign subtypes 9/10/12/15/1 map to the matching kind, everything else to `Other`. Text: markup → its text, note → its text, foreign → contents.

- [ ] **Step 1: Tests.**
  - `AnnotationGeometryTest`: `markupTakesSelectionRectsAndText`, `markupHitInsideRect`, `markupMissFarAway`, `inkHitNearSegment` (8 pt off a 2 pt line misses, 5 pt hits), `dotStrokeHits`, `noteHitInsideIcon`, `latestOwnWins` (two overlapping highlights → the later), `ownBeforeForeign`, `otherPageIgnored`, `foreignInkUsesStrokes`, `eraserHitsCrossedInk`, `eraserIgnoresMarkupAndNotes`, `eraserHitsForeignInk`.
  - `StrokeBuilderTest`: `firstPointKept`, `closePointsSkipped`, `farPointKept`.
  - `AnnotationListItemsTest`: `orderedByPageThenTop`, `foreignKinds`, `texts`.
  - `PageThemeTest`: `lightRecolorIsIdentity`, `nightRecolorInvertsAndKeepsAlpha`.
- [ ] **Step 2: Run** `./gradlew :feature:reader:testDebugUnitTest --tests "*AnnotationGeometryTest" --tests "*StrokeBuilderTest" --tests "*AnnotationListItemsTest" --tests "*PageThemeTest"`. Expected: FAIL to compile.
- [ ] **Step 3: Implement.** **Step 4: Run.** Expected: PASS. **Step 5: Commit** `"Annotation geometry"`.

### Task 2: Annotation controller

**Files:**
- Create: `feature/reader/.../annotation/AnnotationController.kt`; Test: `feature/reader/src/test/.../annotation/AnnotationControllerTest.kt` (+ `FakeAnnotationRepository`, a recording scheduler, a fake `PdfDocument` with per-page foreign annotations)
- Modify: `core/domain/.../AnnotationHistory.kt` (+ `removeAll(ids: Set<String>, refs: Set<ForeignRef>)`, one step) and `AnnotationHistoryTest` (`removeAllIsOneStep`, `removeAllOfNothingRecordsNothing`)

**Interfaces:**
- `data class AnnotationsUiState(val ready: Boolean = false, val own: List<Annotation> = emptyList(), val foreign: List<ForeignAnnotation> = emptyList() /* deleted ones excluded */, val canUndo: Boolean = false, val canRedo: Boolean = false, val saveError: SaveError? = null)`
- `class AnnotationController(uri: String, repository: AnnotationRepository, scheduler: AnnotationSaveScheduler, scope: CoroutineScope, onForeignHidden: suspend (ForeignRef, hidden: Boolean) -> Unit)`:
  - `val state: StateFlow<AnnotationsUiState>`; `val fileSaved: Flow<Unit>`. It emits when the observed draft is clean and its base differs from the base seen at `prepare`, i.e. a save wrote a new version.
  - `suspend fun prepare(document: PdfDocument)`: `repository.prepare`, then a history built from the draft, then hide every `removedForeign` ref, then read every page's foreign annotations (on the PDF dispatcher, one page per call), then `ready = true`. Draft changes (`saveError`, base) are observed from then on.
  - `fun add(annotation)`, `fun remove(id)`, `fun removeForeign(ref)` (hides it), `fun replace(old: String, new: Annotation)`, `fun removeAll(ids, refs)` (eraser), `fun undo()`, `fun redo()`. Undoing or redoing a foreign deletion shows or hides it. Each change calls `repository.update` with the new state.
  - `fun requestSave()` calls `scheduler.requestSave(uri)` when the draft is dirty.

- [ ] **Step 1: Tests** (`runTest`, `backgroundScope`): `prepareMakesReadyWithDraftAndForeign`, `addUpdatesRepository`, `undoRedoRoundTrip`, `undoShowsForeignAgain` (records `onForeignHidden` calls: hide on delete, show on undo), `eraseIsOneUndoStep`, `replaceEditsNoteInOneStep`, `deletedForeignNotListed`, `previouslyRemovedForeignHiddenOnPrepare`, `requestSaveOnlyWhenDirty`, `saveErrorIsExposed`, `fileSavedIsReported` (repository flips the draft to clean with a new base → one emission).
- [ ] **Step 2: Run** `--tests "*AnnotationControllerTest"` and `:core:domain:testDebugUnitTest --tests "*AnnotationHistoryTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** **Step 4: Run.** Expected: PASS. **Step 5: Commit** `"Annotation controller"`.

### Task 3: Annotation colours and pen width

**Files:**
- Modify: `core/model/.../ReaderPreferences.kt` (+ `markupColor: Int = 0xFFFFEB3B`, `penColor: Int = 0xFFE53935`, `penWidthPt: Float = 2f`), `ReaderPreferencesRepository` (+ `setMarkupColor`, `setPenColor`, `setPenWidth`), `DataStoreReaderPreferencesRepository` (keys `markup_color`, `pen_color` int; `pen_width` float), `DataStoreReaderPreferencesRepositoryTest` (+ `annotationDefaults`, `annotationSettingsAreRead`)

- [ ] **Step 1: Tests.** **Step 2: Run** (FAIL). **Step 3: Implement.** **Step 4: Run** (PASS). **Step 5: Commit** `"Remember annotation colours"`.

### Task 4: Draw annotations on pages; redraw a page after a foreign deletion

**Files:**
- Create: `feature/reader/.../annotation/AnnotationDrawing.kt` (`DrawScope.drawAnnotation(annotation, pageSize, theme)`, `DrawScope.drawStroke(points, color, widthPt, pageSize)`)
- Modify: `core/render/.../PageImageSource.kt` (+ `fun invalidate(pageIndex: Int)`, `fun version(pageIndex: Int): Int` as snapshot state), `TiledPageImageSource.kt` (drops the page's draft and cached tiles, re-renders its live tiles and keeps the old ones until replaced, bumps the version), `ThumbnailSource.kt` (+ `invalidate(pageIndex)`), `components/PdfPage.kt` (params `annotations: List<Annotation>`; the draft reloads when `version` changes; draws annotations after tiles and highlights), `PdfPageList.kt`, `PdfPagePager.kt`, `ReaderScreen.kt` (passes each page's own annotations)
- Test: `core/render/src/androidTest/.../TiledPageImageSourceTest.kt` (+ `invalidateRerendersPage`: renderer called again for that page's tiles; `version` incremented; another page untouched)

**Interfaces:**
- Markup: highlight = filled rect per line, `recolor(color)` at alpha 0.4. Underline = 1.5 pt line at the rect bottom minus 10 % of its height. Strikeout = 1.5 pt line through the vertical middle.
- Ink: round-capped, round-joined path, width `widthPt` scaled.
- Note: 20 pt rounded square filled with the colour, 1 pt darker border, three short lines.
- Scale: `pageSize.scaleForWidth(size.width)`, same as `drawHighlight`.

- [ ] **Step 1: Write `invalidateRerendersPage`** (instrumented).
- [ ] **Step 2: Implement.** `assembleDebug`.
- [ ] **Step 3: Run** `TiledPageImageSourceTest` (with approval). Expected: PASS.
- [ ] **Step 4: Commit** `"Draw annotations on pages"`.

### Task 5: Reader wiring: controller, markup from selection, tap menu, notes

**Files:**
- Modify: `ReaderViewModel.kt` (injects `AnnotationRepository`, `AnnotationSaveScheduler`, `SessionPasswords`; creates the controller per opened document; `onForeignHidden` = `document.setForeignHidden` + `pageImages.invalidate` + `thumbnails.invalidate`; remembers the password after a successful open; `onMarkup(kind)`, `onAnnotationTapped`-aware `onTap`, `onDeleteAnnotation(target)`, `onNoteSubmitted(text)`, `onNoteDismissed()`, `onEditNote(note)`, `onUndo`, `onRedo`), `ReaderUiState.kt` (`Ready.annotations: AnnotationsUiState`, `annotationMenu: AnnotationTarget?`, `noteEditor: NoteEditor?` with `data class NoteEditor(val pageIndex: Int, val at: PdfPoint, val editing: Note?)`), `ReaderScreen.kt`
- Create: `feature/reader/.../annotation/SelectionMarkupBar.kt`, `AnnotationMenuDialog.kt`, `NoteDialog.kt`; strings

**Interfaces:**
- `SelectionMarkupBar(enabled, color, onMarkup: (MarkupKind) -> Unit)`: a bottom bar of three buttons, shown whenever there is a selection. After marking up, the selection is cleared.
- `AnnotationMenuDialog(target, onDelete, onEditNote, onDismiss)` shows the kind, page and text. Delete is offered for own annotations and for foreign deletable ones; Edit only for own notes.
- `NoteDialog(initial: String, editing: Boolean, onSave(String), onDelete(), onDismiss)`. Saving a blank text adds nothing (new) or deletes (editing).
- `onTap` follows the Global Constraints order. The note tool opens `NoteEditor(page, point, null)`.

- [ ] **Step 1:** the JVM tests stay green (`LinkActionTest`, geometry, controller). There's no ViewModel test harness (as in 4c); the decisions live in the tested pure code and the controller.
- [ ] **Step 2: Implement.** `assembleDebug`.
- [ ] **Step 3: Commit** `"Mark up text, edit notes and delete annotations"`.

### Task 6: Annotation mode: toolbar, pen, eraser

**Files:**
- Create: `feature/reader/.../annotation/AnnotationToolbar.kt`, `AnnotationGestures.kt` (`Modifier.inkInput(enabled, pageSize, onStroke(List<PdfPoint>))` and `Modifier.eraserInput(...)` in page-local coordinates)
- Modify: `ReaderViewModel.kt` (`annotating: Boolean`, `tool: AnnotationTool`, `onToggleAnnotating`, `onToolSelected`, `onMarkupColor`, `onPenColor`, `onPenWidth`, `onInkStroke(page, points)`, `onErase(page, erased)`), `ReaderUiState.kt`, `ReaderScreen.kt` (top bar action "Açıklama ekle"; bottom toolbar while annotating; tool state passed to pages), `PdfPageList.kt` / `PdfPagePager.kt` (one-finger scrolling off in pen/eraser modes), `components/PdfPage.kt` (live stroke and pending erase in local state), `ZoomState.kt` (`zoomable` gets `oneFingerPan: Boolean`), designsystem icons `ic_edit`, `ic_ink_eraser`, `ic_sticky_note`, `ic_undo`, `ic_redo`, `ic_text_select` (Material symbols paths, same format as `ic_grid.xml`)

**Interfaces:**
- `enum class AnnotationTool { Select, Pen, Eraser, Note }`.
- `AnnotationToolbar(state: ToolbarState, onTool, onColor, onWidth, onUndo, onRedo, onDone)`. Colour swatches show for the pen (pen colour) and for select/markup (markup colour); widths show for the pen only. Undo and redo are enabled from the controller state.
- Ink gesture: first down with one pointer, every move feeds a `StrokeBuilder` (page points via `CoordinateMapper.toPdfPoints` at the laid-out page width), drawn live. On up → `onStroke(points)`. A second pointer or a consumed change cancels the stroke.
- Eraser gesture: each move computes `erasedBy` against the page's current annotations, accumulates the result in local "pending" state (left out of drawing), and calls `onErase` once on up.

- [ ] **Step 1:** JVM suite green.
- [ ] **Step 2: Implement.** `assembleDebug`.
- [ ] **Step 3: Commit** `"Draw and erase with a pen"`.

### Task 7: Annotation list

**Files:**
- Modify: `components/TableOfContentsSheet.kt` (third tab "Açıklamalar": `LazyColumn` of `annotationListItems`, a kind icon, "Sayfa N", text excerpt (2 lines), trailing delete `IconButton`; tap → jump to the page and the item's top; empty state), `ReaderScreen.kt`, strings

- [ ] **Step 1:** JVM suite green. **Step 2: Implement.** `assembleDebug`. **Step 3: Commit** `"List annotations"`.

### Task 8: Saving from the reader

**Files:**
- Modify: `ReaderViewModel.kt` (`onStop()` → `controller.requestSave()`; `onCleared` → `requestSave`; `controller.fileSaved` → reopen the document at the current position with the session password, which also rebuilds the controller; `onSaveCopy(target: String)` → `SaveAsCopyUseCase` → `UiEvent` to open the copy, or a snackbar on failure), `ReaderScreen.kt` (`LifecycleEventEffect(ON_STOP)`; save-error banner under the top bar with the action "Kopya olarak kaydet" (or "Tekrar dene" for `Failed`); overflow item "Kopya olarak kaydet" via `rememberLauncherForActivityResult(CreateDocument("application/pdf"))` suggesting `"<name without .pdf> (açıklamalı).pdf"`), `navigation/ReaderNavigation.kt` (`readerScreen(onBack, onOpenDocument: (String) -> Unit)`), `app/.../TilePdfNavHost.kt` (pop the reader and open the copy)

- [ ] **Step 1:** JVM suite green. **Step 2: Implement.** `assembleDebug`. **Step 3: Commit** `"Save annotations from the reader"`.

### Task 9: UI tests, manual check, docs

**Files:**
- Create: `feature/reader/src/androidTest/.../annotation/AnnotationToolbarTest.kt` (`selectingPenReportsIt`, `widthsOnlyForPen`, `undoDisabledWithoutHistory`), `NoteDialogTest.kt` (`savingReportsText`, `blankNewNoteAddsNothing`), `components/TableOfContentsSheetTest.kt` (+ `annotationsTabListsAndDeletes`)
- Modify: `README.md` (Features: annotations; architecture note on the controller), `docs/superpowers/specs/2026-10-17-phase5-annotations-design.md` (module ruling: annotation UI in `feature:reader`)

- [ ] **Step 1: Write the UI tests.** Run them with approval. Expected: PASS.
- [ ] **Step 2: Full run.** `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Manual check on emulator-5554:**
  - In `outline.pdf`: highlight a word (yellow), underline, strike out; draw two strokes at 1× and one at 2× zoom (each lands under the finger); erase two strokes in one gesture, undo (both back), redo.
  - Add a note with Turkish text, edit it.
  - Open `annotated.pdf`: tap the foreign highlight → Sil (it disappears), undo (back), delete again.
  - Open the "Açıklamalar" tab: every item is listed; tap → jumps.
  - Press home, wait for the save, return: the reader reloads and everything shows once. Leave, reopen: everything is there.
  - Read the saved file back with `adb pull` + the 5a read test helpers if needed.
  - Horizontal mode: draw a stroke.
  - Read-only: open the pushed `links.pdf` through "Open with" (one-off grant), annotate, go home, return: the banner offers "Kopya olarak kaydet". Save to Downloads: the copy opens with the annotations.
- [ ] **Step 4: Commit** `"Document annotations"`, push, open PR #9 stacked on #8.
