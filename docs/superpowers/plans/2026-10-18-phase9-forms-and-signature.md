# Phase 9: Form filling and signature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task.

**Goal:** Fill in AcroForm fields (text, check box, radio button, combo box, list box) and sign documents with a drawn signature, saved into the PDF through the existing safe-save path.

**Architecture:**
- **Engine:** PDFium's own form API (`fpdf_formfill.h`) is reached through our JNI.
  - Every `NativeDocument` gets a form-fill environment.
  - Fields are listed through `FPDFAnnot_*` form calls.
  - Values are set through `FORM_*` calls, so PDFium regenerates each widget's appearance.
- **Draft:** pending values live in the annotation draft as `formValues` (field name → `/V` value). They behave like `removedForeign`: pending until saved, then dropped.
- **Reader:** the reader applies pending values to its in-memory document, so the pages show them.
- **Signature:** a drawn signature becomes an ink annotation, placed on the page with a tap.

**Tech stack:** C++17 JNI over PDFium chromium/8076, Kotlin, Compose, Room (v7), JUnit.

**User decision (2026-10-18):** "kendimiz yazabilir miyiz? gerekirse c++'dan gidebiliriz". No paid SDK.

## Global constraints
- TDD for JVM unit tests only. Instrumented tests (`core:pdf` form tests, migration v7) are written with the code and run once on the API 35 emulator at the end.
- Field values follow PDF `/V` semantics:
  - **Text:** the text.
  - **Combo box and list box:** the option's label.
  - **Check box:** its export value when checked, `Off` when not.
  - **Radio group:** the export value of the chosen button, `Off` when none.
- Not supported (stated in the README):
  - JavaScript actions and calculation;
  - XFA forms;
  - cryptographic (PAdES) signatures;
  - multi-select list boxes (only the first selection is shown and set).
- Read-only fields are shown but not editable.
- Copy goes in EN and TR.

## Review focus
1. **A field name shared by several widgets** (radio kids, the same field shown twice): setting a value updates every widget of that name.
2. **The reopen after a save:** the values come back from the file, and the draft holds nothing pending.
3. **A turned or cropped page:** tapping a field still finds it, because field rects go through `CoordinateMapper` and the page transform.
4. **Encrypted documents:** form values save incrementally with the session password, like annotations.
5. **A document without a form:** no field loading, no tint, and no extra cost on open.

---

### Task 1: Form fixture
- `tools/generate_test_pdfs.py` → `forms.pdf`: 1 A4 page with an AcroForm (`/DR` Helvetica, `/DA "/Helv 12 Tf 0 g"`). Fields:
  - `name`: text field at bottom-left rect (72, 700, 300, 724);
  - `notes`: multiline text field (72, 600, 300, 680);
  - `agree`: check box, export value `Yes`, with `/AP /N` On/Off (72, 560, 86, 574);
  - `size`: radio group with two kids exporting `S` (72, 520) and `L` (120, 520), 14 pt boxes;
  - `city`: combo box with options Ankara, İstanbul, İzmir (UTF-16), at (72, 470, 200, 490);
  - `id`: read-only text field with value `42` (72, 430, 200, 450).
- Regenerating must leave the other fixtures byte-identical.

### Task 2: Engine (core:pdf, C++ + Kotlin)
- **Native:**
  - `NativeDocument.form` is set by `FPDFDOC_InitFormFillEnvironment` (`FPDF_FORMFILLINFO` version 1, no callbacks) when `FPDF_GetFormType != FORMTYPE_NONE`.
  - `FORM_OnAfterLoadPage` runs after loading a page and `FORM_OnBeforeClosePage` before closing one.
  - `FPDFDOC_ExitFormFillEnvironment` runs before `FPDF_CloseDocument`.
- **JNI:**
  - `nativeHasForm(handle)`;
  - `nativeFormFields(handle, page)` → `NativeFormField[]` (annot index, type, name, value, flags, top-left rect, options, checked, export value);
  - `nativeSetFormText(handle, page, annot, text)`: focus, select all, replace selection, kill focus;
  - `nativeSetFormChecked(handle, page, annot, checked)`: a click at the widget centre when the state differs;
  - `nativeSetFormChoice(handle, page, annot, index)`: focus, `FORM_SetIndexSelected`, kill focus.
- **Model (core:model):**
  - `FormField(pageIndex, annotIndex, name, kind: FormFieldKind, value, rect: PdfRect, options, checked, exportValue, readOnly, multiline)`;
  - `FormFieldKind { Text, CheckBox, RadioButton, ComboBox, ListBox }`.
