# Doc-Sync "Sync now" Preview & Cloud Storage Accounting — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the manual "Sync now" dialog show what it will transfer (counts/sizes per direction), and include synced document archive sizes in the cloud-storage total on the Sync settings screen.

**Architecture:** Extract a single planning function `DocumentSync.computeSyncPlan()` from `runSync()` so the preview and the actual run share one resolver path; the "Sync now" dialog renders that plan and starts the transfer directly from it. Sum non-deleted cloud-archive sizes from the local listing cache into `CloudSync.bytesUsed()`.

**Tech Stack:** Kotlin, Android, JUnit (JVM unit tests under `app/src/test`), Gradle.

## Global Constraints

- New/edited files use the copyright header: `Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.` (no "Martin Denham" in new files). When editing an existing file, update its year to `2026` and ensure "Sykerö Software / Tuomas Airaksinen" is present (keep "Martin Denham" if already there).
- All user-facing strings go through `app/src/main/res/values/strings.xml` (English only; other languages handled separately).
- Java/Kotlin: import classes, use simple names — no fully-qualified names in code.
- Cloud document size = `DocumentSyncMeta.size` (the packaged ZIP size in the cloud), **never** the unpacked install size and **never** `DocumentStatusItem.sizeBytes`.
- Gradle commands require `dangerouslyDisableSandbox: true`.
- Unit-test run command: `./gradlew testStandardGoogleplayDebugUnitTest --tests "<pattern>"`.

---

### Task 1: Include synced document archives in cloud-storage total (Issue 2)

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt` (add `sumCloudBytes` top-level fun + `cloudBytesUsed()` in the `DocumentSync` object)
- Modify: `app/src/main/java/net/bible/service/cloudsync/CloudSync.kt:578-582` (`bytesUsed()`)
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncStorageTest.kt` (create)

