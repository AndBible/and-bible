# Document Sync Management-View Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three refinements to the Synced Documents view — "do not sync to cloud" for device-only documents, full bulk actions via a Contextual Action Bar, and an "Only on this device" filter.

**Architecture:** All changes live in the management UI (`CloudDocumentsActivity`, `CloudDocumentsAdapter`) and the pure helpers it uses. Improvement 1 reuses the existing per-device block list (no data-model/engine change). Improvement 2 reuses the per-item `documentMenuActions` logic to derive bulk actions over a selection. Improvement 3 is a new filter enum value.

**Tech Stack:** Kotlin, Android (AppCompat `ActionMode`, RecyclerView `ListAdapter`), JUnit unit tests.

## Global Constraints

- **New-file copyright header:** "Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors." (no "Martin Denham"). No new files are created by this plan, so this applies only if a task adds one.
- **All user-facing text goes through resources** — `app/src/main/res/values/strings.xml`. English only; other languages are handled separately. Never hardcode visible text.
- **Theme/e-ink safety:** status conveyed by text + icon, never colour alone (unchanged here).
- **Test command (Kotlin-only):** `./gradlew testStandardGoogleplayDebugUnitTest` — **requires `dangerouslyDisableSandbox: true`** (Gradle daemon does not run in the sandbox). For a single class append `--tests "net.bible.android.view.activity.cloud.ClassName"`.
- **Commit each task** once its tests pass; one logical change per commit. Do not push.

---

