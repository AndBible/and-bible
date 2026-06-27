# Document Sync — Per-Device Direction Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each device independent control over which document-sync operations run automatically (download / upload / delete), and turn the misnamed download-only "Sync now" into a true full sync with a per-run operation picker.

**Architecture:** Three new per-device boolean preferences gate automatic behaviour. The engine's `pullDocuments` is generalised into `runSync(download, upload, delete, manual)` so one code path serves both the automatic sync cycle and the manual "Sync now". Two new pure functions — `selectSyncActions` (filter resolver output by the download/delete flags) and `resolveUploads` (local-only + local-newer minus blocked) — carry the testable logic. The manual action gains a multi-choice dialog.

**Tech Stack:** Kotlin, Android (AndroidX Preference, AppCompat AlertDialog), JUnit + `org.junit.Assert`. Pure logic lives in `service/cloudsync/documents/`; tests in `app/src/test/java/net/bible/service/cloudsync/documents/`.

## Global Constraints

- New files use the 2026 copyright header (Sykerö Software / Tuomas Airaksinen, **no** Martin Denham); edited files get their year bumped to 2026.
- All user-facing strings go through resources (`app/src/main/res/values/strings.xml`). English only.
- Kotlin: import classes and use simple names; no fully-qualified names in code.
- Run Kotlin unit tests with: `./gradlew testStandardGoogleplayDebugUnitTest` (requires `dangerouslyDisableSandbox: true`).
- The three auto toggles default **on** (preserving current behaviour). They gate **automatic** behaviour only — manual actions are never gated by them.
- Uploads (auto and manual) honour the per-device block list, like downloads.

---

### Task 1: Pure resolution helpers — `selectSyncActions` + `resolveUploads`

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncResolver.kt` (append two functions)
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncResolverTest.kt` (append tests)

**Interfaces:**
- Consumes: `DocumentSyncAction`, `DocumentSyncActionType`, `CloudDocument`, `LocalDocument` (all already in `DocumentSyncResolver.kt`).
- Produces:
  - `fun selectSyncActions(actions: List<DocumentSyncAction>, allowDownload: Boolean, allowDelete: Boolean): List<DocumentSyncAction>`
  - `fun resolveUploads(localDocs: Map<String, LocalDocument>, cloudDocs: List<CloudDocument>, blocked: Set<String>, isNewer: (cloudVersion: String, localVersion: String) -> Boolean): List<String>`

- [ ] **Step 1: Write the failing tests**

Append to `DocumentSyncResolverTest.kt` (inside the existing test class, after the last test). If the file lacks the imports, ensure `import org.junit.Assert.assertEquals` and `import org.junit.Test` are present (they are, used by existing tests).

