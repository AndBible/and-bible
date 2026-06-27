# Document Sync — Optional Display of Removed Documents — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the Synced Documents view optionally show cloud tombstones (removed documents), with per-row Restore (re-push a still-installed local copy) and Purge (delete the tombstone) actions, gated by a 3-dot-menu toggle and a conditional status filter.

**Architecture:** A pure `assembleStatusItems` function (extracted from the Android-coupled `buildStatusItems`) gains an `includeDeleted` flag and a `cloudDeleted` output field, so the inclusion/flagging logic is unit-testable. The activity threads the persisted toggle through `scan`/`scanCached`, rebuilds the status spinner to add a trailing "Removed" filter only while the toggle is on, and the per-row menu offers Restore/Purge for tombstone rows. Restore reuses the existing push path; Purge calls a new `DocumentStore.deleteDocument` via `DocumentSync.purgeTombstone`.

**Tech Stack:** Kotlin, Android, JUnit (plain, no Robolectric for the pure logic), Gradle.

## Global Constraints

- Java/Kotlin: import classes and use simple names; never fully-qualified names in code.
- New files use the 2026 copyright header (Sykerö Software / Tuomas Airaksinen and the AndBible contributors; no "Martin Denham").
- All user-facing text goes through `app/src/main/res/values/strings.xml`. English only — no other-language translations.
- E-ink / monochrome safe: status conveyed by text, never colour alone.
- Run unit tests with `dangerouslyDisableSandbox: true` (Gradle daemon does not work in the sandbox): `./gradlew testStandardGoogleplayDebugUnitTest`.
- Commit each task separately after its verification passes.

---

### Task 1: Data model — `cloudDeleted` field + pure `assembleStatusItems`

Extract the status-row assembly into a pure, testable function with an `includeDeleted` flag, add the `cloudDeleted` output field, and thread the flag through `scan` / `scanCached`. Default `includeDeleted = false` preserves current behaviour exactly.

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/AssembleStatusItemsTest.kt` (create)

**Interfaces:**
- Produces:
  - `DocumentSync.DocumentStatusItem` gains `val cloudDeleted: Boolean = false` (last field, defaulted).
  - `data class LocalDoc(val name: String, val version: String, val category: BookCategory, val type: DocumentType, val canDelete: Boolean, val installSizeBytes: Long?)` (top-level in `DocumentSync.kt`).
  - `fun assembleStatusItems(cloudMetas: List<DocumentSyncMeta>, localDocs: Map<String, LocalDoc>, blocked: Set<String>, includeDeleted: Boolean): List<DocumentSync.DocumentStatusItem>` (top-level, pure).
  - `DocumentSync.scan(includeDeleted: Boolean = false)` and `DocumentSync.scanCached(includeDeleted: Boolean = false)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/service/cloudsync/documents/AssembleStatusItemsTest.kt`:

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
import org.junit.Test

class AssembleStatusItemsTest {
    private fun meta(deleted: Boolean = false, version: String = "1.0") = DocumentSyncMeta(
        initials = "KJV", name = "KJV", documentType = DocumentType.SWORD, version = version,
        size = 100, language = "en", category = "BIBLE", sourceDevice = "dev", timestamp = 1L,
        deleted = deleted,
    )

    private fun local(version: String = "1.0") = LocalDoc(
        name = "KJV", version = version, category = BookCategory.BIBLE,
        type = DocumentType.SWORD, canDelete = true, installSizeBytes = null,
    )

    @Test fun excludesTombstoneByDefault() {
        val items = assembleStatusItems(listOf(meta(deleted = true)), emptyMap(), emptySet(), includeDeleted = false)
        assertEquals(emptyList<DocumentSync.DocumentStatusItem>(), items)
    }

    @Test fun includesTombstoneWhenRequested() {
        val row = assembleStatusItems(listOf(meta(deleted = true)), emptyMap(), emptySet(), includeDeleted = true).single()
        assertEquals(true, row.cloudDeleted)
        assertEquals(false, row.cloudOnly)
        assertEquals(false, row.localOnly)
        assertEquals("1.0", row.cloudVersion)
    }

    @Test fun tombstoneWithLocalCopyIsLocalOnlyAndDeleted() {
        val row = assembleStatusItems(
            listOf(meta(deleted = true)), mapOf("KJV" to local()), emptySet(), includeDeleted = true,
        ).single()
        assertEquals(true, row.cloudDeleted)
        assertEquals(true, row.localOnly)
        assertEquals(false, row.cloudOnly)
        assertEquals(false, row.updateAvailable)
        assertEquals(false, row.localNewer)
    }

    @Test fun tombstoneExcludedKeepsLocalAsLocalOnly() {
        // Toggle off: a tombstoned-but-installed document still shows as a plain local-only row.
        val row = assembleStatusItems(
            listOf(meta(deleted = true)), mapOf("KJV" to local()), emptySet(), includeDeleted = false,
        ).single()
        assertEquals(false, row.cloudDeleted)
        assertEquals(true, row.localOnly)
    }

    @Test fun liveCloudOnlyUnaffected() {
        val row = assembleStatusItems(listOf(meta()), emptyMap(), emptySet(), includeDeleted = true).single()
        assertEquals(false, row.cloudDeleted)
        assertEquals(true, row.cloudOnly)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*AssembleStatusItemsTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL to compile — `assembleStatusItems` and `LocalDoc` unresolved, `cloudDeleted` unresolved.

- [ ] **Step 3: Add the `cloudDeleted` field to `DocumentStatusItem`**

In `DocumentSync.kt`, in the `DocumentStatusItem` data class, add a new last field after `canDeleteLocal`:

```kotlin
        /** Whether the locally installed copy may be deleted (false e.g. for the last Bible). */
        val canDeleteLocal: Boolean,
        /** The cloud meta for this document is a tombstone (removed from the cloud). */
        val cloudDeleted: Boolean = false,
    )