### Task 1: "Only on this device" filter

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` (`CloudDocFilter` enum line 51, `filterCloudDocuments` lines 66-74, `setupStatusFilter` lines 426-433)
- Modify: `app/src/main/res/values/strings.xml` (after line 1913, `cloud_doc_filter_blocked`)
- Test: `app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt`

**Interfaces:**
- Produces: `CloudDocFilter.DEVICE_ONLY` (new enum value, placed before `REMOVED`); `filterCloudDocuments(items, status, nameQuery, category)` now handles `DEVICE_ONLY → item.localOnly && !item.cloudDeleted`.

- [ ] **Step 1: Write the failing tests**

Add to `CloudDocumentsFilterTest.kt` (the existing `items` list already has `KJV` local-only and `GONE` tombstone):

```kotlin
    @Test fun deviceOnlyKeepsOnlyLocalOnlyDocuments() {
        // Only KJV is installed on this device and absent from the cloud.
        assertEquals(listOf("KJV"), filterCloudDocuments(items, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials })
    }

    @Test fun deviceOnlyExcludesTombstones() {
        // A still-installed tombstone is local-only in the data model but must not appear here —
        // it belongs under REMOVED, not "Only on this device".
        val ghost = listOf(item("GHOST", localOnly = true, cloudDeleted = true))
        assertEquals(emptyList<String>(), filterCloudDocuments(ghost, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials })
    }

    @Test fun deviceOnlyDiffersFromInstalled() {
        // INSTALLED includes the synced/cloud-backed local copies; DEVICE_ONLY is the strict subset.
        val both = listOf(
            item("LOCALONLY", localOnly = true),
            item("SYNCED"),                 // installed AND in cloud (neither localOnly nor cloudOnly)
        )
        assertEquals(listOf("LOCALONLY", "SYNCED"), filterCloudDocuments(both, CloudDocFilter.INSTALLED, "", null).map { it.initials })
        assertEquals(listOf("LOCALONLY"), filterCloudDocuments(both, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials })
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsFilterTest"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — `DEVICE_ONLY` is not a member of `CloudDocFilter` (compile error).

- [ ] **Step 3: Add the enum value**

In `CloudDocumentsActivity.kt` line 51, insert `DEVICE_ONLY` **before** `REMOVED` (REMOVED must stay last — the spinner appends its label conditionally and relies on position→ordinal alignment):

```kotlin
enum class CloudDocFilter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED, DEVICE_ONLY, REMOVED }
```

- [ ] **Step 4: Handle the new filter in `filterCloudDocuments`**

In the `when (status)` block (lines 67-74), add the `DEVICE_ONLY` branch after `BLOCKED`:

```kotlin
            CloudDocFilter.BLOCKED -> item.blocked && !item.cloudDeleted
            CloudDocFilter.DEVICE_ONLY -> item.localOnly && !item.cloudDeleted
            CloudDocFilter.REMOVED -> item.cloudDeleted
```

- [ ] **Step 5: Add the spinner label**

In `setupStatusFilter` (lines 426-433), add the device-only label after `cloud_doc_filter_blocked` and before the conditional `cloud_doc_filter_removed`:

```kotlin
        val labels = mutableListOf(
            getString(R.string.cloud_doc_filter_all),
            getString(R.string.cloud_doc_filter_installed),
            getString(R.string.cloud_doc_filter_cloud),
            getString(R.string.cloud_doc_filter_updates),
            getString(R.string.cloud_doc_filter_blocked),
            getString(R.string.cloud_doc_filter_device_only),
        )
        if (DocumentSyncSettings.showRemovedDocuments) labels.add(getString(R.string.cloud_doc_filter_removed))
```

- [ ] **Step 6: Add the string**

In `app/src/main/res/values/strings.xml`, after line 1913 (`cloud_doc_filter_blocked`):

```xml
    <string name="cloud_doc_filter_device_only">Only on this device</string>
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsFilterTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (all filter tests, including the three new ones).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsFilterTest.kt
git commit -m "Add 'Only on this device' filter to synced documents view

Claude-Session: https://claude.ai/code/session_01VzDdwCTKLnxvuEyiNT5dfC"
```

---

### Task 2: "Do not sync to cloud" for device-only documents

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` (`documentMenuActions` lines 105-106; replace the private `actionLabel` lines 482-493 with a pure top-level `actionLabelRes`; update its caller in `showItemMenu` line 473)
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt` (`statusText` lines 164-175)
- Modify: `app/src/main/res/values/strings.xml` (after line 1925, `cloud_doc_action_restore`)
- Test: `app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsMenuTest.kt`

**Interfaces:**
- Consumes: `documentMenuActions(item, syncEnabled)`, `CloudDocAction`.
- Produces: `documentMenuActions` now offers `BLOCK`/`UNBLOCK` for local-only rows; new top-level `fun actionLabelRes(action: CloudDocAction, localOnly: Boolean, syncEnabled: Boolean): Int`.

- [ ] **Step 1: Write/adjust the failing tests**

In `CloudDocumentsMenuTest.kt`, **replace** `localOnlyOffersOnlyPush` (lines 46-48) with the new expectation, and add the label + unblock tests:

```kotlin
    @Test fun localOnlyOffersPushAndBlock() {
        // A device-only document can now be marked "do not sync to cloud" (BLOCK) in addition to Push.
        assertEquals(
            listOf(CloudDocAction.PUSH, CloudDocAction.BLOCK),
            documentMenuActions(item(localOnly = true), syncEnabled = true),
        )
    }

    @Test fun blockedLocalOnlyOffersUnblock() {
        assertEquals(
            listOf(CloudDocAction.PUSH, CloudDocAction.UNBLOCK),
            documentMenuActions(item(localOnly = true, blocked = true), syncEnabled = true),
        )
    }

    @Test fun actionLabelIsContextSensitiveForBlock() {
        // Local-only: "do not sync to cloud"; cloud document: the existing "block" wording.
        assertEquals(R.string.cloud_doc_action_dont_sync, actionLabelRes(CloudDocAction.BLOCK, localOnly = true, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_allow_sync, actionLabelRes(CloudDocAction.UNBLOCK, localOnly = true, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_block, actionLabelRes(CloudDocAction.BLOCK, localOnly = false, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_unblock, actionLabelRes(CloudDocAction.UNBLOCK, localOnly = false, syncEnabled = true))
    }

    @Test fun actionLabelRemoveDependsOnSyncEnabled() {
        assertEquals(R.string.cloud_doc_action_remove_all_devices, actionLabelRes(CloudDocAction.REMOVE_CLOUD, localOnly = false, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_remove_cloud, actionLabelRes(CloudDocAction.REMOVE_CLOUD, localOnly = false, syncEnabled = false))
    }
```

Add the import at the top of the test file (after line 18's package, alongside the other imports):

```kotlin
import net.bible.android.activity.R
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsMenuTest"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — `actionLabelRes` unresolved; `localOnlyOffersPushAndBlock` mismatch (currently only PUSH).

- [ ] **Step 3: Offer BLOCK for local-only rows**

In `documentMenuActions` (`CloudDocumentsActivity.kt`), change the block/unblock tail (lines 105-106) to drop the `!item.localOnly` guard:

```kotlin
    // Block/unblock the per-device sync opt-out. For a local-only document this means
    // "do not sync to cloud" (the block list already excludes it from auto-upload); for a
    // cloud-backed document it also blocks auto-download to this device.
    if (item.blocked) add(CloudDocAction.UNBLOCK)
    else add(CloudDocAction.BLOCK)
```

- [ ] **Step 4: Replace `actionLabel` with the pure top-level `actionLabelRes`**

Delete the private `actionLabel` method (lines 482-493). Add a top-level function next to the other pure helpers (e.g. just below `documentMenuActions`, before the `applyOptimisticRemoval` doc comment at line 109):

```kotlin
/**
 * The string resource for a per-item action's menu label. Context-sensitive: for a local-only
 * document, Block/Unblock read as "do not sync to cloud" / "sync to cloud"; for a cloud-backed
 * document they keep the block-on-this-device wording. Remove adapts to whether sync is enabled
 * (it then deletes everywhere, otherwise cloud only).
 */
fun actionLabelRes(action: CloudDocAction, localOnly: Boolean, syncEnabled: Boolean): Int = when (action) {
    CloudDocAction.DOWNLOAD -> R.string.cloud_doc_action_download
    CloudDocAction.PUSH -> R.string.cloud_doc_action_push
    CloudDocAction.REMOVE_CLOUD ->
        if (syncEnabled) R.string.cloud_doc_action_remove_all_devices else R.string.cloud_doc_action_remove_cloud
    CloudDocAction.BLOCK ->
        if (localOnly) R.string.cloud_doc_action_dont_sync else R.string.cloud_doc_action_block
    CloudDocAction.UNBLOCK ->
        if (localOnly) R.string.cloud_doc_action_allow_sync else R.string.cloud_doc_action_unblock
    CloudDocAction.RESTORE -> R.string.cloud_doc_action_restore
    CloudDocAction.PURGE -> R.string.cloud_doc_action_purge
}
```

- [ ] **Step 5: Update the caller in `showItemMenu`**

In `showItemMenu` (line 473), pass the item context to the new function:

```kotlin
        documentMenuActions(item, DocumentSyncSettings.enabled).forEachIndexed { order, action ->
            popup.menu.add(0, action.ordinal, order, actionLabelRes(action, item.localOnly, DocumentSyncSettings.enabled))
        }
```

- [ ] **Step 6: Context-sensitive status text**

In `CloudDocumentsAdapter.kt` `statusText` (lines 164-175), branch the `blocked` case:

```kotlin
            item.blocked ->
                if (item.localOnly) context.getString(R.string.cloud_doc_status_wont_sync)
                else context.getString(R.string.cloud_doc_status_blocked)
```

- [ ] **Step 7: Add the strings**

In `strings.xml`, after line 1925 (`cloud_doc_action_restore`) add the action labels, and after line 1907 (`cloud_doc_status_blocked`) add the status:

```xml
    <string name="cloud_doc_action_dont_sync">Do not sync to cloud</string>
    <string name="cloud_doc_action_allow_sync">Sync to cloud</string>
```

```xml
    <string name="cloud_doc_status_wont_sync">Won\'t sync to cloud</string>
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsMenuTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsMenuTest.kt
git commit -m "Allow marking device-only documents 'do not sync to cloud'

Surfaces the existing per-device block list for local-only rows with
context-sensitive labels and status text.

Claude-Session: https://claude.ai/code/session_01VzDdwCTKLnxvuEyiNT5dfC"
```

---

### Task 3: Bulk action resolution helpers (pure)

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` (add two top-level functions near `documentMenuActions`)
- Create: `app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsBulkTest.kt`

**Interfaces:**
- Consumes: `documentMenuActions(item, syncEnabled)` (from Task 2), `CloudDocAction`, `DocumentSync.DocumentStatusItem`.
- Produces:
  - `fun bulkMenuActions(selected: List<DocumentSync.DocumentStatusItem>, syncEnabled: Boolean): List<CloudDocAction>` — union of per-item actions, in `CloudDocAction` declaration order.
  - `fun applicableInitials(action: CloudDocAction, selected: List<DocumentSync.DocumentStatusItem>, syncEnabled: Boolean): List<String>` — initials of the selected items that support `action`.

- [ ] **Step 1: Write the failing tests**

Create `CloudDocumentsBulkTest.kt`:

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

class CloudDocumentsBulkTest {
    private fun item(
        initials: String,
        cloudOnly: Boolean = false, localOnly: Boolean = false,
        update: Boolean = false, localNewer: Boolean = false, blocked: Boolean = false,
        canDeleteLocal: Boolean = true, cloudDeleted: Boolean = false,
    ) = DocumentStatusItem(
        initials = initials, name = initials, type = DocumentType.SWORD,
        cloudVersion = "1.0", localVersion = "1.0",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = update,
        localNewer = localNewer, blocked = blocked, sizeBytes = 0, category = BookCategory.BIBLE,
        canDeleteLocal = canDeleteLocal, cloudDeleted = cloudDeleted,
    )

    @Test fun emptySelectionHasNoActions() {
        assertEquals(emptyList<CloudDocAction>(), bulkMenuActions(emptyList(), syncEnabled = true))
    }

    @Test fun unionOverHeterogeneousSelectionInCanonicalOrder() {
        // cloud-only → DOWNLOAD, REMOVE_CLOUD, BLOCK; device-only → PUSH, BLOCK; synced → REMOVE_CLOUD, BLOCK.
        // Union, in CloudDocAction declaration order (DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, ...).
        val selected = listOf(item("A", cloudOnly = true), item("B", localOnly = true), item("C"))
        assertEquals(
            listOf(CloudDocAction.DOWNLOAD, CloudDocAction.PUSH, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK),
            bulkMenuActions(selected, syncEnabled = true),
        )
    }

    @Test fun blockAndUnblockBothAppearForMixedBlockedState() {
        // One blocked, one not → both opt-out actions surface (each applies to its own subset).
        val selected = listOf(item("A", cloudOnly = true, blocked = true), item("B", cloudOnly = true))
        val actions = bulkMenuActions(selected, syncEnabled = true)
        assertEquals(true, actions.contains(CloudDocAction.BLOCK))
        assertEquals(true, actions.contains(CloudDocAction.UNBLOCK))
    }

    @Test fun applicableInitialsForDownloadSkipsNonDownloadable() {
        val selected = listOf(item("A", cloudOnly = true), item("B", localOnly = true), item("C", update = true))
        // Only the cloud-only and updatable rows can be downloaded; the device-only row is skipped.
        assertEquals(listOf("A", "C"), applicableInitials(CloudDocAction.DOWNLOAD, selected, syncEnabled = true))
    }

    @Test fun applicableInitialsForBlockSkipsAlreadyBlocked() {
        val selected = listOf(item("A", cloudOnly = true, blocked = true), item("B", localOnly = true))
        // A is already blocked (offers UNBLOCK, not BLOCK); only B can be newly blocked.
        assertEquals(listOf("B"), applicableInitials(CloudDocAction.BLOCK, selected, syncEnabled = true))
    }

    @Test fun applicableInitialsForRemoveRespectsLastBibleGuardWhenSyncOn() {
        // Undeletable local copy (last Bible) with sync on: REMOVE_CLOUD is not offered → skipped.
        val selected = listOf(item("A", canDeleteLocal = false), item("B"))
        assertEquals(listOf("B"), applicableInitials(CloudDocAction.REMOVE_CLOUD, selected, syncEnabled = true))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsBulkTest"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — `bulkMenuActions` / `applicableInitials` unresolved.

- [ ] **Step 3: Implement the helpers**

In `CloudDocumentsActivity.kt`, add below `documentMenuActions` (and below the new `actionLabelRes` from Task 2):

```kotlin
/**
 * The bulk actions offered for a multi-selection: the union of [documentMenuActions] over
 * [selected], in canonical [CloudDocAction] declaration order. An action is offered when at least
 * one selected item supports it; it then operates only on that supporting subset (see
 * [applicableInitials]). An empty selection yields no actions.
 */
fun bulkMenuActions(
    selected: List<DocumentSync.DocumentStatusItem>,
    syncEnabled: Boolean,
): List<CloudDocAction> {
    val supported = selected.flatMapTo(mutableSetOf()) { documentMenuActions(it, syncEnabled) }
    return CloudDocAction.entries.filter { it in supported }
}

/**
 * Initials of the [selected] items that support [action] — the exact subset a bulk [action] runs on.
 * Items that don't support it (e.g. a device-only row under a bulk Download) are skipped.
 */
fun applicableInitials(
    action: CloudDocAction,
    selected: List<DocumentSync.DocumentStatusItem>,
    syncEnabled: Boolean,
): List<String> =
    selected.filter { action in documentMenuActions(it, syncEnabled) }.map { it.initials }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.CloudDocumentsBulkTest"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/test/java/net/bible/android/view/activity/cloud/CloudDocumentsBulkTest.kt
git commit -m "Add pure bulk-action resolution helpers for selection mode

Claude-Session: https://claude.ai/code/session_01VzDdwCTKLnxvuEyiNT5dfC"
```

---

### Task 4: Adapter — every row selectable

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt` (constructor, `isDownloadable`, `submit`, `bind`)

**Interfaces:**
- Consumes: `onSelectionChanged: (Int) -> Unit`.
- Produces: `CloudDocumentsAdapter(onOverflow, onSelectionChanged)` (the `onNothingToDownload` param is removed); `getSelectedInitials()`, `isSelectionMode()`, `setSelectionMode(Boolean)` unchanged. Long-press now always enters selection mode; every row is checkable.

This task is Android UI — it has no unit test; verification is a successful compile (the unit-test task compiles all main sources) plus the manual on-device check in Task 5.

- [ ] **Step 1: Remove the constructor's `onNothingToDownload` param**

In `CloudDocumentsAdapter.kt`, delete the `onNothingToDownload` constructor parameter (lines 50-51), leaving:

```kotlin
class CloudDocumentsAdapter(
    private val onOverflow: (DocumentStatusItem, View) -> Unit,
    /** Invoked whenever the set of selected items changes (selection mode only). */
    private val onSelectionChanged: (Int) -> Unit = {},
) : ListAdapter<DocumentStatusItem, CloudDocumentsAdapter.ViewHolder>(DIFF_CALLBACK) {
```

- [ ] **Step 2: Remove the `isDownloadable` restriction**

Delete the `isDownloadable` function (lines 54-55).

- [ ] **Step 3: Retain all still-present rows in `submit`**

Replace the `submit` body's selection-pruning (lines 61-70) with retention by presence, not downloadability:

```kotlin
    fun submit(items: List<DocumentStatusItem>) {
        // A refresh can drop rows (e.g. a document removed elsewhere). Keep in the selection only
        // the initials still present in the new list, so the CAB count stays consistent.
        if (selectionMode) {
            val present = items.mapTo(mutableSetOf()) { it.initials }
            if (selectedInitials.retainAll(present)) onSelectionChanged(selectedInitials.size)
        }
        submitList(items)
    }
```

- [ ] **Step 4: Make every row checkable in `bind`**

Replace the selection block in `bind` (lines 111-147) so the checkbox shows for all rows in selection mode, with no dimming, and long-press always starts selection:

```kotlin
            // In selection mode every row is selectable: show a checkable box and let taps toggle it.
            checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            itemView.alpha = 1f
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = item.initials in selectedInitials
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedInitials.add(item.initials) else selectedInitials.remove(item.initials)
                onSelectionChanged(selectedInitials.size)
            }

            overflow.visibility = if (selectionMode) View.GONE else View.VISIBLE
            // The activity builds and shows the popup menu of valid actions for this item.
            overflow.setOnClickListener { onOverflow(item, it) }

            itemView.setOnClickListener {
                if (selectionMode) checkbox.isChecked = !checkbox.isChecked
            }
            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    selectedInitials.add(item.initials)
                    notifyItemRangeChanged(0, itemCount)
                    onSelectionChanged(selectedInitials.size)
                }
                true
            }
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.*"` (with `dangerouslyDisableSandbox: true`)
Expected: COMPILE FAILS in `CloudDocumentsActivity.kt` only — it still passes `onNothingToDownload =` to the adapter constructor (fixed in Task 5). This task's own file compiles. If you prefer a green checkpoint, do Steps 1-4 here and run the build at the end of Task 5; otherwise proceed directly to Task 5 before committing.

- [ ] **Step 6: Commit (with Task 5)**

This change is not independently buildable (the activity still references the removed callback). Commit it together with Task 5, or stage it now and commit after Task 5's build is green. Recommended: proceed to Task 5, then commit Tasks 4+5 together.

---

### Task 5: Activity — Contextual Action Bar for bulk actions

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` (adapter construction, `performBulkAction`, `onSelectionChanged`, `exitSelectionMode`, imports; add the `ActionMode.Callback` + bulk dispatch/confirm helpers)
- Modify: `app/src/main/res/layout/activity_cloud_documents.xml` (remove the bottom bar, lines 89-101)
- Create: `app/src/main/res/menu/cloud_documents_selection.xml`
- Modify: `app/src/main/res/values/strings.xml` (add CAB/bulk strings; remove two now-unused strings)

**Interfaces:**
- Consumes: `bulkMenuActions`, `applicableInitials` (Task 3); `applyOptimisticRemoval`, `applyOptimisticPurge`, `documentMenuActions` (existing); adapter `getSelectedInitials()` / `isSelectionMode()` / `setSelectionMode()` (Task 4); `DocumentSyncService.start(...)`.

This task is Android UI — verification is a green build (unit tests compile + pass) plus a manual on-device check.

- [ ] **Step 1: Remove the bottom action bar from the layout**

In `activity_cloud_documents.xml`, delete the entire `bottomBar` `LinearLayout` block (lines 89-101, the one containing `primaryAction`).

- [ ] **Step 2: Create the selection menu resource**

Create `app/src/main/res/menu/cloud_documents_selection.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  ~ Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
  ~
  ~ This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  ~
  ~ AndBible is free software: you can redistribute it and/or modify it under the
  ~ terms of the GNU General Public License as published by the Free Software Foundation,
  ~ either version 3 of the License, or (at your option) any later version.
  ~
  ~ AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  ~ without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  ~ See the GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License along with AndBible.
  ~ If not, see http://www.gnu.org/licenses/.
  -->
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item android:id="@+id/bulk_download"
        android:icon="@drawable/ic_cloud_download_24dp"
        android:title="@string/cloud_doc_action_download"
        app:showAsAction="ifRoom" />
    <item android:id="@+id/bulk_upload"
        android:icon="@drawable/ic_cloud_upload_24dp"
        android:title="@string/cloud_doc_action_push"
        app:showAsAction="ifRoom" />
    <item android:id="@+id/bulk_remove"
        android:icon="@drawable/ic_delete_24dp"
        android:title="@string/cloud_doc_action_remove_cloud"
        app:showAsAction="ifRoom" />
    <item android:id="@+id/bulk_dont_sync"
        android:title="@string/cloud_doc_action_dont_sync"
        app:showAsAction="never" />
    <item android:id="@+id/bulk_allow_sync"
        android:title="@string/cloud_doc_action_allow_sync"
        app:showAsAction="never" />
    <item android:id="@+id/bulk_restore"
        android:title="@string/cloud_doc_action_restore"
        app:showAsAction="never" />
    <item android:id="@+id/bulk_purge"
        android:title="@string/cloud_doc_action_purge"
        app:showAsAction="never" />
</menu>
```

- [ ] **Step 3: Add the new strings and remove the unused ones**

In `strings.xml`, **remove** `cloud_doc_bulk_download` (line 1949) and `cloud_doc_nothing_to_download` (line 1950) — the single-button bulk download and its "nothing to download" toast are gone. **Add**:

```xml
    <string name="cloud_doc_selected_count">%1$d selected</string>
    <string name="cloud_doc_bulk_skipped">Skipped %1$d not applicable</string>
    <plurals name="cloud_doc_bulk_remove_all_confirm">
        <item quantity="one">Remove %1$d document from the cloud and from all your devices, including this one?</item>
        <item quantity="other">Remove %1$d documents from the cloud and from all your devices, including this one?</item>
    </plurals>
    <plurals name="cloud_doc_bulk_remove_cloud_confirm">
        <item quantity="one">Remove %1$d document from the cloud? Copies on this device are kept.</item>
        <item quantity="other">Remove %1$d documents from the cloud? Copies on this device are kept.</item>
    </plurals>
    <plurals name="cloud_doc_bulk_purge_confirm">
        <item quantity="one">Permanently remove %1$d document from the cloud history?</item>
        <item quantity="other">Permanently remove %1$d documents from the cloud history?</item>
    </plurals>
```

- [ ] **Step 4: Update imports and the adapter construction**

At the top of `CloudDocumentsActivity.kt`, add the import (with the other `androidx` imports):

```kotlin
import androidx.appcompat.view.ActionMode
```

In `onCreate`, drop the `onNothingToDownload` argument and the `binding.primaryAction` listener. The adapter construction (lines 181-187) becomes:

```kotlin
        adapter = CloudDocumentsAdapter(
            onOverflow = { item, anchor -> showItemMenu(item, anchor) },
            onSelectionChanged = { count -> onSelectionChanged(count) },
        )
```

Delete the line `binding.primaryAction.setOnClickListener { performBulkAction() }` (line 193).

- [ ] **Step 5: Add the `actionMode` field and the callback**

Add a field beside the other state (near lines 165-170):

```kotlin
    /** The active selection-mode Contextual Action Bar, or null when not in selection mode. */
    private var actionMode: ActionMode? = null
```

Add the callback and a `selectedItems()` helper (e.g. near `onSelectionChanged`):

```kotlin
    /** The currently selected rows, resolved from the adapter's selected initials. */
    private fun selectedItems(): List<DocumentStatusItem> {
        val selected = adapter.getSelectedInitials()
        return allItems.filter { it.initials in selected }
    }

    private val selectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.cloud_documents_selection, menu)
            return true
        }

        // Re-evaluated on every selection change: show only the actions at least one selected row supports.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val actions = bulkMenuActions(selectedItems(), DocumentSyncSettings.enabled)
            menu.findItem(R.id.bulk_download).isVisible = CloudDocAction.DOWNLOAD in actions
            menu.findItem(R.id.bulk_upload).isVisible = CloudDocAction.PUSH in actions
            menu.findItem(R.id.bulk_remove).isVisible = CloudDocAction.REMOVE_CLOUD in actions
            menu.findItem(R.id.bulk_dont_sync).isVisible = CloudDocAction.BLOCK in actions
            menu.findItem(R.id.bulk_allow_sync).isVisible = CloudDocAction.UNBLOCK in actions
            menu.findItem(R.id.bulk_restore).isVisible = CloudDocAction.RESTORE in actions
            menu.findItem(R.id.bulk_purge).isVisible = CloudDocAction.PURGE in actions
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean {
            val action = when (menuItem.itemId) {
                R.id.bulk_download -> CloudDocAction.DOWNLOAD
                R.id.bulk_upload -> CloudDocAction.PUSH
                R.id.bulk_remove -> CloudDocAction.REMOVE_CLOUD
                R.id.bulk_dont_sync -> CloudDocAction.BLOCK
                R.id.bulk_allow_sync -> CloudDocAction.UNBLOCK
                R.id.bulk_restore -> CloudDocAction.RESTORE
                R.id.bulk_purge -> CloudDocAction.PURGE
                else -> return false
            }
            performBulkAction(action)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            adapter.setSelectionMode(false)
        }
    }