```kotlin
    // --- selectSyncActions ---

    private val sampleActions = listOf(
        DocumentSyncAction("DL", DocumentSyncActionType.DOWNLOAD),
        DocumentSyncAction("UP", DocumentSyncActionType.UPGRADE),
        DocumentSyncAction("RM", DocumentSyncActionType.UNINSTALL),
        DocumentSyncAction("BK", DocumentSyncActionType.SKIP_BLOCKED),
        DocumentSyncAction("NO", DocumentSyncActionType.NONE),
    )

    @Test
    fun selectSyncActions_allowAll_keepsExecutableDropsNonExecutable() {
        val result = selectSyncActions(sampleActions, allowDownload = true, allowDelete = true)
        assertEquals(
            listOf(
                DocumentSyncAction("DL", DocumentSyncActionType.DOWNLOAD),
                DocumentSyncAction("UP", DocumentSyncActionType.UPGRADE),
                DocumentSyncAction("RM", DocumentSyncActionType.UNINSTALL),
            ),
            result,
        )
    }

    @Test
    fun selectSyncActions_downloadOff_dropsDownloadAndUpgrade() {
        val result = selectSyncActions(sampleActions, allowDownload = false, allowDelete = true)
        assertEquals(listOf(DocumentSyncAction("RM", DocumentSyncActionType.UNINSTALL)), result)
    }

    @Test
    fun selectSyncActions_deleteOff_dropsUninstall() {
        val result = selectSyncActions(sampleActions, allowDownload = true, allowDelete = false)
        assertEquals(
            listOf(
                DocumentSyncAction("DL", DocumentSyncActionType.DOWNLOAD),
                DocumentSyncAction("UP", DocumentSyncActionType.UPGRADE),
            ),
            result,
        )
    }

    @Test
    fun selectSyncActions_allOff_keepsNothing() {
        assertEquals(emptyList<DocumentSyncAction>(), selectSyncActions(sampleActions, allowDownload = false, allowDelete = false))
    }

    // --- resolveUploads ---

    private val newer: (String, String) -> Boolean = { a, b -> a > b } // simple lexical comparator for tests

    @Test
    fun resolveUploads_includesLocalOnly() {
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        val result = resolveUploads(local, cloudDocs = emptyList(), blocked = emptySet(), isNewer = newer)
        assertEquals(listOf("KJV"), result)
    }

    @Test
    fun resolveUploads_includesLocalNewerThanCloud() {
        val local = mapOf("KJV" to LocalDocument("KJV", "2.0"))
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "1.0", 0, 0, deleted = false))
        val result = resolveUploads(local, cloud, blocked = emptySet(), isNewer = newer)
        assertEquals(listOf("KJV"), result)
    }

    @Test
    fun resolveUploads_excludesFullySynced() {
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "1.0", 0, 0, deleted = false))
        assertEquals(emptyList<String>(), resolveUploads(local, cloud, emptySet(), newer))
    }

    @Test
    fun resolveUploads_excludesBlocked() {
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        assertEquals(emptyList<String>(), resolveUploads(local, emptyList(), blocked = setOf("KJV"), isNewer = newer))
    }

    @Test
    fun resolveUploads_treatsTombstonedCloudAsMissing() {
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "9.0", 0, 0, deleted = true))
        // A tombstone is not a live cloud copy, so a still-installed local doc is local-only → upload.
        assertEquals(listOf("KJV"), resolveUploads(local, cloud, emptySet(), newer))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncResolverTest" --console=plain` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — `selectSyncActions` / `resolveUploads` unresolved references.

- [ ] **Step 3: Implement the two functions**

Append to `DocumentSyncResolver.kt` (after `decideUninstall`):

```kotlin
/**
 * Filters resolver output down to the actions this sync run is allowed to execute.
 *
 * The three per-device toggles (and the manual "Sync now" dialog) gate operations by direction:
 * DOWNLOAD/UPGRADE are kept only when [allowDownload], UNINSTALL only when [allowDelete].
 * SKIP_BLOCKED and NONE are non-executable and are always dropped. Order is preserved.
 */
fun selectSyncActions(
    actions: List<DocumentSyncAction>,
    allowDownload: Boolean,
    allowDelete: Boolean,
): List<DocumentSyncAction> = actions.filter {
    when (it.type) {
        DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> allowDownload
        DocumentSyncActionType.UNINSTALL -> allowDelete
        DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> false
    }
}

/**
 * Initials of locally installed documents that should be pushed to the cloud: those with no live
 * cloud copy (local-only) or whose local version is strictly newer than the cloud copy. [blocked]
 * documents are excluded (this device opts out of syncing them). A tombstoned cloud entry counts
 * as "no live copy". [isNewer] is the same comparator used elsewhere: returns true when its first
 * argument is strictly newer than its second.
 */
fun resolveUploads(
    localDocs: Map<String, LocalDocument>,
    cloudDocs: List<CloudDocument>,
    blocked: Set<String>,
    isNewer: (cloudVersion: String, localVersion: String) -> Boolean,
): List<String> {
    val liveCloud = cloudDocs.filterNot { it.deleted }.associateBy { it.initials }
    return localDocs.values
        .filter { it.initials !in blocked }
        .filter { local ->
            val cloud = liveCloud[local.initials]
            cloud == null || isNewer(local.version, cloud.version)
        }
        .map { it.initials }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncResolverTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncResolver.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncResolverTest.kt
git commit -m "Add selectSyncActions and resolveUploads pure helpers for direction control"
```

---

### Task 2: Extend `shouldAutoUpload` with an `autoUpload` gate

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncOps.kt:41-42`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncOpsTest.kt` (replace the `shouldAutoUpload` test)

**Interfaces:**
- Produces: `fun shouldAutoUpload(enabled: Boolean, autoUpload: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean`

- [ ] **Step 1: Update the failing test**

Replace the existing `shouldAutoUploadOnlyWhenAllConditionsMet` test in `DocumentSyncOpsTest.kt` with:

