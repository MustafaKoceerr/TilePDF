# Phase 4a — Opening Documents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PDFs open from other apps ("Open with" / "Share"), password-protected PDFs open after the user types the password, and files from providers that can't be reopened later (no persistable grant, or no known size) still open and stay in the library.

**Architecture:** `DocumentRepository.prepare(uri)` turns any incoming URI into one the app can reopen: the same URI when a read grant is (or can be) persisted, otherwise a private copy under `filesDir/documents/` addressed by a `file://` URI. `OpenDocumentUseCase` prepares, opens (optionally with a password) and records the prepared URI. `MainActivity` turns VIEW/SEND intents into a `ReaderRoute`. The reader shows a password dialog instead of the error screen and retries the open; the password is never stored.

**Tech Stack:** as before; Compose UI tests for the dialog; `ActivityScenario` for intents.

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (phase 4: intent, şifreli PDF; Aşama 2 ruling: unsized streams copied in phase 4)

Phase 4 is split: **4a** opening documents (this plan), **4b** navigation (thumbnails, bookmarks, links), **4c** display (horizontal page mode, sepia, keep screen on, full screen).

## Global Constraints

- Accept only `content://` URIs from intents (no `file://`, which other apps can't legitimately share since API 24). MIME `application/pdf`.
- Passwords live only in memory for the current open; never logged, persisted or put in a route/SavedStateHandle.
- Private copies: `filesDir/documents/<sanitized display name>-<8 hex of SHA-256(source uri)>.pdf`; written to a temp file and renamed only after the copy completes.
- Removing a library entry deletes its private copy, never a user's original file.
- No new permissions in the manifest.

## Review Focus

1. **Rotation while the password dialog is open** keeps the dialog and does not reopen the file or lose the error state. Task 3 Compose test `passwordDialogSurvivesRecreation`.
2. **Same intent delivered again after process/config recreation** must not push a second reader. Task 4 test `rotationDoesNotReopen` (recreate the scenario; back stack has one reader).
3. **Copy interrupted (provider throws mid-stream)** leaves no partial file that later looks valid. Task 1 test `failedCopyLeavesNothingBehind`.
4. **Wrong password then correct** opens; **cancel** returns to the previous screen. Task 3 tests.
5. **Huge files** copy without loading into memory (streamed). Covered by implementation constraint + Task 1 test copying `text-dense.pdf` (290 KB) through a 4 KB-chunk provider.

---

### Task 1: `prepare` — keep a reopenable URI; private copies

**Files:**
- Modify: `core/data/.../document/DocumentRepository.kt`, `SafDocumentRepository.kt`
- Create: `core/data/src/androidTest/AndroidManifest.xml` (test `ContentProvider` authority `com.mustafakoceerr.tilepdf.core.data.test.files`), `core/data/src/androidTest/java/.../document/TestDocumentsProvider.kt`
- Test: `core/data/src/androidTest/java/com/mustafakoceerr/tilepdf/data/document/SafDocumentRepositoryTest.kt`

**Interfaces:**
- `DocumentRepository.prepare(uri: String): String` — returns `uri` if it's already a private copy or a persisted grant exists or can be taken (`takePersistableUriPermission` succeeds) **and** the provider reports a size (`statSize > 0`); otherwise copies to a private file and returns its `file://` URI. Runs on `@IoDispatcher`.
- `DocumentRepository.forget(uri: String)` — deletes the private copy behind `uri` if it is one; no-op otherwise.
- `accessibleUris()` also returns the `file://` URIs of existing private copies.
- `TestDocumentsProvider` (androidTest only): serves `sample.pdf` from `core:testing` assets as `content://…/sized/sample.pdf` (real fd, known size) and `content://…/pipe/sample.pdf` (pipe, `statSize == -1`, written in 4 KB chunks), and `content://…/broken/sample.pdf` (pipe that throws after 1 KB).

- [ ] **Step 1: Write `SafDocumentRepositoryTest`:**
  - `privateCopyIsReturnedUnchanged`
  - `unsizedStreamIsCopied`: `prepare(pipe uri)` returns a `file://` URI under `filesDir/documents/`, bytes equal `sample.pdf`, and `Pdfium` opens it with 3 pages.
  - `nonPersistableGrantIsCopied`: `prepare(sized uri)` (test provider grants nothing persistable) → copy.
  - `failedCopyLeavesNothingBehind`: `prepare(broken uri)` throws `IOException`; `filesDir/documents/` has no file.
  - `copiesAreListedAsAccessible`; `forgetDeletesOnlyPrivateCopies` (`forget(content uri)` deletes nothing).
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :core:data:connectedDebugAndroidTest` — Expected: FAIL to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run per device (emulators + R68R800REKZ)** — Expected: PASS.
- [ ] **Step 5: Commit** — `"Keep a reopenable URI for every opened PDF"`

### Task 2: Use case prepares, opens with a password, records the prepared URI

**Files:**
- Modify: `core/domain/.../OpenDocumentUseCase.kt`, `OpenDocumentUseCaseTest.kt`; `LibraryViewModel.kt` (`onRemove` calls `documentRepository.forget`)
- Test: `OpenDocumentUseCaseTest` additions; `feature/library/src/test/.../LibraryViewModelTest.kt`

**Interfaces:**
- `OpenedDocument` gains `val uri: String` (the prepared URI the reader must use for library and position).
- `suspend operator fun invoke(uri: String, password: String? = null): OpenedDocument` — `prepare(uri)` first; then `open(prepared, password)`; record and restore position under `prepared`.

- [ ] **Step 1: Tests:** `opensThePreparedUri` (fake `prepare` maps `content://x` → `file://copy`; `open` and `recordOpened` receive `file://copy`; `OpenedDocument.uri == "file://copy"`); `passesThePassword`; LibraryViewModel `removingDeletesPrivateCopy` (fakes; `forget` called with the uri).
- [ ] **Step 2: Run** `./gradlew :core:domain:testDebugUnitTest :feature:library:testDebugUnitTest` — Expected: FAIL to compile.
- [ ] **Step 3: Implement**; `ReaderViewModel` uses `opened.uri` for saving positions and library updates instead of the route's uri.
- [ ] **Step 4: Run** — Expected: PASS; full JVM suite green.
- [ ] **Step 5: Commit** — `"Open the prepared URI and accept a password"`

### Task 3: Password dialog in the reader

**Files:**
- Modify: `ReaderUiState.kt` (new `data class PasswordRequired(val wrongPassword: Boolean) : ReaderUiState`), `ReaderViewModel.kt`, `ReaderScreen.kt`, reader `strings.xml`
- Create: `feature/reader/src/main/java/.../components/PasswordDialog.kt`
- Test: `feature/reader/src/androidTest/java/com/mustafakoceerr/tilepdf/feature/reader/components/PasswordDialogTest.kt`

**Interfaces:**
- `ReaderViewModel.onPasswordEntered(password: String)` re-runs the open with the password; `PdfPasswordException` maps to `PasswordRequired(wrongPassword = it.wrongPassword)` instead of `Error`.
- `@Composable fun PasswordDialog(wrongPassword: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit)` — `OutlinedTextField` with `PasswordVisualTransformation`, IME action Done submits, field text in `rememberSaveable`; strings `reader_password_title` = "Şifre gerekli", `reader_password_hint` = "Şifre", `reader_password_wrong` = "Şifre yanlış", `reader_password_open` = "Aç"; reuse `cancel`. Remove `reader_error_password`.
- Cancel calls the screen's `onBack`.

- [ ] **Step 1: Write `PasswordDialogTest`** (Compose rule, `StateRestorationTester`): `submitSendsTypedPassword`, `doneKeySubmits`, `wrongPasswordShowsMessage`, `cancelCallsOnCancel`, `passwordDialogSurvivesRecreation` (typed text restored).
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:reader:connectedDebugAndroidTest` — Expected: FAIL to compile.
- [ ] **Step 3: Implement** dialog, state and ViewModel retry (the open job is replaced, never run twice concurrently).
- [ ] **Step 4: Run per device** — Expected: PASS. Manual on emulator-5554: open `encrypted.pdf` → dialog; "wrong" → "Şifre yanlış"; "secret" → page with "Encrypted page".
- [ ] **Step 5: Commit** — `"Ask for the password of protected PDFs"`

### Task 4: Open with / Share from other apps

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (two intent filters on `MainActivity`: `VIEW` + `DEFAULT`, scheme `content`, mimeType `application/pdf`; and `SEND` + `DEFAULT`, mimeType `application/pdf`), `MainActivity.kt`, `app/.../navigation/TilePdfNavHost.kt`
- Create: `app/src/main/java/com/mustafakoceerr/tilepdf/IncomingDocument.kt`
- Test: `app/src/test/java/com/mustafakoceerr/tilepdf/IncomingDocumentTest.kt` (pure parsing on a small `IntentData` value), `app/src/androidTest/java/com/mustafakoceerr/tilepdf/OpenWithTest.kt`

**Interfaces:**
- `internal fun incomingDocumentUri(action: String?, data: String?, streamExtra: String?, type: String?): String?` — `VIEW` → `data` if it starts with `content://`; `SEND` with type `application/pdf` → `streamExtra` if `content://`; anything else `null`.
- `MainActivity` computes it only when `savedInstanceState == null`, and in `onNewIntent`; `TilePdfNavHost(openUri: String?, onOpened: () -> Unit)` navigates to `ReaderRoute(uri)` once, then calls `onOpened` to clear it.

- [ ] **Step 1: Write `IncomingDocumentTest`** (VIEW content → uri; VIEW file:// → null; SEND pdf stream → uri; SEND text → null; MAIN → null) and `OpenWithTest` (launch `MainActivity` with `ACTION_VIEW` on the Task 1 test provider's sized `sample.pdf` URI and `FLAG_GRANT_READ_URI_PERMISSION` → reader title `sample.pdf` shown; `rotationDoesNotReopen`: `scenario.recreate()` → one reader, back goes to the library).
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.** The test provider for the app test lives in `app/src/androidTest` (copy of Task 1's provider serving from `core:testing` assets).
- [ ] **Step 4: Run per device** — Expected: PASS. Manual on emulator-5554: Files app → `outline.pdf` → "Open with" TilePDF opens it; Files → Share → TilePDF opens it; it appears in the library and reopens after the app restarts.
- [ ] **Step 5: Commit** — `"Open PDFs from other apps"`

### Task 5: Docs; full verification

- [ ] **Step 1:** README Features (open with/share, password), Known limitations (remove "password-protected PDFs aren't supported").
- [ ] **Step 2:** Full green run: `./gradlew assembleDebug assembleDebugAndroidTest test lint :app:assembleRelease`; connected per device including R68R800REKZ.
- [ ] **Step 3: Commit** — `"Document opening PDFs from other apps"`
