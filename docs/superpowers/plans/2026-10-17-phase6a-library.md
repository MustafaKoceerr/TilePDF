# Phase 6a — Library: Favorites, Sorting, Search, Undoable Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The library filters to favorites, sorts by last opened, name or date added (remembered), searches names ignoring case and Turkish accents, and removes a document only after an undo window.

**Architecture:** `documents` gets a `favorite` column (Room v5, auto-migration). The sort choice is stored in DataStore behind a small `LibraryPreferencesRepository`. Pure functions decide which rows show and in what order (`libraryRows`, `matchesQuery`), and are JVM-tested. `LibraryViewModel` (JVM-testable, as today) keeps the query, the filter and the pending removals; a removal is committed when its snackbar ends without Undo.

**Tech Stack:** as before.

**Spec:** `docs/superpowers/specs/2026-10-16-tilepdf-v1-design.md` (Okuma → Kütüphane: son okunanlar, favoriler, sıralama, arama) and `docs/superpowers/specs/2026-10-17-phase6-quality-design.md` (6a decisions)

## Global Constraints

- Search folds case and accents: NFD with combining marks dropped, `ı`/`İ` → `i`, lowercase; "sozlesme" matches "Sözleşme.pdf". The query is trimmed, and a blank query matches everything.
- Sort order ties fall back to the last opened time, newest first. Name order uses the same folding.
- Removal: the row disappears at once; the snackbar "Kütüphaneden kaldırıldı" with "Geri al" shows. The repository removal and the copy deletion happen only when the snackbar is dismissed or times out. A pending removal still runs if the screen leaves (`onCleared` commits pending removals).
- Copy (Turkish now; English comes in 6b): `library_all` "Tümü", `library_favorites` "Favoriler", `library_favorite_add` "Favorilere ekle", `library_favorite_remove` "Favorilerden çıkar", `library_sort` "Sırala", `library_sort_recent` "Son açılan", `library_sort_name` "Ada göre", `library_sort_added` "Eklenme tarihi", `library_search` "Ara", `library_search_hint` "Kütüphanede ara", `library_search_close` "Aramayı kapat", `library_removed` "Kütüphaneden kaldırıldı", `library_undo` "Geri al", `library_more` "Diğer seçenekler", `library_no_favorites` "Henüz favori yok", `library_no_results` "Sonuç yok".
- Test rules: JVM unit tests drive TDD (`--tests`). The Room migration test is written in Task 1 and runs with the user's approval, as do the UI test and the manual check in Task 4.

## Review Focus

1. **Undo after the snackbar times out** must not be possible, and the document must be gone. Removing two documents quickly must remove both. Task 3 `secondRemovalCommitsFirst`, `timeoutCommits`.
2. **Leaving the library while a removal is pending** still removes it (no ghost entries). Task 3 `clearedCommitsPending`.
3. **Search with Turkish dotted/dotless i** ("ISIK" vs "ışık.pdf", "istanbul" vs "İstanbul.pdf"). Task 2 `turkishIMatches`.
4. **Favorites filter with no favorites** shows "Henüz favori yok", not the first-run text. Task 4 UI test `emptyFavoritesHint`.
5. **Upgrade from v4**: the library and drafts are kept, and nothing is a favorite. Task 1 `migrate4To5KeepsLibrary`.

---

### Task 1: Favorite column

**Files:**
- Modify: `core/database/.../DocumentEntity.kt` (+ `@ColumnInfo(defaultValue = "0") val favorite: Boolean = false`; `asExternalModel` maps it and `addedAt`), `DocumentDao.kt` (+ `setFavorite(uri, favorite)`), `TilePdfDatabase.kt` (v5, `AutoMigration(4, 5)`), schema `5.json`; `core/model/.../LibraryDocument.kt` (+ `favorite: Boolean = false`, `addedAt: Long = 0`); `core/data/.../LibraryRepository.kt` (+ `setFavorite`), `OfflineLibraryRepository.kt`; fakes implementing `LibraryRepository` (`LibraryViewModelTest`, `OpenDocumentUseCaseTest`, `SaveAsCopyUseCaseTest`)
- Test: `core/database/src/androidTest/.../TilePdfDatabaseMigrationTest.kt` (+ `migrate4To5KeepsLibrary`); `DocumentDaoTest` if present, else in the migration test: `setFavoriteIsStored`

- [ ] **Step 1: Write the instrumented tests.**
- [ ] **Step 2: Implement**; `./gradlew :core:database:assembleDebug` exports `5.json`; `assembleDebug` and the JVM suites of `core:data`, `core:domain`, `feature:library` are green.
- [ ] **Step 3: Run** the migration test class (with approval). Expected: PASS.
- [ ] **Step 4: Commit** `"Mark documents as favorites"`.

### Task 2: Rows: filter, search, sort