```kotlin
    @Test
    fun shouldAutoUploadOnlyWhenAllConditionsMet() {
        assertTrue(shouldAutoUpload(enabled = true, autoUpload = true, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = false, autoUpload = true, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, autoUpload = false, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, autoUpload = true, blocked = true, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, autoUpload = true, blocked = false, autoTransferAllowed = false))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncOpsTest" --console=plain`
Expected: FAIL — argument mismatch (`autoUpload` not a parameter).

- [ ] **Step 3: Update the function**

In `DocumentSyncOps.kt`, replace the `shouldAutoUpload` definition with:

```kotlin
/** Whether an installed document should be auto-uploaded (on install or in the sync cycle). */
fun shouldAutoUpload(enabled: Boolean, autoUpload: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean =
    enabled && autoUpload && !blocked && autoTransferAllowed
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncOpsTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncOps.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncOpsTest.kt
git commit -m "Gate shouldAutoUpload on the new per-device autoUpload toggle"
```

---

### Task 3: Add the new preferences to `DocumentSyncSettings`

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt`

**Interfaces:**
- Produces (all `var Boolean`, default `true`): `autoDownload`, `autoUpload`, `autoDelete`, `syncNowDownload`, `syncNowUpload`, `syncNowDelete`. Pref keys (must match the XML in Task 5): `sync_documents_auto_download`, `sync_documents_auto_upload`, `sync_documents_auto_delete`, `sync_documents_sync_now_download`, `sync_documents_sync_now_upload`, `sync_documents_sync_now_delete`.

No unit test: this is a thin wrapper over `CommonUtils.settings` (Android), consistent with the existing `enabled`/`wifiOnly` properties which are untested. Verified by compilation and by the tasks that consume it.

- [ ] **Step 1: Add the constants and properties**

In `DocumentSyncSettings.kt`, after the existing `WIFI_ONLY` constant add:

```kotlin
    private const val AUTO_DOWNLOAD = "sync_documents_auto_download"
    private const val AUTO_UPLOAD = "sync_documents_auto_upload"
    private const val AUTO_DELETE = "sync_documents_auto_delete"
    private const val SYNC_NOW_DOWNLOAD = "sync_documents_sync_now_download"
    private const val SYNC_NOW_UPLOAD = "sync_documents_sync_now_upload"
    private const val SYNC_NOW_DELETE = "sync_documents_sync_now_delete"
