# Search Results Document Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a document selector to the search-results screen so the user can change which document(s) results come from and re-run the search live, without going back to the search screen or juggling the links window.

**Architecture:** `SearchResults` already holds `selectedTranslations: List<String>` and re-runs `searchControl.getMultiSearchResults(selectedTranslations, searchText)` on demand. We add a toolbar action item that opens the existing `Dialogs.multiselect(...)` chooser, restricted to Strong's-enabled Bibles when the search is a Strong's "find all occurrences" search (signalled by a new intent extra). Confirming re-runs the search. A pure, unit-tested filter function decides the candidate document list.

**Tech Stack:** Kotlin, Android AppCompat action bar, JSword (`SwordBook`, `FeatureType`, `IndexStatus`), mockito-kotlin2 + JUnit for the unit test.

## Global Constraints

- Branch: `current-stable` (do all work here; do NOT use worktrees — they branch from the wrong base).
- All repo output in English (code, comments, commit messages).
- **No new translatable strings** — reuse existing `R.string.choose_translations` ("Choose translations…").
- New files use the copyright header for 2026: `Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.` (no "Martin Denham"). When editing an existing file, update its header year to `2026` and `Tuomas Airaksinen` → `Sykerö Software / Tuomas Airaksinen` (keep "Martin Denham" if present).
- Kotlin: import classes and use simple names; no fully-qualified names in code.
- Run Kotlin unit tests with: `./gradlew testStandardGoogleplayDebugUnitTest -x npmInstall -x npmUpgrade -x jsBuild` and `dangerouslyDisableSandbox: true`.

---

### Task 1: Pure candidate-document filter + unit test

Decides which Bibles the chooser offers: Strong's-enabled only for a Strong's search, all Bibles otherwise. Pure function so it is unit-testable without the Activity.

**Files:**
- Create: `app/src/main/java/net/bible/android/view/activity/search/SearchDocumentFilter.kt`
- Test: `app/src/test/java/net/bible/android/view/activity/search/SearchDocumentFilterTest.kt`

**Interfaces:**
- Produces: `fun candidateSearchDocuments(strongsSearch: Boolean, allBibles: List<SwordBook>): List<SwordBook>` — returns `allBibles` filtered to those with `FeatureType.STRONGS_NUMBERS` when `strongsSearch` is true, otherwise `allBibles` unchanged (order preserved).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/android/view/activity/search/SearchDocumentFilterTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.search

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Test

class SearchDocumentFilterTest {
    private fun bible(hasStrongs: Boolean): SwordBook = mock {
        whenever(it.hasFeature(FeatureType.STRONGS_NUMBERS)).thenReturn(hasStrongs)
    }

    @Test
    fun strongsSearchKeepsOnlyStrongsEnabledBibles() {
        val withStrongs = bible(hasStrongs = true)
        val withoutStrongs = bible(hasStrongs = false)
        val result = candidateSearchDocuments(strongsSearch = true, allBibles = listOf(withStrongs, withoutStrongs))
        assertThat(result, equalTo(listOf(withStrongs)))
    }

