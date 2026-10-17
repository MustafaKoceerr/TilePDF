# Phase 5b — Annotation Drafts and Safe Saving Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every annotation edit lands in a Room draft that survives crashes; undo/redo works on it. When a document closes or the app goes to the background, a worker writes the draft into the PDF safely: temp file, verify, replace. Without write access, or when the file changed elsewhere, the draft is kept and "Kopya olarak kaydet" can write a copy instead.

**Architecture:** One `AnnotationDraft` row per document holds the full list of TilePDF annotations, the deleted foreign refs, a `dirty` flag, the stamp (size + last modified) of the file it is based on, and the last save error (design: "Taslak = tam liste"). `AnnotationRepository` (core:data) prepares a draft, scanning the document once when there is none, updates it, and records save results with compare-and-set, so edits made during a save stay dirty. `AnnotationHistory` (core:domain, pure) gives undo/redo. `AnnotationSaver` (core:data, JVM-tested behind an `AnnotationFiles` boundary) runs the save algorithm. `SaveAnnotationsWorker` is a thin HiltWorker around it, `AnnotationSaveScheduler` enqueues it, and `SaveAsCopyUseCase` (core:domain) writes a copy and moves the draft there. The reader wiring and every UI piece are 5c.

**Tech Stack:** as before, plus WorkManager `androidx.work:work-runtime-ktx:2.11.2` (`work-testing` for androidTest), `androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler` at the catalog's `hiltAndroidx` version (1.4.0; if that doesn't resolve for these artifacts, use the latest stable `androidx.hilt` and note it), kotlinx-serialization-json (already in the catalog) for the draft's JSON columns.

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (Açıklama kayıt akışı) and `docs/superpowers/specs/2026-10-17-phase5-annotations-design.md` (Taslak = tam liste, İlk açılış, Kayıt, İzin, undo cleared after a save)

## Global Constraints

- The original file is only ever replaced by a temp file that PDFium reopened and that holds exactly the draft's own annotation ids, with none of the deleted foreign `/NM` keys. On any failure the original stays untouched and the draft stays dirty.
- Before overwriting, the file's current stamp must equal the draft's base stamp. If it doesn't, record `SaveError.FileChanged` and write nothing.
- No write access (`SecurityException`, or `FileNotFoundException`/`UnsupportedOperationException` opening for write) records `SaveError.NoWritePermission`.
- Overwriting opens `"wt"` and falls back to `"w"` only if the provider rejects `"wt"` (`IllegalArgumentException`/`FileNotFoundException` from mode). Incremental output only ever grows the file, so `"w"` without truncation is still correct. The copy into the original runs under `NonCancellable`.
- Passwords stay in memory only (`SessionPasswords`, process lifetime) and are never written to disk, Room or logs. Without a password for an encrypted document, record `SaveError.NeedsPassword`. The next close after the user enters it saves.
- After a successful save, `removedForeign` drops the saved refs, and every `#index` ref, because indices shift; NM refs added during the save are kept. The base stamp becomes the new file's stamp, and the history is cleared by the caller (5c).
- Library removal cascades to the draft (`ForeignKey` CASCADE), like bookmarks.
- Test rules: JVM unit tests drive TDD, filtered (`--tests`). Room, worker and SAF-file classes get instrumented tests written in their task. Those run only with the user's approval for this plan; otherwise once at the end with their OK. Failures: report the cause and ask.

## Review Focus

1. **Edits made while a save runs** stay dirty and are saved next time. Task 3 `markSavedKeepsLaterEditsDirty`.
2. **A deleted foreign annotation without `/NM` (`#index`) is never applied to the wrong annotation** after a save shifted the indices. Task 3 `markSavedDropsIndexRefs`, Task 5 `copyWithChangedFileSkipsIndexRefs`.
3. **The file was changed by another app while the draft was dirty** → nothing is written and the user can save a copy. Task 5 `changedFileIsNotOverwritten`.
4. **Read-only file** → draft kept, error recorded, nothing half-written. Task 5 `noWriteAccessKeepsDraft`. **A provider that rejects `"wt"`** → `"w"` is used. Task 5 `OpenForOverwriteTest.wtRejectedFallsBackToW`.
5. **Encrypted PDF** saves with the session password and reopens with it. Task 5 instrumented `encryptedDocumentSaves`. Without a password the save records `NeedsPassword`. Task 5 `missingPasswordIsRecorded`.