```

After the existing `wifiOnly` property add:

```kotlin
    // Per-device automatic-operation toggles (default on = current behaviour). These gate the
    // automatic sync cycle and install-time auto-upload only; manual actions ignore them.
    var autoDownload: Boolean
        get() = CommonUtils.settings.getBoolean(AUTO_DOWNLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(AUTO_DOWNLOAD, value)

    var autoUpload: Boolean
        get() = CommonUtils.settings.getBoolean(AUTO_UPLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(AUTO_UPLOAD, value)

    var autoDelete: Boolean
        get() = CommonUtils.settings.getBoolean(AUTO_DELETE, true)
        set(value) = CommonUtils.settings.setBoolean(AUTO_DELETE, value)

    // Remembered checkbox state for the manual "Sync now" operation picker (default all on).
    var syncNowDownload: Boolean
        get() = CommonUtils.settings.getBoolean(SYNC_NOW_DOWNLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(SYNC_NOW_DOWNLOAD, value)

    var syncNowUpload: Boolean
        get() = CommonUtils.settings.getBoolean(SYNC_NOW_UPLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(SYNC_NOW_UPLOAD, value)

    var syncNowDelete: Boolean
        get() = CommonUtils.settings.getBoolean(SYNC_NOW_DELETE, true)
        set(value) = CommonUtils.settings.setBoolean(SYNC_NOW_DELETE, value)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt
git commit -m "Add per-device auto-operation and Sync-now-choice preferences"
```

---

### Task 4: Generalise the engine into `runSync` and wire the auto paths

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt:257-286` (rename + extend `pullDocuments`)
- Modify: `app/src/main/java/net/bible/service/cloudsync/CloudSync.kt:434` (call site)
- Modify: `app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt:54-58` (pass `autoUpload`)

**Interfaces:**
- Consumes: `selectSyncActions`, `resolveUploads` (Task 1), `shouldAutoUpload` new signature (Task 2), `DocumentSyncSettings.autoDownload/autoUpload/autoDelete` (Task 3).
- Produces: `suspend fun DocumentSync.runSync(download: Boolean, upload: Boolean, delete: Boolean, manual: Boolean)` (replaces `pullDocuments(automaticOnly)`).

No new unit test: this is suspend/DB/foreground-service integration code whose logic is already covered by the Task 1/2 pure tests. Verified by compilation + the on-device check at the end of the plan.

- [ ] **Step 1: Replace `pullDocuments` with `runSync`**

In `DocumentSync.kt`, replace the whole `pullDocuments` function (lines 257-286) with:

```kotlin
    /**
     * Runs document sync for the selected operations. Used by both the automatic cycle and the
     * manual "Sync now". [download] enqueues DOWNLOAD/UPGRADE, [delete] applies tombstone-driven
     * UNINSTALLs, [upload] pushes local-only / local-newer documents. Block list and (for
     * automatic runs) the enabled + Wi-Fi-only guards always apply; a [manual] run bypasses the
     * enabled/Wi-Fi guards because the user asked for it explicitly.
     */
    suspend fun runSync(download: Boolean, upload: Boolean, delete: Boolean, manual: Boolean) {
        if (!manual && (!DocumentSyncSettings.enabled || !DocumentSyncSettings.isAutoTransferAllowed)) return
        val store = store() ?: return
        val cloudMetas = store.listDocuments()
        // Keep the cache fresh on every run (not only when something transfers), so with automatic
        // sync on the management view can trust the cache without hitting the network.
        DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
            .replaceAll(cloudMetas.map { it.toCacheEntity() })
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
        for (action in actions) when (action.type) {
            DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> toDownload.add(action.initials)
            DocumentSyncActionType.UNINSTALL -> uninstallLocal(action.initials, local)
            DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
        }
        val toUpload = if (upload) resolveUploads(localDocs, cloudDocs, blocked, ::versionIsNewer) else emptyList()
        if (toUpload.isNotEmpty() || toDownload.isNotEmpty()) {
            DocumentSyncService.start(BibleApplication.application, pushInitials = toUpload, downloadInitials = toDownload)
        }
    }
```

- [ ] **Step 2: Update the automatic-cycle call site**

In `CloudSync.kt:434`, replace:

```kotlin
                DocumentSync.pullDocuments(automaticOnly = true)
```

with:

```kotlin
                DocumentSync.runSync(
                    download = DocumentSyncSettings.autoDownload,
                    upload = DocumentSyncSettings.autoUpload,
                    delete = DocumentSyncSettings.autoDelete,
                    manual = false,
                )
```

Ensure `DocumentSyncSettings` is imported in `CloudSync.kt` (add `import net.bible.service.cloudsync.documents.DocumentSyncSettings` if missing — `DocumentSync` is already imported at line 38).

- [ ] **Step 3: Pass `autoUpload` in `BookInstallWatcher`**

In `BookInstallWatcher.kt`, replace the `shouldAutoUpload(...)` call (lines 54-58) with:

```kotlin
                    && shouldAutoUpload(
                        DocumentSyncSettings.enabled,
                        DocumentSyncSettings.autoUpload,
                        DocumentSyncSettings.blockList.isBlocked(book.initials),
                        DocumentSyncSettings.isAutoTransferAllowed,
                    )
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild --console=plain`
Expected: BUILD SUCCESSFUL. (The `CloudDocumentsActivity` call to `pullDocuments` is updated in Task 6; if compiling before Task 6, expect an unresolved `pullDocuments` there — proceed to Task 6 before the final build. To keep this task self-contained, do Step 5 of Task 6 first if compiling standalone. Otherwise compile after Task 6.)

> Note: because `CloudDocumentsActivity` still references `pullDocuments` until Task 6, run the full compile only after Task 6. Within this task, confirm `DocumentSync.kt`, `CloudSync.kt`, and `BookInstallWatcher.kt` have no other errors by inspection.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt \
        app/src/main/java/net/bible/service/cloudsync/CloudSync.kt \
        app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt
git commit -m "Generalise pullDocuments into runSync; gate auto cycle and install-upload on toggles"
```

---

### Task 5: Settings UI — three switches + visibility + strings

**Files:**
- Modify: `app/src/main/res/xml/sync_settings.xml` (add three switches in the `document_sync_category`)
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt` (`updateDocumentSyncVisibility`)
- Modify: `app/src/main/res/values/strings.xml` (six new strings)

**Interfaces:**
- Consumes: `DocumentSyncSettings.enabled` (existing) for visibility; the pref keys from Task 3.

No unit test: Android preference wiring, verified by build + on-device check.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, near the existing `document_sync_wifi_only_*` strings, add:

```xml
    <string name="document_sync_auto_download_title">Download automatically</string>
    <string name="document_sync_auto_download_summary">Automatically download new and updated documents from the cloud to this device</string>
    <string name="document_sync_auto_upload_title">Upload automatically</string>
    <string name="document_sync_auto_upload_summary">Automatically upload documents installed on this device to the cloud</string>
    <string name="document_sync_auto_delete_title">Apply removals automatically</string>
    <string name="document_sync_auto_delete_summary">Automatically remove documents from this device when they are removed on another device</string>
```

- [ ] **Step 2: Add the three switches to the XML**

In `app/src/main/res/xml/sync_settings.xml`, inside the `document_sync_category` `PreferenceCategory`, immediately **before** the existing `sync_documents_wifi_only` switch, add:

```xml
		<SwitchPreferenceCompat
			android:key="sync_documents_auto_download"
			android:defaultValue="true"
			android:title="@string/document_sync_auto_download_title"
			android:summary="@string/document_sync_auto_download_summary" />
		<SwitchPreferenceCompat
			android:key="sync_documents_auto_upload"
			android:defaultValue="true"
			android:title="@string/document_sync_auto_upload_title"
			android:summary="@string/document_sync_auto_upload_summary" />
		<SwitchPreferenceCompat
			android:key="sync_documents_auto_delete"
			android:defaultValue="true"
			android:title="@string/document_sync_auto_delete_title"
			android:summary="@string/document_sync_auto_delete_summary" />
```

- [ ] **Step 3: Toggle their visibility with the rest of the category**

In `SyncSettings.kt`, in `updateDocumentSyncVisibility()`, after the existing `sync_documents_wifi_only` visibility line add:

```kotlin
        for (key in listOf("sync_documents_auto_download", "sync_documents_auto_upload", "sync_documents_auto_delete")) {
            preferenceScreen.findPreference<SwitchPreferenceCompat>(key)?.isVisible = DocumentSyncSettings.enabled
        }
```

(`SwitchPreferenceCompat` and `DocumentSyncSettings` are already imported in `SyncSettings.kt`.)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild --console=plain`
Expected: BUILD SUCCESSFUL (assuming Task 6 done, or `CloudDocumentsActivity` not yet touched — see Task 4 note).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/xml/sync_settings.xml \
        app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt \
        app/src/main/res/values/strings.xml
git commit -m "Add auto download/upload/delete toggles to document sync settings"
```

---

### Task 6: "Sync now" — full sync with an operation picker

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` (menu visibility, dialog, action)
- Modify: `app/src/main/res/values/strings.xml` (three checkbox-label strings)

**Interfaces:**
- Consumes: `DocumentSync.runSync(download, upload, delete, manual)` (Task 4), `DocumentSyncSettings.syncNowDownload/Upload/Delete` (Task 3), `CloudSync.signedIn`.

No unit test: dialog/menu wiring, verified by build + on-device check.

- [ ] **Step 1: Add the checkbox-label strings**

In `app/src/main/res/values/strings.xml`, near the existing `cloud_doc_sync_now` string, add:

```xml
    <string name="cloud_doc_sync_now_download">Download new and updated documents</string>
    <string name="cloud_doc_sync_now_upload">Upload local documents to the cloud</string>
    <string name="cloud_doc_sync_now_delete">Apply removals from other devices</string>
```

- [ ] **Step 2: Make "Sync now" visible whenever signed in**

In `CloudDocumentsActivity.kt`, in `onPrepareOptionsMenu`, replace:

```kotlin
        menu.findItem(MENU_SYNC_NOW)?.isVisible = !DocumentSyncSettings.enabled && !adapter.isSelectionMode()
```

with:

```kotlin
        menu.findItem(MENU_SYNC_NOW)?.isVisible = CloudSync.signedIn && !adapter.isSelectionMode()
```

Add `import net.bible.service.cloudsync.CloudSync` if it is not already imported.

- [ ] **Step 3: Open the operation dialog instead of running directly**

In `CloudDocumentsActivity.kt`, in `onOptionsItemSelected`, replace:

```kotlin
        MENU_SYNC_NOW -> { runSyncAction { DocumentSync.pullDocuments(automaticOnly = false) }; true }
```

with:

```kotlin
        MENU_SYNC_NOW -> { showSyncNowDialog(); true }
```

- [ ] **Step 4: Add the dialog method**

In `CloudDocumentsActivity.kt`, add this method (e.g. just above `runSyncAction`):

```kotlin
    /**
     * "Sync now" is a manual, infrequent action, so it asks which operations to run this time
     * (download / upload / delete), pre-filled from the remembered last choice. The chosen
     * operations run via [DocumentSync.runSync] with manual = true, bypassing the enabled and
     * Wi-Fi-only guards but still honouring the block list.
     */
    private fun showSyncNowDialog() {
        val labels = arrayOf(
            getString(R.string.cloud_doc_sync_now_download),
            getString(R.string.cloud_doc_sync_now_upload),
            getString(R.string.cloud_doc_sync_now_delete),
        )
        val checked = booleanArrayOf(
            DocumentSyncSettings.syncNowDownload,
            DocumentSyncSettings.syncNowUpload,
            DocumentSyncSettings.syncNowDelete,
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_doc_sync_now)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncSettings.syncNowDownload = checked[0]
                DocumentSyncSettings.syncNowUpload = checked[1]
                DocumentSyncSettings.syncNowDelete = checked[2]
                if (checked.any { it }) {
                    runSyncAction {
                        DocumentSync.runSync(
                            download = checked[0],
                            upload = checked[1],
                            delete = checked[2],
                            manual = true,
                        )
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
```

Ensure `androidx.appcompat.app.AlertDialog` is imported (it is the type used elsewhere in this activity — confirm the existing import; if the file imports a different `AlertDialog`, reuse that same one).

- [ ] **Step 5: Verify it compiles (full app)**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild --console=plain`
Expected: BUILD SUCCESSFUL — this is the first point where every `pullDocuments` reference is gone.

- [ ] **Step 6: Run the full unit-test suite for the package**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.*" --console=plain`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/main/res/values/strings.xml
git commit -m "Make Sync now a full sync with a download/upload/delete operation picker"
```

---

### Task 7: Final verification

- [ ] **Step 1: Full debug build**

Run: `./gradlew assembleStandardGithubDebug --console=plain` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install on device (optional, on request)**

Run: `adb install -r app/build/outputs/apk/standardGithub/debug/app-standard-github-debug.apk`

- [ ] **Step 3: On-device smoke test**

Manual checklist:
- Sync settings: with document sync enabled, the three new toggles appear under "Document sync"; with it disabled, they are hidden.
- Turn **Upload** off on a device, install a module → it is **not** auto-uploaded; another device's new module still downloads here.
- "Synced documents" overflow shows "Sync now" even with automatic sync on; the dialog opens with the three checkboxes pre-filled from last time; unchecking Upload and confirming downloads/deletes only; checking Upload pushes local documents.

---

## Self-Review

**Spec coverage:**
- Three auto toggles (download/upload/delete), default on → Tasks 3 (prefs), 5 (UI), 4 (gating). ✓
- Toggles gate automatic only, hidden when sync off → Task 5 visibility; manual `runSync(manual=true)` ignores `enabled` → Task 4/6. ✓
- autoUpload gates both install-time and sync-cycle uploads → Task 2 (install via `shouldAutoUpload`) + Task 4 (cycle via `upload=autoUpload` → `resolveUploads`). ✓
- "Sync now" full sync + operation picker, remembers last choice, visible when signed in → Task 6 + Task 3 prefs. ✓
- Uploads honour block list → `resolveUploads` excludes blocked (Task 1) + `shouldAutoUpload` checks blocked (Task 2). ✓
- All strings via resources, English → Tasks 5, 6. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code. ✓

**Type consistency:** `runSync(download, upload, delete, manual)` used identically in Tasks 4 and 6. `selectSyncActions`/`resolveUploads` signatures match between Task 1 definition and Task 4 use. `shouldAutoUpload(enabled, autoUpload, blocked, autoTransferAllowed)` matches between Task 2 and Task 4. Pref keys match between Task 3 (Kotlin) and Task 5 (XML). ✓

**Build-order note:** `pullDocuments` is referenced by `CloudDocumentsActivity` (Task 6) and `CloudSync` (Task 4). The full app compiles cleanly only after both Task 4 and Task 6; the plan flags this in Task 4 Step 4 and verifies the full build in Task 6 Step 5.