```

- [ ] **Step 4: Add `LocalDoc` and the pure `assembleStatusItems`**

In `DocumentSync.kt`, at the end of the file (next to `parseCategoryName`), add:

```kotlin
/**
 * Local document facts the status list needs, extracted from a [Book] so the assembly logic
 * below stays pure and unit-testable (no Android / JSword dependencies).
 */
data class LocalDoc(
    val name: String,
    val version: String,
    val category: BookCategory,
    val type: DocumentType,
    val canDelete: Boolean,
    val installSizeBytes: Long?,
)

/**
 * Pure builder of the management view's status rows from cloud metas + local documents.
 *
 * When [includeDeleted] is false (the default), cloud tombstones are dropped entirely — a
 * document removed from the cloud but still installed locally then shows as a normal local-only
 * row, exactly as before this feature. When true, tombstones become their own rows: because the
 * archive is gone, a tombstone is treated as "no downloadable cloud copy" for the
 * cloudOnly / update / localNewer computations, and flagged via [DocumentSync.DocumentStatusItem.cloudDeleted].
 */
fun assembleStatusItems(
    cloudMetas: List<DocumentSyncMeta>,
    localDocs: Map<String, LocalDoc>,
    blocked: Set<String>,
    includeDeleted: Boolean,
): List<DocumentSync.DocumentStatusItem> {
    val cloud = (if (includeDeleted) cloudMetas else cloudMetas.filter { !it.deleted })
        .associateBy { it.initials }
    val allInitials = (cloud.keys + localDocs.keys).toSortedSet()
    return allInitials.map { initials ->
        val c = cloud[initials]
        val b = localDocs[initials]
        // A tombstone has no downloadable archive: treat the live cloud copy as absent for the
        // cloudOnly / update / localNewer computations, but keep its meta for display + the flag.
        val liveCloud = c?.takeIf { !it.deleted }
        val localVersion = b?.version
        val update = liveCloud != null && localVersion != null &&
            DocumentSync.versionIsNewer(liveCloud.version, localVersion)
        val localNewer = liveCloud != null && localVersion != null &&
            DocumentSync.versionIsNewer(localVersion, liveCloud.version)
        DocumentSync.DocumentStatusItem(
            initials = initials,
            name = c?.name ?: b?.name ?: initials,
            type = b?.type ?: c?.documentType ?: DocumentType.SWORD,
            cloudVersion = c?.version,
            localVersion = localVersion,
            cloudOnly = liveCloud != null && b == null,
            localOnly = liveCloud == null && b != null,
            updateAvailable = update,
            localNewer = localNewer,
            blocked = initials in blocked,
            sizeBytes = liveCloud?.size ?: b?.installSizeBytes ?: 0L,
            category = b?.category ?: parseCategoryName(c?.category),
            canDeleteLocal = b?.canDelete ?: true,
            cloudDeleted = c?.deleted == true,
        )
    }
}
```

- [ ] **Step 5: Rewrite `buildStatusItems` to delegate to the pure function**

In `DocumentSync.kt`, replace the existing private `buildStatusItems` (the `fun buildStatusItems(...) { ... }` block) with:

```kotlin
    private fun buildStatusItems(
        cloudMetas: List<DocumentSyncMeta>,
        local: Map<String, Book>,
        includeDeleted: Boolean = false,
    ): List<DocumentStatusItem> {
        val localDocs = local.mapValues { (_, b) ->
            LocalDoc(
                name = b.name,
                version = DocumentArchiver.documentVersion(b),
                category = b.bookCategory,
                type = DocumentArchiver.documentTypeOf(b),
                canDelete = b.canDelete,
                installSizeBytes = localInstallSizeBytes(b),
            )
        }
        return assembleStatusItems(cloudMetas, localDocs, DocumentSyncSettings.blockList.all(), includeDeleted)
    }