---

### Task 1: Draft model and JSON codec

**Files:**
- Create: `core/model/src/main/java/com/mustafakoceerr/tilepdf/model/AnnotationDraft.kt`; `core/data/src/main/java/com/mustafakoceerr/tilepdf/data/annotation/AnnotationCodec.kt`
- Modify: `core/data/build.gradle.kts` (kotlin serialization plugin, `kotlinx-serialization-json`)
- Test: `core/data/src/test/.../data/annotation/AnnotationCodecTest.kt`

**Interfaces:**
- `data class FileStamp(val size: Long, val lastModified: Long?)`
- `enum class SaveError { NoWritePermission, FileChanged, NeedsPassword, Failed }`
- `data class AnnotationDraft(val uri: String, val annotations: List<Annotation>, val removedForeign: Set<ForeignRef>, val dirty: Boolean, val base: FileStamp, val saveError: SaveError?)`
- `internal object AnnotationCodec { fun encodeAnnotations(List<Annotation>): String; fun decodeAnnotations(String): List<Annotation>; fun encodeRefs(Set<ForeignRef>): String; fun decodeRefs(String): Set<ForeignRef> }`. Private `@Serializable` DTOs with a `type` discriminator (`markup`/`ink`/`note`) and the version key `v = 1`. Unknown types or versions are skipped, not thrown.

- [ ] **Step 1: `AnnotationCodecTest`**: `allKindsRoundTrip` (one of each, with Turkish text and multi-stroke ink), `refsRoundTrip` (`#2` and an NM), `emptyListsRoundTrip`, `unknownTypeIsSkipped` (hand-written JSON with `"type":"stamp"` next to a note → only the note).
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest --tests "*AnnotationCodecTest"`. Expected: FAIL to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `"Add the annotation draft model"`.

### Task 2: Draft table

**Files:**
- Create: `core/database/.../data/local/AnnotationDraftEntity.kt`, `AnnotationDraftDao.kt`; `core/database/src/androidTest/.../AnnotationDraftDaoTest.kt`
- Modify: `TilePdfDatabase.kt` (version 4, `AutoMigration(3, 4)`, entity, `annotationDraftDao()`), `DatabaseModule.kt`, `TilePdfDatabaseMigrationTest.kt` (+`migrate3To4KeepsLibrary`); schema `4.json` exported

**Interfaces:**
- `@Entity(tableName = "annotation_drafts")` `AnnotationDraftEntity(@PrimaryKey uri: String, annotations: String, removedForeign: String, dirty: Boolean, baseSize: Long, baseModified: Long?, saveError: String?, updatedAt: Long)` with `ForeignKey(DocumentEntity, uri → uri, CASCADE)`.
- `AnnotationDraftDao`: `observe(uri): Flow<AnnotationDraftEntity?>`, `get(uri): AnnotationDraftEntity?`, `@Upsert upsert(entity)`, `delete(uri)`, `dirtyUris(): List<String>`, and `@Transaction open suspend fun update(uri: String, transform: (AnnotationDraftEntity) -> AnnotationDraftEntity?): AnnotationDraftEntity?` (get → transform → upsert; returns the stored row; `null` from transform = no write).

- [ ] **Step 1: Instrumented tests** (written now): `migrate3To4KeepsLibrary`; `AnnotationDraftDaoTest`: `upsertAndObserve`, `deletingDocumentDeletesDraft`, `dirtyUrisListsOnlyDirty`, `updateIsAtomic` (two concurrent `update` calls appending to `annotations` both land).
- [ ] **Step 2: Implement** the entity, DAO and version bump; `./gradlew :core:database:assembleDebug` exports `4.json`. Expected: BUILD SUCCESSFUL, `4.json` exists.
- [ ] **Step 3: Run** `:core:database:connectedDebugAndroidTest` filtered to the two classes (with approval). Expected: PASS.
- [ ] **Step 4: Commit** `"Store annotation drafts"`.

### Task 3: Annotation repository

**Files:**
- Create: `core/data/.../data/annotation/AnnotationRepository.kt`, `OfflineAnnotationRepository.kt`, `FileStamps.kt` (interface; the SAF implementation is in Task 5); `core/data/src/test/.../data/annotation/FakeAnnotationDraftDao.kt`, `OfflineAnnotationRepositoryTest.kt`
- Modify: `DataModule.kt`, `core/data/src/test/.../text/FakePdfDocument.kt` (configurable `annotations` per page)

**Interfaces:**
- `fun interface FileStamps { suspend fun stamp(uri: String): FileStamp? }`
- `interface AnnotationRepository`:
  - `fun observe(uri: String): Flow<AnnotationDraft?>`
  - `suspend fun prepare(uri: String, document: PdfDocument): AnnotationDraft`. With no draft, or a clean draft whose base differs from the current stamp: scan every page's `document.annotations(i).own` and store a clean draft based on the current stamp. A dirty draft whose base differs is kept and gets `saveError = FileChanged`. Otherwise the stored draft is returned.
  - `suspend fun update(uri: String, annotations: List<Annotation>, removedForeign: Set<ForeignRef>)` sets `dirty = true` and keeps `saveError`.
  - `suspend fun markSaved(uri: String, saved: AnnotationDraft, newBase: FileStamp)`. In one DAO `update`: `annotations` equal to `saved.annotations` → `dirty = false`; otherwise it stays dirty. `removedForeign` = current minus saved minus every `#`-key, `base = newBase`, `saveError = null`.
  - `suspend fun markFailed(uri: String, error: SaveError)` keeps `dirty`.
  - `suspend fun moveTo(from: String, to: String, saved: AnnotationDraft, newBase: FileStamp)` writes the draft under `to` like `markSaved`, then deletes `from`. The caller has already put `to` in the library.
  - `suspend fun dirtyUris(): List<String>`