**Files:**
- Create: `core/model/.../LibrarySort.kt` (`enum class LibrarySort { Recent, Name, Added }`), `feature/library/.../LibraryRows.kt`; `core/data/.../preferences/LibraryPreferencesRepository.kt` + `DataStoreLibraryPreferencesRepository.kt` (key `library_sort`, unknown → Recent), `DataModule` binding
- Test: `feature/library/src/test/.../LibraryRowsTest.kt`; `core/data/src/test/.../DataStoreLibraryPreferencesRepositoryTest.kt`

**Interfaces:**
- `enum class LibraryFilter { All, Favorites }`
- `fun matchesQuery(name: String, query: String): Boolean`; `internal fun fold(text: String): String`.
- `fun libraryRows(documents: List<LibraryDocument>, accessible: Set<String>, query: String, filter: LibraryFilter, sort: LibrarySort, hidden: Set<String>): List<LibraryItem>`.
- `interface LibraryPreferencesRepository { val sort: Flow<LibrarySort>; suspend fun setSort(sort: LibrarySort) }`.

- [ ] **Step 1: Tests.**
  - `LibraryRowsTest`: `recentIsNewestFirst`, `nameSortFoldsCase` ("çay" before "Defter", "ılık" before "Kalem"), `addedSortNewestFirst`, `favoritesFilter`, `queryFoldsAccents` ("sozlesme" → "Sözleşme.pdf"), `turkishIMatches`, `blankQueryMatchesAll`, `hiddenAreLeftOut`, `accessibilityIsCarried`.
  - `DataStoreLibraryPreferencesRepositoryTest`: `defaultsToRecent`, `setSortIsRead`, `unknownReadsAsRecent`.
- [ ] **Step 2: Run.** Expected: FAIL to compile. **Step 3: Implement.** **Step 4: Run.** Expected: PASS. **Step 5: Commit** `"Sort, filter and search the library"`.

### Task 3: Library ViewModel

**Files:**
- Modify: `LibraryViewModel.kt`, `LibraryUiState.kt` (`Success(items, query, searching, filter, sort, hasDocuments, removed: LibraryItem?)`), `LibraryViewModelTest.kt` (fakes gain favorites, flows and a preferences fake)

**Interfaces:**
- `onQueryChange(String)`, `onSearchOpen()`, `onSearchClose()` (clears the query), `onFilterSelected(LibraryFilter)`, `onSortSelected(LibrarySort)`, `onToggleFavorite(uri)`.
- `onRemove(uri)`: hides the row, sets `removed` (the snackbar shows it), and commits an earlier pending removal first. `onUndoRemove()` unhides it and clears `removed`. `onRemoveConfirmed()` (snackbar dismissed or timed out) runs `libraryRepository.remove` + `documentRepository.forget` and clears `removed`.
- `onCleared()` commits a pending removal on the application scope (injected `@ApplicationScope`).

- [ ] **Step 1: Tests** (`setMain(UnconfinedTestDispatcher())`, as today): `searchFilters`, `closingSearchClearsQuery`, `favoritesFilter`, `sortIsSavedAndApplied`, `toggleFavoriteWrites`, `removeHidesUntilConfirmed`, `undoRestores`, `timeoutCommits` (= `onRemoveConfirmed`), `secondRemovalCommitsFirst`, `clearedCommitsPending` (`onCleared` calls `internal fun commitPendingRemoval()`, which the test calls directly), `removingDeletesPrivateCopy` (kept, now after confirmation).
- [ ] **Step 2: Run** `./gradlew :feature:library:testDebugUnitTest --tests "*LibraryViewModelTest"`. Expected: FAIL. **Step 3: Implement.** **Step 4: Run.** Expected: PASS. **Step 5: Commit** `"Library state: search, filter, sort, undoable removal"`.

### Task 4: Library screen; UI test; manual check; docs

**Files:**
- Modify: `LibraryScreen.kt`:
  - Top bar: search action that turns the bar into a search field, and a sort menu (radio items).
  - Chips row: Tümü / Favoriler.
  - Rows: star toggle plus an overflow menu (Kütüphaneden kaldır).
  - Snackbar with "Geri al".
  - Empty states: first run (existing text), no favorites, no results.
- Modify also: strings, designsystem icons (`ic_star`, `ic_star_border`, `ic_sort`), README Features
- Create: `feature/library/src/androidTest/.../LibraryScreenTest.kt` (`favoriteToggleReports`, `emptyFavoritesHint`, `noResultsHint`, `removeShowsUndo`)

- [ ] **Step 1: Implement.** `assembleDebug`.
- [ ] **Step 2: UI test** (with approval). Expected: PASS.
- [ ] **Step 3: Manual check on emulator-5554:**
  - Star `outline.pdf` and filter Favoriler: only it shows.
  - Sort by name, then reopen the app: still by name.
  - Search "outlıne" and "OUTLINE": both find it.
  - Remove `links.pdf`, then Geri al: it's back.
  - Remove `links.pdf` again and let the snackbar time out: it's gone after an app restart.
- [ ] **Step 4: Full run** `assembleDebug assembleRelease testDebugUnitTest lintDebug`. Expected: green. **Commit** `"Library: favorites, sorting, search"`, push, open PR #10 stacked on #9.