```

- [ ] **Step 6: Thread `includeDeleted` through `scan` and `scanCached`**

In `DocumentSync.kt`, change the `scan` signature and its final call:

```kotlin
    suspend fun scan(includeDeleted: Boolean = false): List<DocumentStatusItem> {
```
and its last line:
```kotlin
        return buildStatusItems(cloudMetas, local, includeDeleted)
```

Change `scanCached`:
```kotlin
    suspend fun scanCached(includeDeleted: Boolean = false): List<DocumentStatusItem> {
        val cacheDao = DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
        val local = installedSyncableBooks().associateBy { it.initials }
        return buildStatusItems(cacheDao.all().map { it.toMeta() }, local, includeDeleted)
    }
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*AssembleStatusItemsTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (5 tests).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/AssembleStatusItemsTest.kt
git commit -m "Add cloudDeleted flag and pure assembleStatusItems with includeDeleted

Claude-Session: https://claude.ai/code/session_018meoC94oFgiHYvfwFLLQdm"
```

---

### Task 2: Per-row menu actions for tombstone rows (`RESTORE`, `PURGE`)

Add the two new actions to the enum and offer them — and only them — on a tombstone row: Restore only when the document is still installed locally, Purge always.

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt:40` (enum)
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt:80-94` (`documentMenuActions`)
- Test: `app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsMenuTest.kt`

**Interfaces:**
- Consumes: `DocumentStatusItem.cloudDeleted` (Task 1).
- Produces: `CloudDocAction.RESTORE`, `CloudDocAction.PURGE`; `documentMenuActions` returns `[RESTORE, PURGE]` for an installed tombstone and `[PURGE]` for a not-installed tombstone.

- [ ] **Step 1: Write the failing tests**

In `CloudDocumentsMenuTest.kt`, add a `cloudDeleted` parameter to the `item(...)` helper (new last param, defaulted) and three tests. Change the helper signature and the `DocumentStatusItem(...)` call:

```kotlin
    private fun item(
        cloudOnly: Boolean = false, localOnly: Boolean = false,
        update: Boolean = false, localNewer: Boolean = false, blocked: Boolean = false,
        canDeleteLocal: Boolean = true, cloudDeleted: Boolean = false,
    ) = DocumentStatusItem(
        initials = "KJV", name = "KJV", type = DocumentType.SWORD,
        cloudVersion = "1.0", localVersion = "1.0",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = update,
        localNewer = localNewer, blocked = blocked, sizeBytes = 0, category = BookCategory.BIBLE,
        canDeleteLocal = canDeleteLocal, cloudDeleted = cloudDeleted,
    )
```

Add these tests:

```kotlin
    @Test fun tombstoneInstalledLocallyOffersRestoreAndPurge() {
        val actions = documentMenuActions(item(localOnly = true, cloudDeleted = true), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.RESTORE, CloudDocAction.PURGE), actions)
    }

    @Test fun tombstoneNotInstalledOffersOnlyPurge() {
        val actions = documentMenuActions(item(cloudDeleted = true), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.PURGE), actions)
    }

    @Test fun tombstoneNeverOffersDownloadOrBlock() {
        val installed = documentMenuActions(item(localOnly = true, cloudDeleted = true), syncEnabled = false)
        assertEquals(false, installed.contains(CloudDocAction.DOWNLOAD))
        assertEquals(false, installed.contains(CloudDocAction.BLOCK))
        assertEquals(false, installed.contains(CloudDocAction.REMOVE_CLOUD))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*CloudDocumentsMenuTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL to compile — `CloudDocAction.RESTORE` / `CloudDocAction.PURGE` unresolved.

- [ ] **Step 3: Add the enum values**

In `CloudDocumentsAdapter.kt`, change line 40:

```kotlin
enum class CloudDocAction { DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, UNBLOCK, RESTORE, PURGE, TOGGLE_SELECT }
```

- [ ] **Step 4: Add the tombstone branch to `documentMenuActions`**

In `CloudDocumentsActivity.kt`, replace the body of `documentMenuActions` (the `buildList { ... }` block) with:

```kotlin
): List<CloudDocAction> = buildList {
    // A removed (tombstoned) document: the cloud archive is gone, so the only paths forward are
    // re-uploading a still-installed local copy, or purging the tombstone entirely.
    if (item.cloudDeleted) {
        if (item.localOnly) add(CloudDocAction.RESTORE)
        add(CloudDocAction.PURGE)
        return@buildList
    }
    // Download: the cloud has a copy this device lacks or that is newer.
    if (item.cloudOnly || item.updateAvailable) add(CloudDocAction.DOWNLOAD)
    // Push: not in the cloud yet, or the local copy is newer than the cloud copy.
    if (item.localOnly || item.localNewer) add(CloudDocAction.PUSH)
    // Remove: only when a cloud copy exists. With sync enabled it also deletes the local copy,
    // so suppress it when the local copy can't be deleted (e.g. the last Bible).
    if (!item.localOnly && !(syncEnabled && !item.canDeleteLocal)) add(CloudDocAction.REMOVE_CLOUD)
    // Block/unblock the per-device auto-download — only meaningful when a cloud copy exists.
    if (item.blocked) add(CloudDocAction.UNBLOCK)
    else if (!item.localOnly) add(CloudDocAction.BLOCK)
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*CloudDocumentsMenuTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (all existing + 3 new tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt \
        app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsMenuTest.kt
git commit -m "Offer Restore/Purge menu actions for removed (tombstone) documents

Claude-Session: https://claude.ai/code/session_018meoC94oFgiHYvfwFLLQdm"
```

---

### Task 3: `REMOVED` status filter

Add a trailing `REMOVED` filter value that keeps only tombstone rows. Appended last so the spinner-position → enum mapping stays correct when the option is hidden.

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt:46` (enum) and `:54-73` (`filterCloudDocuments`)
- Test: `app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt`

**Interfaces:**
- Consumes: `DocumentStatusItem.cloudDeleted` (Task 1).
- Produces: `CloudDocFilter.REMOVED` (last enum value); `filterCloudDocuments(..., CloudDocFilter.REMOVED, ...)` keeps only `cloudDeleted` items.

- [ ] **Step 1: Write the failing test**

In `CloudDocumentsFilterTest.kt`, add a `cloudDeleted` parameter to the `item(...)` helper (new last param, defaulted) and a removed-item to the list plus a test. Change the helper:

```kotlin
    private fun item(
        initials: String,
        name: String = initials,
        cloudOnly: Boolean = false,
        localOnly: Boolean = false,
        updateAvailable: Boolean = false,
        blocked: Boolean = false,
        category: BookCategory? = BookCategory.BIBLE,
        cloudDeleted: Boolean = false,
    ) = DocumentStatusItem(
        initials = initials, name = name, type = DocumentType.SWORD,
        cloudVersion = "1.0", localVersion = "1.0",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = updateAvailable,
        localNewer = false, blocked = blocked, sizeBytes = 0, category = category,
        canDeleteLocal = true, cloudDeleted = cloudDeleted,
    )
```

Add a removed item to the `items` list:

```kotlin
    private val items = listOf(
        item("KJV", name = "King James", localOnly = true, category = BookCategory.BIBLE),
        item("ESV", name = "English Standard", cloudOnly = true, category = BookCategory.BIBLE),
        item("MHC", name = "Matthew Henry", updateAvailable = true, category = BookCategory.COMMENTARY),
        item("STRONGS", name = "Strongs", blocked = true, category = BookCategory.DICTIONARY),
        item("NOCAT", name = "Unknown", cloudOnly = true, category = null),
        item("GONE", name = "Removed Book", cloudDeleted = true, category = BookCategory.BIBLE),
    )
```

Add a test (and note the count test now expects 6):

```kotlin
    @Test fun removedKeepsOnlyTombstones() {
        assertEquals(listOf("GONE"), filterCloudDocuments(items, CloudDocFilter.REMOVED, "", null).map { it.initials })
    }
```

Update the existing `allStatusNoQueryReturnsEverything` expectation from `5` to `6`:

```kotlin
    @Test fun allStatusNoQueryReturnsEverything() {
        assertEquals(6, filterCloudDocuments(items, CloudDocFilter.ALL, "", null).size)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*CloudDocumentsFilterTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL to compile — `CloudDocFilter.REMOVED` unresolved.

- [ ] **Step 3: Add the enum value**

In `CloudDocumentsActivity.kt`, change line 46:

```kotlin
enum class CloudDocFilter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED, REMOVED }
```

- [ ] **Step 4: Add the filter branch**

In `CloudDocumentsActivity.kt`, in `filterCloudDocuments`, add the `REMOVED` branch to the `when (status)`:

```kotlin
        val statusOk = when (status) {
            CloudDocFilter.ALL -> true
            CloudDocFilter.INSTALLED -> !item.cloudOnly
            CloudDocFilter.CLOUD -> !item.localOnly
            CloudDocFilter.UPDATES -> item.updateAvailable
            CloudDocFilter.BLOCKED -> item.blocked
            CloudDocFilter.REMOVED -> item.cloudDeleted
        }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*CloudDocumentsFilterTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt
git commit -m "Add REMOVED status filter for tombstone documents

Claude-Session: https://claude.ai/code/session_018meoC94oFgiHYvfwFLLQdm"
```

---

### Task 4: Engine — purge a tombstone from the cloud

Add the cloud-folder delete to the store and the `purgeTombstone` entry point. These are I/O paths verified by compilation + the existing unit suite (no new unit test; functional behaviour is verified on-device in Task 5).

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentStore.kt`
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt`

**Interfaces:**
- Produces: `DocumentStore.deleteDocument(initials: String)`; `DocumentSync.purgeTombstone(initials: String)`.

- [ ] **Step 1: Add `deleteDocument` to `DocumentStore`**

In `DocumentStore.kt`, add this method (e.g. after `writeTombstone`):

```kotlin
    /**
     * Permanently deletes a document's entire cloud folder (its meta + any residual archives),
     * keyed by initials. Used to purge a tombstone from the cloud history. A no-op when the
     * folder is absent. Children are deleted first, then the folder, so the removal is explicit
     * regardless of whether the adapter cascades folder deletes.
     */
    suspend fun deleteDocument(initials: String) {
        val folder = folderFor(initials) ?: return
        adapter.listFiles(parentsIds = listOf(folder.id)).forEach { adapter.delete(it.id) }
        adapter.delete(folder.id)
    }
```

- [ ] **Step 2: Add `purgeTombstone` to `DocumentSync`**

In `DocumentSync.kt`, add (e.g. after `removeFromCloud`):

```kotlin
    /**
     * Permanently removes a tombstone (the removed-document marker) from the cloud, so the
     * document no longer appears in the "show removed documents" view. Note: if the document is
     * still installed on another device that has not yet applied the removal, that device may
     * re-upload it on its next sync — the tombstone is the signal that prevents that. Manual
     * action only; bypasses no guards because it touches only this account's cloud store.
     */
    suspend fun purgeTombstone(initials: String) {
        val store = store() ?: return
        store.deleteDocument(initials)
        refreshCache()
    }
```

- [ ] **Step 3: Verify the project still compiles and the existing suite passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentStore.kt \
        app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt
git commit -m "Add purgeTombstone to permanently delete a removed-document marker

Claude-Session: https://claude.ai/code/session_018meoC94oFgiHYvfwFLLQdm"
```

---

### Task 5: Integration — toggle, spinner, actions, dialog, subtitle, strings

Wire everything into the activity: the persisted toggle, the conditional spinner, threading `includeDeleted` through every scan, the Restore/Purge action handling with the purge warning dialog, the tombstone subtitle, and all new strings. This is the UI-wiring task, verified by manual on-device testing (consistent with the rest of the feature).

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt`
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/arrays.xml`
- Modify: `app/src/main/res/layout/activity_cloud_documents.xml`

**Interfaces:**
- Consumes: `DocumentSync.scan(includeDeleted)`, `DocumentSync.scanCached(includeDeleted)`, `DocumentSync.purgeTombstone` (Tasks 1, 4); `CloudDocAction.RESTORE/PURGE` (Task 2); `CloudDocFilter.REMOVED` (Task 3); `DocumentStatusItem.cloudDeleted` (Task 1).
- Produces: `DocumentSyncSettings.showRemovedDocuments`; the in-activity `setupStatusFilter`, `confirmPurge`, `MENU_SHOW_REMOVED` wiring.

- [ ] **Step 1: Add the `showRemovedDocuments` preference**

In `DocumentSyncSettings.kt`, add the key constant alongside the others:

```kotlin
    private const val SHOW_REMOVED = "sync_documents_show_removed"
```

and the property (e.g. after `wifiOnly`):

```kotlin
    // Per-device: whether the management view includes removed (tombstoned) cloud documents.
    var showRemovedDocuments: Boolean
        get() = CommonUtils.settings.getBoolean(SHOW_REMOVED, false)
        set(value) = CommonUtils.settings.setBoolean(SHOW_REMOVED, value)
```

- [ ] **Step 2: Add all new strings**

In `app/src/main/res/values/strings.xml`, next to the existing `cloud_doc_*` strings (around line 1903), add:

```xml
    <string name="cloud_doc_show_removed">Show removed documents</string>
    <string name="cloud_doc_filter_removed">Removed</string>
    <string name="cloud_doc_action_restore">Restore to cloud</string>
    <string name="cloud_doc_action_purge">Remove from cloud history</string>
    <string name="cloud_doc_status_removed">Removed from cloud</string>
    <string name="cloud_doc_status_still_installed">still installed here</string>
    <string name="cloud_doc_purge_confirm">Permanently remove \"%1$s\" from the cloud history? If it is still installed on another device, that device may upload it back to the cloud on its next sync.</string>
```

- [ ] **Step 3: Thread `includeDeleted` through every scan call**

In `CloudDocumentsActivity.kt`, update the four scan call-sites to pass the toggle:

- The `scanCached()` call (in `openOrGate`): `DocumentSync.scanCached(DocumentSyncSettings.showRemovedDocuments)`
- The `scan()` call in `refresh()`: `DocumentSync.scan(DocumentSyncSettings.showRemovedDocuments)`
- The `scan()` call in the transfer-event handler: `DocumentSync.scan(DocumentSyncSettings.showRemovedDocuments)`
- The `scan()` call in `runSyncAction`: `DocumentSync.scan(DocumentSyncSettings.showRemovedDocuments)`

- [ ] **Step 4: Replace the static spinner with a programmatic, conditional one**

In `activity_cloud_documents.xml`, remove the `android:entries="@array/cloud_doc_status_filters"` attribute from the `statusSpinner` element (leave the rest of the element intact).

In `CloudDocumentsActivity.kt`, add the import for `ArrayAdapter` (alongside the other `android.widget` imports):

```kotlin
import android.widget.ArrayAdapter
```

Add the method:

```kotlin
    /**
     * (Re)populates the status-filter spinner. The "Removed" entry is appended only while
     * [DocumentSyncSettings.showRemovedDocuments] is on; because REMOVED is the last CloudDocFilter
     * value, omitting it keeps the spinner-position → enum mapping correct for the other filters.
     * The current selection is preserved when still in range, otherwise reset to ALL (position 0).
     */
    private fun setupStatusFilter() {
        val labels = mutableListOf(
            getString(R.string.cloud_doc_filter_all),
            getString(R.string.cloud_doc_filter_installed),
            getString(R.string.cloud_doc_filter_cloud),
            getString(R.string.cloud_doc_filter_updates),
            getString(R.string.cloud_doc_filter_blocked),
        )
        if (DocumentSyncSettings.showRemovedDocuments) labels.add(getString(R.string.cloud_doc_filter_removed))
        val previous = binding.statusSpinner.selectedItemPosition
        binding.statusSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, labels,
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.statusSpinner.setSelection(previous.coerceIn(0, labels.lastIndex))
    }
```

Call `setupStatusFilter()` in `onCreate` **before** the line that assigns `binding.statusSpinner.onItemSelectedListener = filterSelectionListener`, so the initial adapter population does not fire a redundant filter callback.

- [ ] **Step 5: Add the "Show removed documents" checkable menu item**

In `CloudDocumentsActivity.kt`, add the constant to the companion object:

```kotlin
    companion object {
        private const val MENU_SYNC_NOW = 2
        private const val MENU_SHOW_REMOVED = 3
    }
```

In `onCreateOptionsMenu`, after the existing Sync-now item, add:

```kotlin
        menu.add(Menu.NONE, MENU_SHOW_REMOVED, Menu.NONE, R.string.cloud_doc_show_removed).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            isCheckable = true
        }
```

In `onPrepareOptionsMenu`, set its visibility and checked state:

```kotlin
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_SYNC_NOW)?.isVisible = CloudSync.signedIn && !adapter.isSelectionMode()
        menu.findItem(MENU_SHOW_REMOVED)?.apply {
            isVisible = CloudSync.signedIn && !adapter.isSelectionMode()
            isChecked = DocumentSyncSettings.showRemovedDocuments
        }
        return super.onPrepareOptionsMenu(menu)
    }