```

- [ ] **Step 6: Rewrite `onSelectionChanged` and `exitSelectionMode`**

Replace the existing `exitSelectionMode` (lines 372-376) and `onSelectionChanged` (lines 378-391) with:

```kotlin
    private fun exitSelectionMode() {
        actionMode?.finish()
    }

    /** Starts / updates / ends the Contextual Action Bar as the selection changes. */
    private fun onSelectionChanged(count: Int) {
        if (!adapter.isSelectionMode() || count == 0) {
            actionMode?.finish()
            return
        }
        if (actionMode == null) actionMode = startSupportActionMode(selectionActionModeCallback)
        actionMode?.apply {
            title = getString(R.string.cloud_doc_selected_count, count)
            invalidate()   // re-run onPrepareActionMode for the new selection
        }
    }
```

- [ ] **Step 7: Replace `performBulkAction` with the action-aware version**

Replace the old `performBulkAction` (lines 397-402) with the dispatcher plus its helpers:

```kotlin
    /**
     * Runs a bulk [action] over the selected rows it applies to (others are skipped). Destructive
     * actions (remove / purge) confirm first; the rest dispatch immediately. A skipped count is
     * surfaced as a brief toast.
     */
    private fun performBulkAction(action: CloudDocAction) {
        val selected = selectedItems()
        val applicable = applicableInitials(action, selected, DocumentSyncSettings.enabled)
        val skipped = selected.size - applicable.size
        if (applicable.isEmpty()) { exitSelectionMode(); return }
        when (action) {
            CloudDocAction.REMOVE_CLOUD -> confirmBulkRemove(applicable, skipped)
            CloudDocAction.PURGE -> confirmBulkPurge(applicable, skipped)
            else -> {
                dispatchBulk(action, applicable)
                reportSkipped(skipped)
                exitSelectionMode()
            }
        }
    }

    /** Dispatches a non-destructive bulk action over [initials]. */
    private fun dispatchBulk(action: CloudDocAction, initials: List<String>) {
        when (action) {
            CloudDocAction.DOWNLOAD -> DocumentSyncService.start(this, emptyList(), initials)
            // Restore is a re-push of the still-installed local copy — same engine path as Push.
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> DocumentSyncService.start(this, initials, emptyList())
            CloudDocAction.BLOCK -> {
                initials.forEach { DocumentSyncSettings.blockList.block(it) }
                allItems = allItems.map { if (it.initials in initials) it.copy(blocked = true) else it }
                applyFilter()
            }
            CloudDocAction.UNBLOCK -> {
                initials.forEach { DocumentSyncSettings.blockList.unblock(it) }
                allItems = allItems.map { if (it.initials in initials) it.copy(blocked = false) else it }
                applyFilter()
            }
            else -> {}
        }
    }

    private fun confirmBulkRemove(initials: List<String>, skipped: Int) {
        val enabled = DocumentSyncSettings.enabled
        val title = if (enabled) R.string.cloud_doc_action_remove_all_devices else R.string.cloud_doc_action_remove_cloud
        val message = resources.getQuantityString(
            if (enabled) R.plurals.cloud_doc_bulk_remove_all_confirm else R.plurals.cloud_doc_bulk_remove_cloud_confirm,
            initials.size, initials.size,
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncService.start(this, emptyList(), emptyList(), removeInitials = initials)
                initials.forEach { allItems = applyOptimisticRemoval(allItems, it, enabled) }
                applyFilter()
                reportSkipped(skipped)
                exitSelectionMode()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmBulkPurge(initials: List<String>, skipped: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_doc_action_purge)
            .setMessage(resources.getQuantityString(R.plurals.cloud_doc_bulk_purge_confirm, initials.size, initials.size))
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncService.start(this, emptyList(), emptyList(), purgeInitials = initials)
                initials.forEach { allItems = applyOptimisticPurge(allItems, it) }
                applyFilter()
                reportSkipped(skipped)
                exitSelectionMode()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reportSkipped(skipped: Int) {
        if (skipped > 0) {
            Toast.makeText(this, getString(R.string.cloud_doc_bulk_skipped, skipped), Toast.LENGTH_SHORT).show()
        }
    }
```

- [ ] **Step 8: Verify the build (compile + all doc-sync unit tests)**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.view.activity.cloud.*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS — everything compiles (Task 4 + Task 5 together) and all `cloud` unit tests pass.

Then run the full doc-sync suite to confirm no regressions:
Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.*cloud*" --tests "net.bible.service.cloudsync.documents.*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 9: Manual on-device check**

Build & install (`./gradlew assembleStandardGithubDebug` then `adb install -r ...`, both with `dangerouslyDisableSandbox: true`). In Synced Documents:
1. Long-press any row → CAB appears with "N selected"; tapping rows updates the count.
2. Select a mix (cloud-only + device-only + synced) → CAB shows Download/Upload icons + overflow (Do not sync / Restore / Remove from history as applicable). Run Download → only the cloud-side rows transfer; a "Skipped N not applicable" toast appears for the rest.
3. Select two installed-in-cloud rows → Remove → confirmation states the count → both removed.
4. On a device-only row's overflow (not selection mode): "Do not sync to cloud" appears; tapping it shows the "Won't sync to cloud" status; the row appears under both the BLOCKED and "Only on this device" filters.
5. Switch the status filter to "Only on this device" → only local-only (non-cloud, non-removed) documents show.

- [ ] **Step 10: Commit (Tasks 4 + 5)**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt \
        app/src/main/res/layout/activity_cloud_documents.xml \
        app/src/main/res/menu/cloud_documents_selection.xml \
        app/src/main/res/values/strings.xml
git commit -m "Replace bulk-download with full bulk actions via a Contextual Action Bar

Every row is selectable; the CAB offers download/upload/remove plus
do-not-sync/allow-sync/restore/purge in the overflow, each applied to
the selected subset it is valid for.

Claude-Session: https://claude.ai/code/session_01VzDdwCTKLnxvuEyiNT5dfC"
```

---

## Notes for the implementer

- **Gradle in the sandbox:** every `./gradlew` command needs `dangerouslyDisableSandbox: true`. If a build fails with a Gradle journal-cache lock error, another sandbox may be building — wait and retry; do not kill processes or run `--stop`.
- **`R` in unit tests:** `R.string.*` / `R.plurals.*` are plain `int` constants and resolve in plain JUnit tests (no Robolectric needed) — that is why `actionLabelRes` is unit-testable by comparing resource ids.
- **`ActionMode` import:** use `androidx.appcompat.view.ActionMode` (paired with `startSupportActionMode`), not the platform `android.view.ActionMode`.
- **Why Tasks 4 and 5 commit together:** removing the adapter's `onNothingToDownload` callback (Task 4) breaks the activity's constructor call until Task 5 updates it, so the two are not independently buildable.
- **`statusText` is not unit-tested** (it needs a `Context`); it is covered by the Task 5 manual check. The substantive label logic (`actionLabelRes`) is unit-tested instead.
```