**Interfaces:**
- Produces: `fun sumCloudBytes(metas: List<DocumentSyncMeta>): Long` (top-level in package `net.bible.service.cloudsync.documents`); `suspend fun DocumentSync.cloudBytesUsed(): Long`.
- Consumes: `DocumentSyncMeta(initials, name, documentType, version, size, language, category, sourceDevice, timestamp, cipherKey, deleted)` data class; `DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().all()` → entities with `.toMeta()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncStorageTest.kt`:

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

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSyncStorageTest {
    private fun meta(initials: String, size: Long, deleted: Boolean = false) = DocumentSyncMeta(
        initials = initials, name = initials, documentType = DocumentType.SWORD, version = "1.0",
        size = size, language = "en", sourceDevice = "dev", timestamp = 0L, deleted = deleted,
    )

    @Test fun sumsNonDeletedArchiveSizes() {
        val metas = listOf(meta("A", 100), meta("B", 250))
        assertEquals(350L, sumCloudBytes(metas))
    }

    @Test fun excludesTombstones() {
        val metas = listOf(meta("A", 100), meta("GONE", 999, deleted = true), meta("B", 50))
        assertEquals(150L, sumCloudBytes(metas))
    }

    @Test fun emptyListIsZero() {
        assertEquals(0L, sumCloudBytes(emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentSyncStorageTest"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — compilation error, `sumCloudBytes` unresolved.

- [ ] **Step 3: Add `sumCloudBytes` and `cloudBytesUsed`**

In `DocumentSync.kt`, add a top-level function near `parseCategoryName` (after the `DocumentSync` object, package level):

```kotlin
/** Total cloud archive bytes for non-deleted documents (the ZIP sizes stored in the cloud). */
fun sumCloudBytes(metas: List<DocumentSyncMeta>): Long =
    metas.filterNot { it.deleted }.sumOf { it.size }
```

Inside the `DocumentSync` object (e.g. after `scanCached`), add:

```kotlin
/** Cloud storage used by synced document archives, read from the local listing cache (no network). */
suspend fun cloudBytesUsed(): Long = withContext(Dispatchers.IO) {
    val cacheDao = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()
    sumCloudBytes(cacheDao.all().map { it.toMeta() })
}
```

(`withContext` and `Dispatchers` are already imported in `DocumentSync.kt`.)

- [ ] **Step 4: Wire into `CloudSync.bytesUsed()`**

In `CloudSync.kt`, change `bytesUsed()` (lines 578-582) to:

```kotlin
suspend fun bytesUsed(): Long =
    DatabaseContainer.databaseAccessorFactories.asyncMap {
        val dbDef = it.invoke()
        dbDef.bytesUsed
    }.sum() + DocumentSync.cloudBytesUsed()
```

Add `import net.bible.service.cloudsync.documents.DocumentSync` to `CloudSync.kt` if not already present.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentSyncStorageTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt \
        app/src/main/java/net/bible/service/cloudsync/CloudSync.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncStorageTest.kt
git commit -m "Include synced document archives in cloud storage total

CloudSync.bytesUsed() now adds the sum of non-deleted document archive
(ZIP) sizes from the document-sync listing cache, so the Sync settings
storage figure reflects doc-sync usage.

Claude-Session: https://claude.ai/code/session_01WcMDjxxtJ1oQbfT9sxh7qC"
```

---

### Task 2: Extract `computeSyncPlan` and the byte-sum helper (Issue 1 backend)

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt` (add `SyncPlan`, `sumPlanBytes`, `computeSyncPlan`; refactor `runSync`)
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/SyncPlanBytesTest.kt` (create)

**Interfaces:**
- Produces:
  - `data class SyncPlan(val toDownload: List<String>, val toUpload: List<String>, val toUninstall: List<String>, val downloadBytes: Long, val uploadBytes: Long)` (top-level in `net.bible.service.cloudsync.documents`).
  - `fun sumPlanBytes(initials: List<String>, sizeByInitials: Map<String, Long>): Long` (top-level).
  - `suspend fun DocumentSync.computeSyncPlan(download: Boolean, upload: Boolean, delete: Boolean): SyncPlan`.
- Consumes (existing, all present in `DocumentSync.kt`): `store()`, `refreshCache()`, `installedSyncableBooks()`, `localInstallSizeBytes(book)`, `versionIsNewer`, `DocumentArchiver.documentVersion`, `selectSyncActions`, `resolveDocumentSyncActions`, `resolveUploads`, `DocumentSyncActionType`, `CloudDocument`, `LocalDocument`, `DocumentSyncSettings`, `DatabaseContainer`, `BibleApplication`, `DocumentSyncService.start`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/service/cloudsync/documents/SyncPlanBytesTest.kt`:

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

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlanBytesTest {
    private val sizes = mapOf("A" to 100L, "B" to 250L, "C" to 50L)

    @Test fun sumsMatchingInitials() {
        assertEquals(350L, sumPlanBytes(listOf("A", "B"), sizes))
    }

    @Test fun missingInitialsContributeZero() {
        assertEquals(100L, sumPlanBytes(listOf("A", "UNKNOWN"), sizes))
    }

    @Test fun emptyIsZero() {
        assertEquals(0L, sumPlanBytes(emptyList(), sizes))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.SyncPlanBytesTest"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — `sumPlanBytes` unresolved.

- [ ] **Step 3: Add `SyncPlan` and `sumPlanBytes`**

In `DocumentSync.kt`, add at package level (near `sumCloudBytes` from Task 1):

```kotlin
/** What a sync run would transfer, used by the manual "Sync now" preview and by [DocumentSync.runSync]. */
data class SyncPlan(
    val toDownload: List<String>,
    val toUpload: List<String>,
    val toUninstall: List<String>,
    /** Sum of cloud ZIP sizes for [toDownload]. */
    val downloadBytes: Long,
    /** Sum of local install sizes for [toUpload] (the same estimate the enable dialog shows). */
    val uploadBytes: Long,
)

/** Sum the byte sizes of [initials] looked up in [sizeByInitials]; unknown initials contribute 0. */
fun sumPlanBytes(initials: List<String>, sizeByInitials: Map<String, Long>): Long =
    initials.sumOf { sizeByInitials[it] ?: 0L }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.SyncPlanBytesTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (3 tests).

- [ ] **Step 5: Add `computeSyncPlan` and refactor `runSync`**

In the `DocumentSync` object, replace the body of `runSync` (currently lines 276-313) so it delegates to a new `computeSyncPlan`. Add `computeSyncPlan` and rewrite `runSync`:

```kotlin
/**
 * Resolves what document sync would transfer for the selected operations, using the same
 * resolver path as [runSync] (full cloud cache incl. tombstones + per-document sync timestamps).
 * Refreshes the listing cache first. Returns an empty plan when not signed in. The manual
 * "Sync now" preview calls this with all directions true to show per-direction counts.
 */
suspend fun computeSyncPlan(download: Boolean, upload: Boolean, delete: Boolean): SyncPlan {
    store() ?: return SyncPlan(emptyList(), emptyList(), emptyList(), 0L, 0L)
    refreshCache()
    val cloudMetas = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().all().map { it.toMeta() }
    val local = installedSyncableBooks().associateBy { it.initials }
    val cloudDocs = cloudMetas.map {
        CloudDocument(it.initials, it.name, it.documentType, it.version, it.size, it.timestamp, it.deleted)
    }
    val localDocs = local.mapValues { (i, b) -> LocalDocument(i, DocumentArchiver.documentVersion(b)) }
    val blocked = DocumentSyncSettings.blockList.all()
    val syncTimestamps = local.keys.mapNotNull { i -> DocumentSyncSettings.syncTimestamp(i)?.let { i to it } }.toMap()
    val actions = selectSyncActions(
        resolveDocumentSyncActions(cloudDocs, localDocs, syncTimestamps, blocked, ::versionIsNewer),
        allowDownload = download,
        allowDelete = delete,
    )
    val toDownload = mutableListOf<String>()
    val toUninstall = mutableListOf<String>()
    for (action in actions) when (action.type) {
        DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> toDownload.add(action.initials)
        DocumentSyncActionType.UNINSTALL -> toUninstall.add(action.initials)
        DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
    }
    val toUpload = if (upload) resolveUploads(localDocs, cloudDocs, blocked, ::versionIsNewer) else emptyList()
    val cloudSizeByInitials = cloudMetas.associate { it.initials to it.size }
    val localSizeByInitials = local.mapValues { (_, b) -> localInstallSizeBytes(b) ?: 0L }
    return SyncPlan(
        toDownload = toDownload,
        toUpload = toUpload,
        toUninstall = toUninstall,
        downloadBytes = sumPlanBytes(toDownload, cloudSizeByInitials),
        uploadBytes = sumPlanBytes(toUpload, localSizeByInitials),
    )
}

suspend fun runSync(download: Boolean, upload: Boolean, delete: Boolean, manual: Boolean) {
    if (!manual && (!DocumentSyncSettings.enabled || !DocumentSyncSettings.isAutoTransferAllowed)) return
    val plan = computeSyncPlan(download, upload, delete)
    DocumentSyncService.start(
        BibleApplication.application,
        pushInitials = plan.toUpload,
        downloadInitials = plan.toDownload,
        uninstallInitials = plan.toUninstall,
    )
}
```

Keep the existing KDoc comment block above `runSync`. Note: `DocumentSyncService.start` already returns early when all lists are empty, so the explicit `isNotEmpty()` guard from the old body is no longer needed.

- [ ] **Step 6: Verify the module compiles and existing tests still pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS — all document-sync tests (resolver, summary, ops, the two new ones) green; no compilation errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/SyncPlanBytesTest.kt
git commit -m "Extract computeSyncPlan as shared planning path for sync

runSync now delegates to computeSyncPlan, which resolves the
download/upload/uninstall sets (and their byte totals) via the same
resolver path. This lets the manual Sync now flow preview exactly what
the run will transfer.

Claude-Session: https://claude.ai/code/session_01WcMDjxxtJ1oQbfT9sxh7qC"
```

---

### Task 3: Show the preview in the "Sync now" dialog (Issue 1 frontend)

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (add count strings/plurals after line 1939)
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` (rewrite `showSyncNowDialog`, add `countLabel` helper, add `Formatter` import)

**Interfaces:**
- Consumes: `DocumentSync.computeSyncPlan(true, true, true): SyncPlan`; `SyncPlan.{toDownload,toUpload,toUninstall,downloadBytes,uploadBytes}`; `DocumentSyncService.start(context, pushInitials, downloadInitials, uninstallInitials = ...)`; the activity's existing `setBusy(Boolean)`, `lifecycleScope`, `R.string.cloud_doc_sync_now*`.
- Produces: none (UI leaf).

- [ ] **Step 1: Add the new strings**

In `app/src/main/res/values/strings.xml`, immediately after line 1939 (`cloud_doc_wifi_waiting`), add:

```xml
    <plurals name="cloud_doc_sync_now_count">
        <item quantity="one">%1$d document</item>
        <item quantity="other">%1$d documents</item>
    </plurals>
    <plurals name="cloud_doc_sync_now_count_size">
        <item quantity="one">%1$d document (%2$s)</item>
        <item quantity="other">%1$d documents (%2$s)</item>
    </plurals>
    <string name="cloud_doc_sync_now_count_none">Nothing to transfer</string>
```

- [ ] **Step 2: Add the `Formatter` import**

In `CloudDocumentsActivity.kt`, add to the imports (after `import android.os.Bundle`):

```kotlin
import android.text.format.Formatter
```

- [ ] **Step 3: Rewrite `showSyncNowDialog` and add `countLabel`**

Replace the whole `showSyncNowDialog()` method (currently lines 567-598) with:

```kotlin
private fun showSyncNowDialog() = lifecycleScope.launch {
    // Resolve what each operation would actually transfer (same resolver path as the real run),
    // behind the non-blocking loading bar, so the dialog can show counts/sizes per direction.
    setBusy(true)
    val plan = try {
        withContext(Dispatchers.IO) { DocumentSync.computeSyncPlan(download = true, upload = true, delete = true) }
    } finally {
        setBusy(false)
    }
    val labels = arrayOf<CharSequence>(
        getString(R.string.cloud_doc_sync_now_download) + "\n" + countLabel(plan.toDownload.size, plan.downloadBytes),
        getString(R.string.cloud_doc_sync_now_upload) + "\n" + countLabel(plan.toUpload.size, plan.uploadBytes),
        // Removals transfer nothing measurable, so show the count only (no size).
        getString(R.string.cloud_doc_sync_now_delete) + "\n" + countLabel(plan.toUninstall.size, null),
    )
    val checked = booleanArrayOf(
        DocumentSyncSettings.syncNowDownload,
        DocumentSyncSettings.syncNowUpload,
        DocumentSyncSettings.syncNowDelete,
    )
    AlertDialog.Builder(this@CloudDocumentsActivity)
        .setTitle(R.string.cloud_doc_sync_now)
        .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
        .setPositiveButton(R.string.okay) { _, _ ->
            DocumentSyncSettings.syncNowDownload = checked[0]
            DocumentSyncSettings.syncNowUpload = checked[1]
            DocumentSyncSettings.syncNowDelete = checked[2]
            // Start the transfer directly from the already-resolved plan, including only the
            // checked directions — no second cache refresh. start() ignores empty lists.
            DocumentSyncService.start(
                this@CloudDocumentsActivity,
                pushInitials = if (checked[1]) plan.toUpload else emptyList(),
                downloadInitials = if (checked[0]) plan.toDownload else emptyList(),
                uninstallInitials = if (checked[2]) plan.toUninstall else emptyList(),
            )
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

/**
 * Second-line label for a "Sync now" operation: a pluralised document count with an optional
 * size, or "nothing to transfer" when the operation has no work. [bytes] is null for removals
 * (no size) and ignored when not greater than zero (size unknown, e.g. a local-only document
 * with no declared install size).
 */
private fun countLabel(count: Int, bytes: Long?): String = when {
    count == 0 -> getString(R.string.cloud_doc_sync_now_count_none)
    bytes != null && bytes > 0 ->
        resources.getQuantityString(R.plurals.cloud_doc_sync_now_count_size, count, count, Formatter.formatShortFileSize(this, bytes))
    else -> resources.getQuantityString(R.plurals.cloud_doc_sync_now_count, count, count)
}
```

Update the KDoc above `showSyncNowDialog` so it reflects the new behaviour (it previously referenced `DocumentSync.runSync` with `manual = true`):

```kotlin
/**
 * "Sync now" is a manual, infrequent action. It first resolves what each operation would
 * transfer (via [DocumentSync.computeSyncPlan]) and shows per-direction counts/sizes, then asks
 * which operations to run (download / upload / delete), pre-filled from the remembered last
 * choice. The chosen directions are dispatched straight to [DocumentSyncService] from the
 * resolved plan; the block list is already applied during resolution and a manual run bypasses
 * the enabled and Wi-Fi-only guards.
 */
```

- [ ] **Step 4: Build the debug variant to verify it compiles**

Run: `./gradlew :app:compileStandardGithubDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL, no unresolved references.

- [ ] **Step 5: Run the document-sync unit tests once more (regression)**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt
git commit -m "Show transfer preview in the Sync now dialog

The manual Sync now dialog now resolves the plan first and annotates
each operation (download / upload / delete) with its document count and
size, mirroring the enable-autosync preview. OK dispatches the chosen
directions straight from the resolved plan.

Claude-Session: https://claude.ai/code/session_01WcMDjxxtJ1oQbfT9sxh7qC"
```

---

## Self-Review

**Spec coverage:**
- Issue 1 backend (`SyncPlan`, `computeSyncPlan`, `runSync` refactor, `sumPlanBytes`) → Task 2. ✓
- Issue 1 frontend (dialog preview, strings, label rules) → Task 3. ✓
- Issue 2 (`sumCloudBytes`, `cloudBytesUsed`, `bytesUsed()` wiring) → Task 1. ✓
- Testing (`sumCloudBytes`, `sumPlanBytes` pure tests; resolver coverage relied upon) → Tasks 1 & 2. ✓
- Label selection rules (size / plain / none) → Task 3 `countLabel`. ✓

**Placeholder scan:** none — every code step shows full code; exact commands and expected output given.

**Type consistency:** `SyncPlan` fields (`toDownload`, `toUpload`, `toUninstall`, `downloadBytes`, `uploadBytes`) are used identically in Tasks 2 and 3. `computeSyncPlan(download, upload, delete)` signature matches between definition (Task 2) and call site (Task 3, all-true). `sumPlanBytes(initials, sizeByInitials)` and `sumCloudBytes(metas)` match their tests. `DocumentSyncService.start` named args (`pushInitials`, `downloadInitials`, `uninstallInitials`) match the verified signature.

## Notes / out of scope
- No network refresh on opening Sync settings for Issue 2 (cache-based, by decision).
- Automatic sync cycle (`CloudSync.kt:440`) behaviour unchanged — still calls `runSync(...)`.
- Enable-autosync dialog unchanged.