```

In `onOptionsItemSelected`, add the case:

```kotlin
        MENU_SHOW_REMOVED -> {
            val show = !DocumentSyncSettings.showRemovedDocuments
            DocumentSyncSettings.showRemovedDocuments = show
            item.isChecked = show
            // Rebuild the spinner (adds/removes the "Removed" entry) and reset to ALL if the
            // currently-selected filter no longer exists, then re-scan with the new flag.
            if (!show && binding.statusSpinner.selectedItemPosition == CloudDocFilter.REMOVED.ordinal) {
                binding.statusSpinner.setSelection(CloudDocFilter.ALL.ordinal)
            }
            setupStatusFilter()
            refresh()
            true
        }
```

- [ ] **Step 6: Handle the Restore / Purge actions**

In `CloudDocumentsActivity.kt`, in `actionLabel`, add the two new cases (before `TOGGLE_SELECT`):

```kotlin
        CloudDocAction.RESTORE -> R.string.cloud_doc_action_restore
        CloudDocAction.PURGE -> R.string.cloud_doc_action_purge
```

In `performAction`, add the two cases:

```kotlin
            // Restore = re-push the still-installed local copy; the tombstone is overwritten by a
            // fresh, non-deleted meta + uploaded archive (same engine path as Push).
            CloudDocAction.RESTORE -> DocumentSyncService.start(this, listOf(item.initials), emptyList())
            CloudDocAction.PURGE -> confirmPurge(item)
