# Document Sync Testing Corrections — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply five testing-found corrections to the document-sync management view and wiring: a Download-Documents-style filter bar (name + status + category), exclusion of virtual MyDocuments, deferring sync-enable to "Start syncing", renaming/hiding the "Select" overflow item, and lowercasing the cloud folder name.

**Architecture:** Pure-Kotlin filtering logic is extracted into a top-level testable function; the manifest (`DocumentSyncMeta`) gains a `category` field so cloud-only documents can be category-filtered; the rest is Android Activity/Fragment/layout wiring verified by compilation and manual on-device testing.

**Tech Stack:** Kotlin, Android (AppCompat, Preference, RecyclerView), JSword (`BookCategory`), kotlinx.serialization, JUnit unit tests.

**Spec:** `docs/superpowers/specs/2026-06-26-document-sync-corrections-design.md`

## Global Constraints

- Kotlin/Java: import classes and use simple names; no fully-qualified names in code.
- New files use the 2026 "Sykerö Software / Tuomas Airaksinen and the AndBible contributors" copyright header (no "Martin Denham"); when editing an existing file, do not change unrelated content.
- All user-facing strings go through `strings.xml` (English only); never hardcode UI text.
- UI must work in dark, light, monochrome/e-ink, and no-animations modes (no colour-only signalling).
- Gradle runs in this environment require `dangerouslyDisableSandbox: true`; unit tests do not need the JS bundle — append `-x npmInstall -x npmUpgrade -x jsBuild`.
- Kotlin-only change → no Vue tests.
- Commit after each task. End commit messages with the session trailer:
  `Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8`

---

## File Structure

- `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncMeta.kt` — add `category` field (Task 1).
- `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt` — exclude MyDocuments, carry/parse `category` (Task 2).
- `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` — extract `filterCloudDocuments` + `CloudDocFilter` (Task 3), new filter bar wiring + overflow hide + setup-enable (Tasks 4, 5, 6).
- `app/src/main/res/layout/activity_cloud_documents.xml` — replace radio group with filter row (Task 4).
- `app/src/main/res/values/arrays.xml` — status filter array (Task 4).
- `app/src/main/res/values/strings.xml` — rename `cloud_doc_select` (Task 5).
- `app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt` — auto/manual enable + switch refresh (Task 6).
- `app/src/main/java/net/bible/service/cloudsync/CloudSync.kt` — lowercase folder suffix (Task 7).
- `app/src/main/java/net/bible/android/control/backup/BackupControl.kt` + `app/src/main/java/net/bible/android/control/page/CurrentPageBase.kt` — cleanups (Task 8).
- Tests: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncMetaTest.kt` (Task 1), new `DocumentCategoryTest.kt` (Task 2), new `CloudDocumentsFilterTest.kt` (Task 3).

---

## Task 1: Add `category` to `DocumentSyncMeta`

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncMeta.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncMetaTest.kt`

**Interfaces:**
- Produces: `DocumentSyncMeta` gains `val category: String = ""` (the `BookCategory` enum-constant name, e.g. `"BIBLE"`; default `""` when absent).

- [ ] **Step 1: Write the failing tests**

Add to `DocumentSyncMetaTest.kt` (inside the existing test class):

```kotlin
@Test fun categoryRoundTrips() {
    val meta = DocumentSyncMeta(
        initials = "KJV", name = "King James", documentType = DocumentType.SWORD,
        version = "2.6", size = 100, language = "en", sourceDevice = "dev1",
        timestamp = 123L, category = "BIBLE",
    )
    val parsed = DocumentSyncMeta.fromJson(meta.toJson())
    assertEquals("BIBLE", parsed.category)
}

@Test fun categoryDefaultsToEmptyWhenMissing() {
    // Old-client JSON without a "category" key must still parse.
    val json = """{"initials":"KJV","name":"King James","documentType":"SWORD",
        "version":"2.6","size":100,"language":"en","sourceDevice":"dev1","timestamp":123}"""
    val parsed = DocumentSyncMeta.fromJson(json)
    assertEquals("", parsed.category)
}
```