- **Planning (core:pdf, JVM-tested):** `formEdits(fields, values: Map<String, String>): List<FormEdit>` (`SetText`, `SetChecked`, `SetChoice`) gives the widget operations that make the fields hold `values`.
  - It skips fields that already match, read-only fields, unknown names and options that don't exist.
  - For a radio group: the button whose export value matches is checked; with `Off`, the checked one is unchecked.
- **API:**
  - `PdfDocument.hasForm()`, `formFields(pageIndex)`, `applyFormValues(values): Set<Int>` (the pages changed);
  - `EditablePdfDocument.formFields(pageIndex)` and `applyFormValues(values): Boolean`.
- **Tests:**
  - `FormEditsTest`: `textChangesOnlyWhenDifferent`, `checkBoxFollowsExportValue`, `radioChecksMatchingButton`, `radioOffUnchecksChecked`, `choiceByLabel`, `unknownOptionIsSkipped`, `readOnlyIsSkipped`, `unknownNameIsSkipped`.
  - Instrumented `PdfiumFormTest`: lists the six fields with their kinds and rects; sets text, check box, radio and combo values and reads them back; the values survive a save and reopen.

### Task 3: Draft (core:database, core:data)
- `AnnotationDraftEntity.formValues` holds JSON with default `'{}'`: Room v7 by auto-migration, plus a migration test `migrate6To7`.
- `AnnotationDraft.formValues: Map<String, String>` holds the pending values.
- `AnnotationRepository.setFormValue(uri, name, value)` marks the draft dirty.
- `afterSave` drops the saved values; the draft stays dirty while pending values remain.
- **Tests (`OfflineAnnotationRepositoryTest`):** `formValueMarksDirty`, `savedFormValuesAreDropped`, `newerFormValueSurvivesSave`.

### Task 4: Saving (core:data)
- `AnnotationSaver.write` applies `draft.formValues` after the annotations. A changed form counts as changed.
- `holdsExactly` also checks that every pending field reads back with its value.
- `export` (share and print) includes them.
- **Tests (`AnnotationSaverTest` with the fake files extended):** `savesFormValues`, `matchingFormValuesNeedNoWrite`, `lostFormValueFailsVerification`, `exportIncludesFormValues`.

### Task 5: Reader (feature:reader)
- **On open, for a document with a form:**
  - pending values are applied to the reading document;
  - fields load per page on demand into `formFields: Map<Int, List<FormField>>`, for the current page and its neighbours.
- **Tap order:** selection → note placement → annotation menu → **form field** → link → full screen.
  - Text: a dialog with a single-line or multiline `TextField`.
  - Check box: toggles.
  - Radio: chooses the button.
  - Combo or list: a dialog of options.
  - Read-only: falls through to link and full screen.
- **Setting a value:** draft `setFormValue`, then apply to the reading document, then invalidate that page's images, then reload its fields.
- **Pages draw a light tint over editable fields.** It is drawn through the page transform and hidden while annotating.
- **Pure helper, JVM-tested:** `formFieldAt(fields, point)` → `FormFieldAtTest` (`insideHits`, `outsideMisses`, `readOnlyIgnored`, `smallestWins`).

### Task 6: Signature (core:data, feature:reader)
- **`SignatureStore`** (core:data) keeps the saved signature in `filesDir/signature.json`: normalised strokes in a unit box plus its aspect ratio.
  - `save`, `load` and `clear`, JVM-tested with a temp directory.
- **Pure helpers in feature:reader:**
  - `normalizeSignature(strokes: List<List<Offset-like points>>)`: fits the strokes into a unit box and keeps the aspect ratio.
  - `placeSignature(signature, at: PdfPoint, widthPt)`: gives the stroke points with the signature's top-left at `at`.
  - Both are tested in `SignatureGeometryTest`.
- **UI:**
  - The reader ⋮ menu gets "Add signature" / "İmza ekle".
  - It opens a pad dialog (Compose canvas, Clear / Save) when no signature is saved; otherwise a choice: "Use saved signature" or "Draw new".
  - Then the reader is in placement mode, with the hint "Tap where to sign" / "İmzalamak için dokunun". The next tap on a page adds an Ink annotation (black, 1.5 pt) 160 pt wide through the annotation controller, so it can be undone and saved like any drawing.

### Task 7: Verification
- JVM: the full `testDebugUnitTest`, `lintDebug` and `assembleRelease`.
- Device (API 35):
  - `PdfiumFormTest` and `TilePdfDatabaseMigrationTest`.
  - On the emulator:
    - fill in every field kind in `forms.pdf` and leave the reader (save), then reopen: the values are there;
    - the values also show in the share export;
    - a rotated page still takes taps on its fields;
    - a signature is placed, undone, placed again and saved.
- The README lists form filling and signing, and the unsupported items above.
- PR with base `phase8b-reading-comfort`.