    @Test
    fun nonStrongsSearchKeepsAllBibles() {
        val a = bible(hasStrongs = true)
        val b = bible(hasStrongs = false)
        val all = listOf(a, b)
        val result = candidateSearchDocuments(strongsSearch = false, allBibles = all)
        assertThat(result, equalTo(all))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.search.SearchDocumentFilterTest" -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL to compile — `candidateSearchDocuments` is unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/net/bible/android/view/activity/search/SearchDocumentFilter.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.search

import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook

/**
 * Documents offered in the search-results document selector.
 *
 * A Strong's-number query ("find all occurrences") only matches Strong's-tagged modules, so for a
 * Strong's search the chooser is restricted to Strong's-enabled Bibles. Any other search offers all
 * Bibles.
 */
fun candidateSearchDocuments(strongsSearch: Boolean, allBibles: List<SwordBook>): List<SwordBook> =
    if (strongsSearch) allBibles.filter { it.hasFeature(FeatureType.STRONGS_NUMBERS) } else allBibles
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.search.SearchDocumentFilterTest" -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/search/SearchDocumentFilter.kt \
        app/src/test/java/net/bible/android/view/activity/search/SearchDocumentFilterTest.kt
git commit -m "Add candidate-document filter for search results selector"
```

---

### Task 2: Signal a Strong's search via a new intent extra

`SearchResults` must know whether the current search is a Strong's "find all occurrences" search, to restrict the chooser. Add the constant and set it from `LinkControl.showAllOccurrences`.

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/search/SearchControl.kt` (constants block near `TARGET_DOCUMENT`, ~line 275)
- Modify: `app/src/main/java/net/bible/android/control/link/LinkControl.kt` (`showAllOccurrences`, ~line 368)

**Interfaces:**
- Produces: `SearchControl.IS_STRONGS_SEARCH: String` — intent-extra key (boolean). `true` only for the Strong's find-all-occurrences flow.

- [ ] **Step 1: Add the constant**

In `SearchControl.kt`, in the `companion object` next to the existing keys:

```kotlin
        const val SEARCH_DOCUMENT = "SearchDocument"
        const val TARGET_DOCUMENT = "TargetDocument"
        const val SELECTED_TRANSLATIONS = "SelectedTranslations"
        const val IS_STRONGS_SEARCH = "IsStrongsSearch"
```

- [ ] **Step 2: Set the extra in LinkControl.showAllOccurrences**

In `LinkControl.kt` `showAllOccurrences`, immediately after the existing `searchParams.putString(SearchControl.TARGET_DOCUMENT, currentBible.initials)` line, add:

```kotlin
        searchParams.putBoolean(SearchControl.IS_STRONGS_SEARCH, true)
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/control/search/SearchControl.kt \
        app/src/main/java/net/bible/android/control/link/LinkControl.kt
git commit -m "Signal Strong's find-all-occurrences search via intent extra"
```

---

### Task 3: Document selector in the search-results toolbar

Add a toolbar action item that shows the selected translation abbreviations, opens the multiselect chooser (candidates from Task 1), and re-runs the search on confirm.

**Files:**
- Modify: `app/src/main/res/menu/search_results_actionbar_menu.xml`
- Modify: `app/src/main/java/net/bible/android/view/activity/search/SearchResults.kt`

**Interfaces:**
- Consumes: `candidateSearchDocuments(Boolean, List<SwordBook>)` (Task 1); `SearchControl.IS_STRONGS_SEARCH` (Task 2); `Dialogs.multiselect(context, title: String, items, itemToString, preSelected): List<T>` (returns empty list on cancel / "select all" / empty pick — treat empty as "no change"); `SwordDocumentFacade.bibles: List<Book>`.

- [ ] **Step 1: Add the menu item**

In `app/src/main/res/menu/search_results_actionbar_menu.xml`, add before the existing `openResultsInWindow` item (so it sits to the left of the overflow):

```xml
    <item android:id="@+id/changeSearchDocuments"
        android:title="@string/choose_translations"
        android:icon="@drawable/ic_library_books_white_24dp"
        app:showAsAction="always|withText"
        />
```

- [ ] **Step 2: Read the Strong's flag and add imports in SearchResults**

In `SearchResults.kt`, add imports:

```kotlin
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.index.IndexStatus
```

(Do NOT re-add already-present imports: `lifecycleScope`, `SwordBook`, `Dialogs`, `Intent`, `Menu`, `MenuItem` are already imported. `SearchIndex` is in the same package — no import needed.)

Add a field next to `selectedTranslations` (line ~59):

```kotlin
    private var isStrongsSearch = false
    private var documentSelectorMenuItem: MenuItem? = null
```

In `onCreate`, right after the `selectedTranslations = ...` assignment (line ~80), add:

```kotlin
        isStrongsSearch = intent.getBooleanExtra(SearchControl.IS_STRONGS_SEARCH, false)
```

- [ ] **Step 3: Capture the menu item and show current selection**

Replace `onCreateOptionsMenu` with:

```kotlin
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_results_actionbar_menu, menu)
        documentSelectorMenuItem = menu.findItem(R.id.changeSearchDocuments)
        updateDocumentSelectorTitle()
        return super.onCreateOptionsMenu(menu)
    }
```

Add these helpers (anywhere in the class body, e.g. after `openResultsInAWindow`):

```kotlin
    private fun allBibles(): List<SwordBook> =
        SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().sortedBy { it.abbreviation }

    private fun updateDocumentSelectorTitle() {
        val byInitials = allBibles().associateBy { it.initials }
        val label = selectedTranslations
            .mapNotNull { byInitials[it]?.abbreviation }
            .joinToString(", ")
            .ifEmpty { getString(R.string.choose_translations) }
        documentSelectorMenuItem?.title = label
    }
```

- [ ] **Step 4: Handle the click — open chooser and re-run search**

In `onOptionsItemSelected`, add a branch before `else`:

```kotlin
            R.id.changeSearchDocuments -> {
                lifecycleScope.launch { showDocumentSelector() }
                true
            }
```

Add the `showDocumentSelector` method:

```kotlin
    private suspend fun showDocumentSelector() {
        val candidates = candidateSearchDocuments(isStrongsSearch, allBibles())
        if (candidates.isEmpty()) return

        val selected = Dialogs.multiselect(
            context = this,
            title = getString(R.string.choose_translations),
            items = candidates,
            itemToString = { book ->
                if (book.indexStatus == IndexStatus.DONE) "${book.abbreviation} - ${book.name}"
                else "${book.abbreviation} - ${book.name} (${getString(R.string.search_index_not_created)})"
            },
            preSelected = { selectedTranslations.contains(it.initials) }
        )
        // Empty result = cancelled / "select all" / nothing picked -> no change.
        if (selected.isEmpty()) return

        // An unindexed document must be indexed before it can be searched (mirrors Search.onSearch).
        val unindexed = selected.filter { it.indexStatus != IndexStatus.DONE }
        if (unindexed.isNotEmpty()) {
            startActivity(Intent(this, SearchIndex::class.java).apply {
                putExtra(SearchControl.SEARCH_DOCUMENT, unindexed.first().initials)
            })
            return
        }

        selectedTranslations = selected.map { it.initials }
        updateDocumentSelectorTitle()
        prepareResults()
    }
```

- [ ] **Step 5: Refresh the selector label after each search**

At the end of `fetchSearchResults`' success `withContext(Dispatchers.Main)` block (right after setting `supportActionBar?.title`), add:

```kotlin
                updateDocumentSelectorTitle()
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full unit-test suite (no regressions)**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.search.*" -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (includes `SearchDocumentFilterTest` and `SearchItemAdapterTest`).

- [ ] **Step 8: Manual verification (device/emulator)**

No unit test covers the live search re-run (it needs an indexed Bible module, which is CI-only). Verify manually:
1. Open a Bible with Strong's numbers, tap a Strong's number → dictionary → "Find all occurrences". Results open; the toolbar shows the current translation (e.g. `KJV`).
2. Tap the toolbar document button → the chooser lists **only Strong's-enabled Bibles**. Pick a different one → results re-run and the label updates.
3. Do a normal search from the search screen → in results, tap the document button → the chooser lists **all Bibles** and adding one re-runs the search.
4. Verify appearance in dark, light, and monochrome (e-ink) themes — the icon is a standard action-bar icon and must render in all.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/menu/search_results_actionbar_menu.xml \
        app/src/main/java/net/bible/android/view/activity/search/SearchResults.kt
git commit -m "Add document selector to search results toolbar (OSTicket 3361)"
```

---

### Task 4: Remove dead `TARGET_DOCUMENT` (separate cleanup)

`SearchControl.TARGET_DOCUMENT` is written in three places but read nowhere; the display translation is driven entirely by `SELECTED_TRANSLATIONS` / `SEARCH_DOCUMENT`. Remove it. Independent of the feature.

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/search/SearchControl.kt` (remove the constant)
- Modify: `app/src/main/java/net/bible/android/control/link/LinkControl.kt` (remove the `TARGET_DOCUMENT` put)
- Modify: `app/src/main/java/net/bible/android/view/activity/search/Search.kt` (remove the `TARGET_DOCUMENT` put)
- Modify: `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt` (remove the `TARGET_DOCUMENT` put)

- [ ] **Step 1: Confirm there are no readers**

Run: `grep -rn "TARGET_DOCUMENT\|TargetDocument" app/src/main/java`
Expected: only the four write/definition sites above — no `getStringExtra(... TARGET_DOCUMENT ...)`. If any reader exists, STOP and do not remove.

- [ ] **Step 2: Remove the constant and its three write sites**

Delete `const val TARGET_DOCUMENT = "TargetDocument"` from `SearchControl.kt`, and delete the single line that puts `SearchControl.TARGET_DOCUMENT` in each of `LinkControl.kt` (~line 368), `Search.kt` (~line 343), and `BibleView.kt` (~line 426).

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL, no unresolved `TARGET_DOCUMENT` references.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/control/search/SearchControl.kt \
        app/src/main/java/net/bible/android/control/link/LinkControl.kt \
        app/src/main/java/net/bible/android/view/activity/search/Search.kt \
        app/src/main/java/net/bible/android/view/activity/page/BibleView.kt
git commit -m "Remove unused TARGET_DOCUMENT search intent extra"
```

---

## Notes

- The results-screen selector is a **live override for both flows**: it updates the in-memory `selectedTranslations` and re-runs the search, but does not persist to `search_selected_translations`. So it never mutates the search screen's saved default (avoiding the primary-document ordering subtleties in `Search.ensurePrimaryDocumentFirst`). The search screen keeps persisting its own selection as before.
- If only one candidate Bible exists, the selector still shows and the chooser lists one item — harmless.