Ensure `import org.junit.Assert.assertEquals` and `import org.junit.Test` are present (the file already uses them).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentSyncMetaTest" -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: compile error / FAIL — `category` is not a parameter of `DocumentSyncMeta`.

- [ ] **Step 3: Add the field**

In `DocumentSyncMeta.kt`, add `category` to the data class (after `language`, before `sourceDevice` for readability; order is irrelevant to JSON):

```kotlin
@Serializable
data class DocumentSyncMeta(
    val initials: String,
    val name: String,
    val documentType: DocumentType,
    val version: String,
    val size: Long,
    val language: String,
    val category: String = "",
    val sourceDevice: String,
    val timestamp: Long,
    val cipherKey: String? = null,
    val deleted: Boolean = false,
) {
```

Note: a non-default parameter (`sourceDevice`, `timestamp`) cannot follow a default one in Kotlin construction by position, but all existing call sites use named arguments, so this compiles. Verify in Step 5; if any positional call site breaks, place `category` last among the defaulted params instead (after `deleted`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentSyncMetaTest" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncMeta.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncMetaTest.kt
git commit -m "Add category field to DocumentSyncMeta

Stores the BookCategory enum name so cloud-only documents can be
category-filtered in the management view. Defaults to empty for
backward compatibility with metas written by older clients.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Task 2: Exclude MyDocuments and carry category in `DocumentSync`

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentCategoryTest.kt` (create)

**Interfaces:**
- Consumes: `DocumentSyncMeta.category: String` (Task 1).
- Produces:
  - `DocumentSync.DocumentStatusItem` gains `val category: BookCategory?` (last constructor parameter).
  - Top-level `fun parseCategoryName(name: String?): BookCategory?` in package `net.bible.service.cloudsync.documents` — returns the `BookCategory` for a valid enum name, or `null` for null/blank/unknown.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/service/cloudsync/documents/DocumentCategoryTest.kt`:

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

package net.bible.service.cloudsync.documents

import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentCategoryTest {
    @Test fun parsesValidEnumName() {
        assertEquals(BookCategory.BIBLE, parseCategoryName("BIBLE"))
        assertEquals(BookCategory.GENERAL_BOOK, parseCategoryName("GENERAL_BOOK"))
    }

    @Test fun returnsNullForBlankOrNull() {
        assertNull(parseCategoryName(null))
        assertNull(parseCategoryName(""))
        assertNull(parseCategoryName("   "))
    }

    @Test fun returnsNullForUnknownName() {
        assertNull(parseCategoryName("NOT_A_CATEGORY"))
        assertNull(parseCategoryName("Biblical Texts")) // display name, not enum name
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentCategoryTest" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: compile error — `parseCategoryName` is unresolved.

- [ ] **Step 3: Implement `parseCategoryName`, exclude MyDocuments, add category**

In `DocumentSync.kt`:

Add imports near the existing ones:
```kotlin
import net.bible.service.sword.mydocument.isMyDocument
import org.crosswire.jsword.book.BookCategory
```

Add the top-level function (outside the `object DocumentSync`, e.g. at the bottom of the file):
```kotlin
/** Parses a stored BookCategory enum name; null for null/blank/unknown names. */
fun parseCategoryName(name: String?): BookCategory? =
    name?.takeIf { it.isNotBlank() }?.let { runCatching { BookCategory.valueOf(it) }.getOrNull() }
```

Change the syncable-books filter to also drop MyDocuments:
```kotlin
private fun installedSyncableBooks(): List<Book> =
    Books.installed().books.filter { !it.isPseudoBook && !it.isMyDocument }
```

Add `category` to `DocumentStatusItem` (as the last field):
```kotlin
data class DocumentStatusItem(
    val initials: String,
    val name: String,
    val type: DocumentType,
    val cloudVersion: String?,
    val localVersion: String?,
    val cloudOnly: Boolean,
    val localOnly: Boolean,
    val updateAvailable: Boolean,
    val blocked: Boolean,
    val sizeBytes: Long,
    val category: BookCategory?,
)
```

Populate it in `scan()` (local book category wins; otherwise parse the cloud meta's category). Inside the `allInitials.map { ... }` block, after `val update = ...`, add:
```kotlin
val category = b?.bookCategory ?: parseCategoryName(c?.category)
```
and add `category = category,` as the last argument of the `DocumentStatusItem(...)` constructor call.

In `pushDocument(book)`, add the category to the `DocumentSyncMeta(...)` construction (after `language = book.language.code,`):
```kotlin
category = book.bookCategory.name,
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentCategoryTest" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: PASS. (This also compiles `DocumentSync.kt`, confirming the new field/filter compile.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentCategoryTest.kt
git commit -m "Exclude MyDocuments from document sync; carry book category

MyDocuments (AI Documents and user documents) are DB-backed and sync via
the AI/DB sync, so filter them out of installedSyncableBooks via
Book.isMyDocument. Add category to DocumentStatusItem (from the local
book or the cloud meta) and write it on push, so cloud-only documents can
be category-filtered.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Task 3: Extract `filterCloudDocuments` pure function

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`
- Test: `app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt` (create)

**Interfaces:**
- Consumes: `DocumentStatusItem` incl. `category` (Task 2).
- Produces:
  - Top-level `enum class CloudDocFilter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED }` in package `net.bible.android.view.activity.cloud` (order must match the status spinner array in Task 4).
  - Top-level `fun filterCloudDocuments(items: List<DocumentStatusItem>, status: CloudDocFilter, nameQuery: String, category: BookCategory?): List<DocumentStatusItem>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt`:

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

package net.bible.android.view.activity.cloud

import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import net.bible.service.cloudsync.documents.DocumentType
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDocumentsFilterTest {
    private fun item(
        initials: String,
        name: String = initials,
        cloudOnly: Boolean = false,
        localOnly: Boolean = false,
        updateAvailable: Boolean = false,
        blocked: Boolean = false,
        category: BookCategory? = BookCategory.BIBLE,
    ) = DocumentStatusItem(
        initials = initials, name = name, type = DocumentType.SWORD,
        cloudVersion = "1.0", localVersion = "1.0",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = updateAvailable,
        blocked = blocked, sizeBytes = 0, category = category,
    )

    private val items = listOf(
        item("KJV", name = "King James", localOnly = true, category = BookCategory.BIBLE),
        item("ESV", name = "English Standard", cloudOnly = true, category = BookCategory.BIBLE),
        item("MHC", name = "Matthew Henry", updateAvailable = true, category = BookCategory.COMMENTARY),
        item("STRONGS", name = "Strongs", blocked = true, category = BookCategory.DICTIONARY),
        item("NOCAT", name = "Unknown", cloudOnly = true, category = null),
    )

    @Test fun allStatusNoQueryReturnsEverything() {
        assertEquals(5, filterCloudDocuments(items, CloudDocFilter.ALL, "", null).size)
    }

    @Test fun installedExcludesCloudOnly() {
        val r = filterCloudDocuments(items, CloudDocFilter.INSTALLED, "", null)
        assertEquals(listOf("KJV", "MHC", "STRONGS"), r.map { it.initials })
    }

    @Test fun cloudExcludesLocalOnly() {
        val r = filterCloudDocuments(items, CloudDocFilter.CLOUD, "", null)
        assertEquals(listOf("ESV", "MHC", "STRONGS", "NOCAT"), r.map { it.initials })
    }

    @Test fun updatesAndBlocked() {
        assertEquals(listOf("MHC"), filterCloudDocuments(items, CloudDocFilter.UPDATES, "", null).map { it.initials })
        assertEquals(listOf("STRONGS"), filterCloudDocuments(items, CloudDocFilter.BLOCKED, "", null).map { it.initials })
    }

    @Test fun nameQueryIsCaseInsensitiveSubstring() {
        assertEquals(listOf("KJV"), filterCloudDocuments(items, CloudDocFilter.ALL, "james", null).map { it.initials })
    }

    @Test fun categoryFilterMatchesExactCategory() {
        assertEquals(listOf("KJV", "ESV"), filterCloudDocuments(items, CloudDocFilter.ALL, "", BookCategory.BIBLE).map { it.initials })
    }

    @Test fun nullCategoryItemMatchesOnlyAllCategory() {
        // Filtering by a concrete category never includes the null-category item.
        assertEquals(emptyList<String>(), filterCloudDocuments(items, CloudDocFilter.ALL, "", BookCategory.MAPS).map { it.initials })
    }

    @Test fun combinesStatusNameAndCategory() {
        val r = filterCloudDocuments(items, CloudDocFilter.CLOUD, "english", BookCategory.BIBLE)
        assertEquals(listOf("ESV"), r.map { it.initials })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsFilterTest" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: compile error — `CloudDocFilter` / `filterCloudDocuments` unresolved.

- [ ] **Step 3: Extract enum + function**

In `CloudDocumentsActivity.kt`:

Remove the private nested enum line:
```kotlin
    private enum class Filter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED }
```

Add at the top level of the file (after the imports, before the class), and add `import org.crosswire.jsword.book.BookCategory`:
```kotlin
enum class CloudDocFilter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED }

/**
 * Pure filter used by the cloud documents management view: keeps items matching the
 * selected status, a case-insensitive name substring, and (when non-null) an exact
 * book category. A null [category] argument means "any category"; an item whose own
 * category is null only matches when [category] is null.
 */
fun filterCloudDocuments(
    items: List<DocumentSync.DocumentStatusItem>,
    status: CloudDocFilter,
    nameQuery: String,
    category: BookCategory?,
): List<DocumentSync.DocumentStatusItem> {
    val query = nameQuery.trim()
    return items.filter { item ->
        val statusOk = when (status) {
            CloudDocFilter.ALL -> true
            CloudDocFilter.INSTALLED -> !item.cloudOnly
            CloudDocFilter.CLOUD -> !item.localOnly
            CloudDocFilter.UPDATES -> item.updateAvailable
            CloudDocFilter.BLOCKED -> item.blocked
        }
        val nameOk = query.isEmpty() || item.name.contains(query, ignoreCase = true)
        val categoryOk = category == null || item.category == category
        statusOk && nameOk && categoryOk
    }
}
```

This temporarily breaks the activity's own references to `Filter` / `filter` — those are rewritten in Task 4. To keep this task compiling on its own, also update the activity body now: replace the field `private var filter: Filter = Filter.ALL` with `private var filter: CloudDocFilter = CloudDocFilter.ALL`, and in `applyFilter()` replace the `when (filter) { Filter.ALL ... }` arms with `CloudDocFilter.ALL` etc., and in the `binding.filters.setOnCheckedChangeListener` block replace `Filter.INSTALLED`→`CloudDocFilter.INSTALLED` (and the rest) and the `else -> Filter.ALL`→`else -> CloudDocFilter.ALL`. Leave the radio-group wiring otherwise intact (Task 4 replaces it).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsFilterTest" -x npmInstall -x npmUpgrade -x jsBuild`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt
git commit -m "Extract pure filterCloudDocuments function with tests

Moves the management-view filtering into a top-level, unit-tested
function combining status, case-insensitive name substring, and book
category. Prepares for the new three-control filter bar.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Task 4: New filter bar (name + status + category)

**Files:**
- Modify: `app/src/main/res/layout/activity_cloud_documents.xml`
- Modify: `app/src/main/res/values/arrays.xml`
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`

**Interfaces:**
- Consumes: `CloudDocFilter`, `filterCloudDocuments` (Task 3); `@array/documentTypes` (existing); `@string/free_text_search_documents` (existing).
- Produces: new view ids `nameSearch`, `statusSpinner`, `categorySpinner`; a private `categoryForSpinnerPosition(pos: Int): BookCategory?`.

- [ ] **Step 1: Add the status filter array**

In `app/src/main/res/values/arrays.xml`, add (near the existing `documentTypes` array):

```xml
	<string-array name="cloud_doc_status_filters">
		<item>@string/cloud_doc_filter_all</item>
		<item>@string/cloud_doc_filter_installed</item>
		<item>@string/cloud_doc_filter_cloud</item>
		<item>@string/cloud_doc_filter_updates</item>
		<item>@string/cloud_doc_filter_blocked</item>
	</string-array>
```

- [ ] **Step 2: Replace the filter row in the layout**

In `app/src/main/res/layout/activity_cloud_documents.xml`, replace the entire `<HorizontalScrollView>…</HorizontalScrollView>` block (the one containing `RadioGroup android:id="@+id/filters"`) with:

```xml
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:baselineAligned="false"
        android:paddingHorizontal="8dp"
        android:paddingVertical="4dp">

        <EditText
            android:id="@+id/nameSearch"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="@string/free_text_search_documents"
            android:importantForAutofill="no"
            android:inputType="text"
            android:maxLines="1" />

        <Spinner
            android:id="@+id/statusSpinner"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_gravity="center_vertical"
            android:layout_weight="1"
            android:entries="@array/cloud_doc_status_filters" />

        <Spinner
            android:id="@+id/categorySpinner"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_gravity="center_vertical"
            android:layout_weight="1"
            android:entries="@array/documentTypes" />
    </LinearLayout>
```

- [ ] **Step 3: Rewire the activity to the new controls**

In `CloudDocumentsActivity.kt`:

Add imports:
```kotlin
import android.view.View
import android.widget.AdapterView
import androidx.core.widget.addTextChangedListener
import org.crosswire.jsword.book.BookCategory
```
(`android.view.View` is already imported; keep one import only.)

Remove the now-unused `private var filter: CloudDocFilter = CloudDocFilter.ALL` field.

Replace the old radio-group wiring in `onCreate` — delete:
```kotlin
        binding.filters.setOnCheckedChangeListener { _, checkedId ->
            filter = when (checkedId) {
                R.id.filterInstalled -> CloudDocFilter.INSTALLED
                R.id.filterCloud -> CloudDocFilter.CLOUD
                R.id.filterUpdates -> CloudDocFilter.UPDATES
                R.id.filterBlocked -> CloudDocFilter.BLOCKED
                else -> CloudDocFilter.ALL
            }
            applyFilter()
        }
```
and replace with:
```kotlin
        val filterSelectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = applyFilter()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.statusSpinner.onItemSelectedListener = filterSelectionListener
        binding.categorySpinner.onItemSelectedListener = filterSelectionListener
        binding.nameSearch.addTextChangedListener(afterTextChanged = { applyFilter() })
```

Add the spinner-position → category mapping as a private method:
```kotlin
    /** Maps the category spinner position (the @array/documentTypes order) to a BookCategory; 0 = All. */
    private fun categoryForSpinnerPosition(pos: Int): BookCategory? = when (pos) {
        1 -> BookCategory.BIBLE
        2 -> BookCategory.COMMENTARY
        3 -> BookCategory.DICTIONARY
        4 -> BookCategory.GENERAL_BOOK
        5 -> BookCategory.MAPS
        6 -> BookCategory.AND_BIBLE
        else -> null
    }
```

Replace the body of `applyFilter()`:
```kotlin
    private fun applyFilter() {
        if (adapter.isSelectionMode()) exitSelectionMode()
        val status = CloudDocFilter.entries[binding.statusSpinner.selectedItemPosition.coerceIn(0, CloudDocFilter.entries.lastIndex)]
        val category = categoryForSpinnerPosition(binding.categorySpinner.selectedItemPosition)
        val name = binding.nameSearch.text?.toString().orEmpty()
        val filtered = filterCloudDocuments(allItems, status, name, category)
        adapter.submit(filtered)
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew testStandardGoogleplayDebugUnitTest -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL — main sources compile (the obsolete `binding.filters` / `R.id.filter*` references are gone). Existing unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_cloud_documents.xml app/src/main/res/values/arrays.xml app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt
git commit -m "Replace cloud documents radio filters with name+status+category bar

Adopts the Download Documents filter-bar look: a name search, a status
spinner, and a document-category spinner (reusing @array/documentTypes).
Drives the extracted filterCloudDocuments function.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Task 5: "Select" → "Select multiple", hidden in selection mode

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`

**Interfaces:**
- Consumes: existing `MENU_SELECT` id, `adapter.isSelectionMode()`.

- [ ] **Step 1: Rename the string**

In `app/src/main/res/values/strings.xml`, change:
```xml
    <string name="cloud_doc_select">Select</string>
```
to:
```xml
    <string name="cloud_doc_select">Select multiple</string>
```

- [ ] **Step 2: Hide the item when already selecting, and refresh the menu on mode changes**

In `CloudDocumentsActivity.kt`, add an `onPrepareOptionsMenu` override (next to `onCreateOptionsMenu`):
```kotlin
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_SELECT)?.isVisible = !adapter.isSelectionMode()
        return super.onPrepareOptionsMenu(menu)
    }
```

Ensure the menu is re-evaluated whenever selection mode toggles. Add `invalidateOptionsMenu()` at the end of each of these methods: `enterSelectionMode()`, `exitSelectionMode()`, and `enterSetupMode()`.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew testStandardGoogleplayDebugUnitTest -x npmInstall -x npmUpgrade -x jsBuild`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt
git commit -m "Rename Select to Select multiple and hide it while selecting

The overflow item enters bulk multi-select; it is a no-op when the list
is already in selection mode (e.g. setup mode), so hide it there and give
it a clearer label.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Task 6: Enable sync only on "Start syncing"

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt`

**Interfaces:**
- Consumes: `DocumentSyncSettings.enabled`, `DocumentSyncSettings.automatic`, `CloudDocumentsActivity.EXTRA_SETUP_MODE`.

- [ ] **Step 1: Set enabled when "Start syncing" runs**

In `CloudDocumentsActivity.kt`, in `performSetupSync()`, set the master flag as the first statement (the commit point), before computing the selections:
```kotlin
    private fun performSetupSync() {
        // "Start syncing" is the commit point: enable document sync now (auto mode
        // defers enabling until here, so backing out of setup leaves sync off).
        DocumentSyncSettings.enabled = true
        val selected = adapter.getSelectedInitials()
        // ... rest unchanged ...
```

- [ ] **Step 2: Defer enabling in auto mode; refresh the switch on return**

In `SyncSettings.kt`, replace the `sync_enable_documents` preference-change body (currently sets `DocumentSyncSettings.enabled = true` then launches setup then `activity?.recreate()`) with:

```kotlin
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")!!.run {
            setOnPreferenceChangeListener { _, newValue ->
                val enable = newValue as Boolean
                if (enable) {
                    lifecycleScope.launch {
                        var signedIn = CloudSync.signedIn
                        if (!signedIn) signedIn = CloudSync.signIn(activity as ActivityBase) == true
                        if (signedIn) {
                            if (DocumentSyncSettings.automatic) {
                                // Auto mode: do NOT enable yet — CloudDocumentsActivity's
                                // "Start syncing" performs the commit. Backing out leaves it off.
                                startActivity(
                                    Intent(requireContext(), CloudDocumentsActivity::class.java)
                                        .putExtra(CloudDocumentsActivity.EXTRA_SETUP_MODE, true)
                                )
                            } else {
                                // Manual mode: no Start CTA, so enabling the switch is the commit.
                                DocumentSyncSettings.enabled = true
                                startActivity(
                                    Intent(requireContext(), CloudDocumentsActivity::class.java)
                                        .putExtra(CloudDocumentsActivity.EXTRA_SETUP_MODE, false)
                                )
                            }
                        }
                    }
                    // Never flip the switch on optimistically; onResume reflects the real state
                    // once the user returns from the management/setup screen.
                    false
                } else {
                    DocumentSyncSettings.enabled = false
                    true
                }
            }
        }
```

Add an `onResume` override to `SyncSettingsFragment` so the switch reflects the committed state when returning from the setup screen:
```kotlin
    override fun onResume() {
        super.onResume()
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")?.isChecked =
            DocumentSyncSettings.enabled
    }
```
(If `SyncSettingsFragment` already overrides `onResume`, add the line into the existing override instead of creating a second one.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew testStandardGoogleplayDebugUnitTest -x npmInstall -x npmUpgrade -x jsBuild`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification note**

Record in the commit body that this needs on-device verification: (a) auto mode → toggle on, sign in, setup screen opens, press back without Start → switch returns to off and `DocumentSyncSettings.enabled` is false; (b) auto mode → press "Start syncing" → returning shows the switch on; (c) manual mode → toggle on enables immediately and opens the management view.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt
git commit -m "Enable document sync only on Start syncing (auto mode)

Auto mode no longer flips the master switch on when toggled; the
'Start syncing' action in the setup view is the commit point. Backing out
of setup leaves sync off. Manual mode (no Start CTA) still enables on
toggle. The settings switch reflects the committed state on resume.

Needs on-device verification of the three flows.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Task 7: Lowercase the cloud folder name

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/CloudSync.kt:106`

**Interfaces:** none.

- [ ] **Step 1: Lowercase the suffix**

In `CloudSync.kt`, change:
```kotlin
    const val DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX = "DOCUMENTS"
```
to:
```kotlin
    const val DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX = "documents"
```
The folder name becomes `${packageName}-sync-documents`, matching the other categories. No migration (pre-release; the old `…-sync-DOCUMENTS` folder is orphaned).

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew testStandardGoogleplayDebugUnitTest -x npmInstall -x npmUpgrade -x jsBuild`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/CloudSync.kt
git commit -m "Lowercase document sync cloud folder name

Use ...-sync-documents to match the other sync folders (...-sync-bookmarks
etc.). No migration: pre-release, the old uppercase folder is orphaned.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Task 8: Cleanups — exclude MyDocuments from module backup, remove dead import

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/backup/BackupControl.kt`
- Modify: `app/src/main/java/net/bible/android/control/page/CurrentPageBase.kt`

**Interfaces:**
- Consumes: `Book.isMyDocument` (existing extension in `net.bible.service.sword.mydocument`).

- [ ] **Step 1: Exclude MyDocuments from the module backup list**

In `BackupControl.kt`, add the import (with the other imports):
```kotlin
import net.bible.service.sword.mydocument.isMyDocument
```
Change line 468:
```kotlin
            Books.installed().books.filter { !it.isPseudoBook }.sortedBy { it.language }
```
to:
```kotlin
            Books.installed().books.filter { !it.isPseudoBook && !it.isMyDocument }.sortedBy { it.language }
```
(MyDocuments are DB-backed and belong to the DB backup, not the SWORD-module backup.)

- [ ] **Step 2: Remove the dead import**

In `CurrentPageBase.kt`, delete the unused import line (the file uses `isMyDocument`, not `isPseudoBook`):
```kotlin
import net.bible.service.download.isPseudoBook
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew testStandardGoogleplayDebugUnitTest -x npmInstall -x npmUpgrade -x jsBuild`
Expected: BUILD SUCCESSFUL (no "unused import" failure, no broken reference).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/control/backup/BackupControl.kt app/src/main/java/net/bible/android/control/page/CurrentPageBase.kt
git commit -m "Exclude MyDocuments from module backup; drop dead import

MyDocuments are DB-backed, so omit them from the SWORD-module backup
multiselect (they are covered by the DB backup). Remove the unused
isPseudoBook import in CurrentPageBase.

Claude-Session: https://claude.ai/code/session_014x221isQ5jmECjwXUbLcU8"
```

---

## Final verification (after all tasks)

- [ ] Run the document-sync unit tests:
  `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.*" --tests "net.bible.android.view.activity.cloud.*" -x npmInstall -x npmUpgrade -x jsBuild`
  Expected: all pass.
- [ ] Build and install the debug APK for on-device testing:
  `./gradlew assembleStandardGithubDebug` then `adb install -r app/build/outputs/apk/standardGithub/debug/app-standard-github-debug.apk`.
- [ ] Manually verify on device: (1) filter bar (name/status/category) filters correctly and AI Documents no longer appears; (2) "Select multiple" hidden in setup mode, present in the normal view; (3) the three enable-on-Start flows from Task 6; (4) cloud folder is created as `…-sync-documents`.