```

Add the confirmation dialog:

```kotlin
    private fun confirmPurge(item: DocumentStatusItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_doc_action_purge)
            .setMessage(getString(R.string.cloud_doc_purge_confirm, item.name))
            .setPositiveButton(R.string.okay) { _, _ ->
                runSyncAction { DocumentSync.purgeTombstone(item.initials) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
```

- [ ] **Step 7: Add the tombstone subtitle branch**

In `CloudDocumentsAdapter.kt`, in the `statusText` companion function, add a `cloudDeleted` branch as the **first** `when` case:

```kotlin
        fun statusText(context: android.content.Context, item: DocumentStatusItem): String = when {
            item.cloudDeleted -> {
                val removed = context.getString(R.string.cloud_doc_status_removed)
                if (item.localOnly) "$removed · ${context.getString(R.string.cloud_doc_status_still_installed)}"
                else removed
            }
            item.blocked -> context.getString(R.string.cloud_doc_status_blocked)
            item.updateAvailable -> context.getString(R.string.cloud_doc_status_update)
            item.cloudOnly -> context.getString(R.string.cloud_doc_status_cloud_only)
            item.localOnly -> context.getString(R.string.cloud_doc_status_local_only)
            else -> context.getString(R.string.cloud_doc_status_synced)
        }
```

- [ ] **Step 8: Remove the now-unused filter array**

In `app/src/main/res/values/arrays.xml`, delete the `cloud_doc_status_filters` string-array (the `<string-array name="cloud_doc_status_filters"> … </string-array>` block).

- [ ] **Step 9: Build and run the unit suite**

Run: `./gradlew testStandardGoogleplayDebugUnitTest` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL; all tests pass (no resource-not-found from the removed array; the layout no longer references it).

- [ ] **Step 10: Manual on-device verification**

Build and install (`./gradlew assembleStandardGithubDebug` then `adb install -r …`). With document sync configured and at least one removed (tombstoned) document in the cloud:
- Overflow menu shows "Show removed documents", unchecked by default; removed documents are hidden.
- Enable it → removed documents appear with subtitle "Removed from cloud" (and "· still installed here" when locally installed); the status spinner gains a "Removed" entry.
- A removed + locally-installed row offers Restore + Purge; a removed + not-installed row offers only Purge.
- Restore re-uploads and the row returns to a normal synced/local state after refresh.
- Purge shows the warning dialog; confirming removes the row.
- Disabling the toggle hides removed documents again and the "Removed" spinner entry; if "Removed" was selected, the filter resets to All.
- Verify in dark, light, and monochrome themes that the rows render correctly.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt \
        app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values/arrays.xml \
        app/src/main/res/layout/activity_cloud_documents.xml
git commit -m "Wire up optional removed-documents view: toggle, filter, Restore/Purge

Claude-Session: https://claude.ai/code/session_018meoC94oFgiHYvfwFLLQdm"
```

---

## Self-Review

**Spec coverage:**
- Awareness/history → tombstone rows + "Removed from cloud" subtitle (Tasks 1, 5). ✓
- Restore (re-push local) → `RESTORE` action, only when installed locally (Tasks 2, 5). ✓
- Purge with warning → `PURGE` action + `confirmPurge` dialog + `purgeTombstone` engine (Tasks 2, 4, 5). ✓
- 3-dot toggle, persisted, default off → `showRemovedDocuments` + `MENU_SHOW_REMOVED` (Task 5). ✓
- REMOVED status filter, only when toggle on → `CloudDocFilter.REMOVED` + conditional spinner (Tasks 3, 5). ✓
- `includeDeleted` default false = no regression → `assembleStatusItems` default + tests (Task 1). ✓
- Archive-gone constraint (restore only from local) → enforced by `localOnly` guard on `RESTORE` (Task 2). ✓
- E-ink/monochrome safety → status via text, manual theme check (Task 5). ✓
- Strings through resources, English only → Task 5 Step 2. ✓
- Remove unused array → Task 5 Step 8. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code; every test step shows assertions.

**Type consistency:** `assembleStatusItems`, `LocalDoc`, `cloudDeleted`, `CloudDocAction.RESTORE/PURGE`, `CloudDocFilter.REMOVED`, `purgeTombstone`, `deleteDocument`, `showRemovedDocuments`, `setupStatusFilter`, `confirmPurge`, `MENU_SHOW_REMOVED` are used consistently across tasks. `scan`/`scanCached` gain a defaulted `includeDeleted` so the `SyncSettings` caller stays unchanged.