- `OfflineAnnotationRepository(dao, stamps: FileStamps, now: () -> Long)`.

- [ ] **Step 1: `OfflineAnnotationRepositoryTest`** (fake DAO, `FakePdfDocument`, fake stamps): `prepareScansWhenNoDraft` (own annotations of pages 0 and 2 collected, clean, base = stamp), `prepareReturnsStoredDraft` (no scan: `annotations` calls 0), `cleanDraftOfChangedFileIsRescanned`, `dirtyDraftOfChangedFileIsMarkedChanged`, `updateMarksDirty`, `markSavedClearsDirty`, `markSavedKeepsLaterEditsDirty`, `markSavedDropsSavedAndIndexRefs` (current {NM a, NM b, #3}, saved {NM a, #3} → {NM b}), `markFailedKeepsDirty`, `moveToRekeysDraft`, `dirtyUrisOnlyDirty`.
- [ ] **Step 2: Run** `--tests "*OfflineAnnotationRepositoryTest"`. Expected: FAIL to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `"Keep annotations in a draft"`.

### Task 4: Undo and redo

**Files:**
- Create: `core/domain/.../domain/AnnotationHistory.kt`; Test: `core/domain/src/test/.../AnnotationHistoryTest.kt`

**Interfaces:**
- `data class AnnotationState(val annotations: List<Annotation>, val removedForeign: Set<ForeignRef>)`
- `class AnnotationHistory(initial: AnnotationState, limit: Int = 100)` with `val state`, `canUndo`, `canRedo`; `fun add(annotation)`, `fun remove(id: String)`, `fun removeForeign(ref: ForeignRef)`, `fun replace(old: String, new: Annotation)` (note edit, one step), `fun undo()`, `fun redo()`, `fun reset(state: AnnotationState)` (clears both stacks). Each returns the new `AnnotationState`. A new edit clears redo. More than `limit` steps drops the oldest. Removing an unknown id is a no-op that records nothing.

- [ ] **Step 1: `AnnotationHistoryTest`**: `addThenUndoRedo`, `removeForeignIsUndoable`, `replaceIsOneStep`, `newEditClearsRedo`, `unknownRemoveRecordsNothing`, `limitDropsOldest` (limit 2, 3 edits, undo twice → after first edit), `resetClearsHistory`.
- [ ] **Step 2: Run** `./gradlew :core:domain:testDebugUnitTest --tests "*AnnotationHistoryTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** **Step 4: Run.** Expected: PASS. **Step 5: Commit** `"Undo and redo annotation edits"`.

### Task 5: Saving

**Files:**
- Create: `core/data/.../data/annotation/AnnotationFiles.kt` (interface), `SafAnnotationFiles.kt`, `AnnotationSaver.kt`, `SessionPasswords.kt`; `core/data/src/test/.../annotation/AnnotationSaverTest.kt` (+ fakes `FakeAnnotationFiles`, `FakeEditableDocument`); `core/data/src/androidTest/.../annotation/AnnotationSaveFlowTest.kt`
- Modify: `DataModule.kt` (`FileStamps` → `SafAnnotationFiles`)

**Interfaces:**
- `interface AnnotationFiles : FileStamps { suspend fun openForEditing(uri: String, password: String?): EditablePdfDocument; suspend fun openForReading(file: File, password: String?): PdfDocument; fun newTempFile(): File; suspend fun replace(uri: String, with: File) }`. `replace` throws `SecurityException`/`FileNotFoundException` when it can't write.
- `SafAnnotationFiles` (Context, `PdfEngine`, `@IoDispatcher`): `stamp` from `OpenableColumns.SIZE` + `DocumentsContract.Document.COLUMN_LAST_MODIFIED` (or `File` for `file:` URIs; `null` if unreadable). Temp files go in `cacheDir/annotation-saves/`. `replace` opens `"wt"`, falls back to `"w"` when the mode is rejected, copies under `NonCancellable`, and `fsync`s via `FileOutputStream.fd.sync()` when the stream is a `FileOutputStream`.
- `@Singleton class SessionPasswords { fun remember(uri: String, password: String); fun get(uri: String): String?; fun moved(from: String, to: String) }`. In memory only.
- `sealed interface SaveResult { data object NothingToSave; data class Saved(val uri: String); data class Failed(val error: SaveError) }`
- `interface AnnotationSaving { suspend fun save(uri: String): SaveResult; suspend fun saveCopy(uri: String, target: String): SaveResult }`, implemented by `class AnnotationSaver(repository: AnnotationRepository, files: AnnotationFiles, passwords: SessionPasswords)` and bound in `DataModule`. The worker and `SaveAsCopyUseCase` depend on the interface.
  - `save`: draft missing or clean → `NothingToSave`. Stamp ≠ base → `markFailed(FileChanged)`. Open for editing with the session password (`PdfPasswordException` → `NeedsPassword`). `write(annotations, removedForeign)`. If nothing changed → `markSaved(current stamp)`, `Saved`. Otherwise `saveIncremental(temp)` → reopen temp, check the own ids on every page equal the draft's and no NM-keyed removed ref remains, else `Failed` → `replace(uri, temp)` (`SecurityException`/`FileNotFoundException` → `NoWritePermission`) → `markSaved(newStamp)`. The temp file is deleted in `finally`.
  - `saveCopy`: the same, without the stamp check, writing into `target`. If the source's stamp ≠ base, the `#` refs are skipped (NM refs still apply). The result goes to `repository.moveTo(uri, target, …)` and `passwords.moved`. The draft isn't marked failed when a copy fails.

- [ ] **Step 1: `AnnotationSaverTest`**: `cleanDraftSavesNothing`, `savesAndMarksSaved` (replace called with the temp; `markSaved` with the new stamp), `changedFileIsNotOverwritten` (no `openForEditing`, error `FileChanged`), `noWriteAccessKeepsDraft` (`replace` throws `SecurityException` → `NoWritePermission`, still dirty), `verificationFailureKeepsOriginal` (reopened temp lacks an id → `Failed`, `replace` never called), `missingPasswordIsRecorded`, `tempFileIsAlwaysDeleted` (success and failure), `nothingChangedStillMarksSaved`, `copyMovesDraft`, `copyWithChangedFileSkipsIndexRefs`.
- [ ] **Step 2: Run** `--tests "*AnnotationSaverTest"`. Expected: FAIL to compile.
- [ ] **Step 3: Implement.** `SafAnnotationFiles.replace` keeps the `"wt"`→`"w"` decision in a small internal function `openForOverwrite(open: (String) -> OutputStream?): OutputStream`, JVM-tested by `OpenForOverwriteTest` (`wtRejectedFallsBackToW`, `securityExceptionIsNotSwallowed`).
- [ ] **Step 4: Run** both JVM classes. Expected: PASS.
- [ ] **Step 5: Write `AnnotationSaveFlowTest`** (instrumented, file URIs in `cacheDir`, real `Pdfium`, repository over the real DAO in an in-memory DB): `savesIntoTheFile` (a note added to a copy of `sample.pdf` is read back after `save`), `encryptedDocumentSaves` (`encrypted.pdf` with "secret" in `SessionPasswords` → saved, reopens with "secret", note present), `stampChangesAfterSave`. Run it with approval. Expected: PASS. If `encryptedDocumentSaves` fails, PDFium can't save that encryption incrementally. Report it and ask before deciding how 5c treats encrypted files.
- [ ] **Step 6: Commit** `"Save annotations safely"`.

### Task 6: Worker, scheduling, write grants

**Files:**
- Create: `core/data/.../data/annotation/SaveAnnotationsWorker.kt`, `AnnotationSaveScheduler.kt`, `WorkManagerAnnotationSaveScheduler.kt`; `core/domain/.../domain/ResumePendingSavesUseCase.kt` + JVM test; `core/data/src/androidTest/.../annotation/SaveAnnotationsWorkerTest.kt`
- Modify: `gradle/libs.versions.toml`, `core/data/build.gradle.kts` (work, hilt-work, ksp hilt-compiler; work-testing for androidTest), `app/build.gradle.kts` (hilt-work), `TilePdfApp.kt` (`Configuration.Provider` with the injected `HiltWorkerFactory`; calls `ResumePendingSavesUseCase` in `onCreate`), `app/src/main/AndroidManifest.xml` (remove `androidx.work.WorkManagerInitializer` from `InitializationProvider`, `tools:node="remove"`), `SafDocumentRepository.kt` (`takePersistableGrant` tries READ|WRITE, then READ)

**Interfaces:**
- `interface AnnotationSaveScheduler { fun requestSave(uri: String) }`. The WorkManager implementation uses `enqueueUniqueWork("save-annotations:$uri", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SaveAnnotationsWorker>().setInputData(workDataOf("uri" to uri)).build())`.
- `@HiltWorker class SaveAnnotationsWorker @AssistedInject constructor(@Assisted context, @Assisted params, saver: AnnotationSaving) : CoroutineWorker`. `doWork`: no uri → `failure()`; otherwise `saver.save(uri)` → `success()` (errors are in the draft). An unexpected `IOException` → `retry()`, at most 3 attempts (`runAttemptCount`).
- `class ResumePendingSavesUseCase(repository: AnnotationRepository, scheduler: AnnotationSaveScheduler) { suspend operator fun invoke() }` requests a save for every dirty draft.

- [ ] **Step 1: `ResumePendingSavesUseCaseTest`** (JVM): `requestsEveryDirtyDraft`, `nothingDirtyRequestsNothing`. Run: FAIL. Implement. Run: PASS.
- [ ] **Step 2: Wire WorkManager and Hilt.** `./gradlew assembleDebug` succeeds. The merged manifest has no `WorkManagerInitializer` (`app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml`).
- [ ] **Step 3: Write `SaveAnnotationsWorkerTest`** (`TestListenableWorkerBuilder` with a factory that supplies a saver over fakes): `savesTheGivenUri`, `missingUriFails`. Run it with approval. Expected: PASS.
- [ ] **Step 4: Grant.** `takePersistableGrant` READ|WRITE first, READ second. No unit test: it only calls the provider. Checked manually in 5c, when saving into a picked file.
- [ ] **Step 5: Commit** `"Save annotations in the background"`.

### Task 7: Save as copy

**Files:**
- Create: `core/domain/.../domain/SaveAsCopyUseCase.kt`; Test: `core/domain/src/test/.../SaveAsCopyUseCaseTest.kt`

**Interfaces:**
- `class SaveAsCopyUseCase(saver: AnnotationSaving, library: LibraryRepository, documents: DocumentRepository) { suspend operator fun invoke(uri: String, target: String, displayName: String, pageCount: Int): SaveResult }`. It first calls `documents.persistAccess(target)` and `library.recordOpened(target, displayName, pageCount)` (so the draft's foreign key holds), then `saver.saveCopy`. On failure it calls `library.remove(target)` and returns the failure.

- [ ] **Step 1: Tests**: `recordsTargetBeforeSaving` (order checked with a recorder), `failureRemovesTarget`, `successReturnsSaved`. Run: FAIL. Implement. Run: PASS.
- [ ] **Step 2: Commit** `"Save annotated copies"`.

### Task 8: Docs; full verification

- [ ] **Step 1:** README: an annotation-saving section (draft, worker, temp → verify → replace, errors, passwords only in memory, save as copy). Update the module table.
- [ ] **Step 2: Full run.** `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL. `connectedDebugAndroidTest` for `core:database` and `core:data` with the user's OK. Expected: all green.
- [ ] **Step 3: Commit** `"Document annotation saving"`, push, open PR #8 stacked on #7.
