# Phase 12: Form JavaScript — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Acrobat form scripts run in TilePDF. Totals calculate themselves, formats apply, validation rejects bad values with the form's own message, and `app.alert` / `app.response` show dialogs.

**Architecture:**
- **The engine.** Our PDFium build (#19) has V8. JavaScript runs once the form-fill info has an `IPDF_JSPLATFORM`. `pdfium_jni.cpp` gives every form document one.
  - It answers `app_alert`, `app_response`, `Doc_print` and `Doc_gotoPage` through the document's `FormScriptHost`.
  - A document without a host gets default answers silently (OK / Yes, empty response), e.g. the saver's.
- **Document scripts.** `FORM_DoDocumentJSAction` runs on every form document, since field scripts call the functions it defines. `FORM_DoDocumentOpenAction` runs only when a reader sets a host.
- **Blocking dialogs.** The reader's host, `ScriptDialogs`, blocks the PDF thread until the person answers a dialog, or the reader goes away.
- **After a change:** scripts may change any field, so every page's images are redrawn and the shown fields are read again.
- **The draft keeps what the form accepted.** A rejected value isn't kept; a reformatted one is kept as the form holds it. Calculated fields aren't kept: they calculate again when the values are filled in on open and on save.

**Tech Stack:** PDFium (V8) form-fill API, JNI (JavaVM callbacks), Kotlin, Compose.

**Spec:** the roadmap at `~/.claude/plans/nifty-yawning-lark.md`, phase 12. It is stacked on #20.

## Global Constraints
- **Offline.** No script can reach the network. `Doc_mail` and `Doc_submitForm` are left unset, so they do nothing.
- **No hang when the reader closes.** A blocked script call is released when the reader closes; it then gets the default answer.
- **No dialogs while saving.** Saving in the background never shows a dialog.

## Review Focus
- **A script alerts while a value is being filled in.** The dialog shows, the fill waits, and the answer goes back to the script.
- **The reader closes during an alert.** The PDF thread is released; nothing deadlocks.
- **A validation script rejects a value.** The draft doesn't keep it, and saving doesn't fail.
- **A calculated field on another page.** That page redraws.
- **A form without scripts.** It behaves exactly as before.

---

### Task 1: `ScriptDialogs` (JVM)
- `core/pdf/.../FormScriptHost.kt`:
  ```kotlin
  interface FormScriptHost {
      fun alert(message: String, title: String, buttons: Int, icon: Int): Int
      fun response(question: String, title: String, default: String, label: String, password: Boolean): String?
      fun print()
      fun goToPage(pageIndex: Int)
  }
  ```
  - The constants mirror `JSPLATFORM_ALERT_*`.
- `FillableForm.setScriptHost(host: FormScriptHost?)` defaults to doing nothing.
- `feature/reader/.../ScriptDialogs.kt`:
  - `dialog: StateFlow<ScriptDialog?>` (`Alert` or `Prompt`);
  - `answer(ScriptAnswer)`;
  - `close()`;
  - print and go-to-page callbacks.
- Test: `ScriptDialogsTest`.
  - An alert waits for the answer from another thread.
  - Buttons map to return codes.
  - A prompt returns the text, or null on cancel.
  - `close()` releases a waiting call with the default answer.
  - Calls after `close()` return the default at once.

### Task 2: The JS platform in JNI
- `pdfium_jni.cpp`:
  - `JavaVM` is kept in `JNI_OnLoad`.
  - A `ScriptPlatform : IPDF_JSPLATFORM` struct per document holds the document pointer; `formInfo.m_pJsPlatform` points to it.
  - `nativeSetScriptHost(doc, host)` keeps a global ref, released on close.
  - It calls `FORM_DoDocumentOpenAction` the first time a host is set.
  - `FORM_DoDocumentJSAction` runs after the form environment is created.
- `PdfiumDocument` and `PdfiumEditableDocument` get `setScriptHost`.
- `tools/generate_test_pdfs.py` gets `calc.pdf`:
  - text fields `a` and `b`;
  - read-only `total`, with `AFSimple_Calculate("SUM", ["a","b"])` and `AFNumber_Format(2, …)` in `/CO`;
  - `age`, whose validate script alerts "Too old" and rejects values over 150.
- Test: `core/pdf/src/androidTest/.../PdfiumJavaScriptTest`:
  - filling a = 2 and b = 3 makes `total` "5";
  - age 200 calls the host's `alert("Too old")`, and `age` stays empty;
  - with no host, the same fill doesn't block;
  - `forms.pdf`, which has no scripts, still fills as before.

### Task 3: The reader
- `ReaderViewModel`:
  - creates `ScriptDialogs` per opened document and sets it as host;
  - closes it on reopen and in `onCleared`;
  - `setFormValue` fills first, then stores the field's accepted value. It invalidates every page's images and thumbnails, and reloads the form fields around the current page.
- `ReaderScreen`:
  - `ScriptAlertDialog`, with the buttons for the type;
  - `ScriptPromptDialog`, with a text field (password masked).
- Strings: EN + TR ("OK", "Cancel", "Yes", "No"; the title falls back to "This document says").
- Test: `acceptedFormValue` (JVM, pure). Given the asked value, the value before and the value read back, it returns what to keep: the read-back value if it changed, else null.

### Task 4: Docs and checks
- README: form scripts supported (calculations, formats, validation, alerts); not supported: mail, submit, timers and page scripts.
- Store texts: the forms line mentions calculations.
- `testDebugUnitTest lintDebug assembleRelease checkReleasePermissions`; `PdfiumJavaScriptTest` on emulator-5556.
- By hand: on `calc.pdf`, fill a and b and the total shows 5.00; type 200 into age and the alert shows.
