# Document Sync: "Sync now" preview & cloud storage accounting

Date: 2026-06-27

## Background

Two follow-up issues on the document-sync feature:

1. **"Sync now" gives no preview.** The manual "Sync now" action
   (`CloudDocumentsActivity.showSyncNowDialog()`) shows three checkboxes
   (download / upload / delete) and runs immediately on OK. It never tells the
   user *what* will be transferred. Enabling automatic sync, by contrast, scans
   first and shows a summary dialog ("X uploads (Y MB), Z downloads (W MB)").
   "Sync now" should show what is coming, the same way.

2. **Cloud storage figure omits synced documents.** The Sync settings screen
   shows `cloud_sync_info` = `CloudSync.bytesUsed()`, which sums only the synced
   *databases* (bookmarks, workspaces, …). The space used by synced *documents*
   in the cloud is not included, so the figure never reflects doc-sync usage.

## Key facts established during investigation

- `DocumentSyncMeta.size` is the size of the **packaged ZIP archive stored in
  the cloud** (`pushDocument`: `size = archive.length()`; `downloadAndInstall`
  comment: "the exact packaged (zip) size recorded at push time"). This is the
  correct quantity for cloud storage — **not** the unpacked module size. Issue 2
  must sum these cloud meta sizes, **not** `DocumentStatusItem.sizeBytes` (which
  falls back to the unpacked install size for local-only documents).

- The actual sync run (`runSync`) resolves transfers via
  `resolveDocumentSyncActions` + `resolveUploads`, reading the **full** cloud
  cache (tombstones included) and per-document sync timestamps. This diverges
  subtly from `computeDocumentSyncSummary` (used by the enable dialog), which
  works off a scan with tombstones filtered out. Example: a document removed
  from the cloud but still installed locally is counted as an upload by
  `computeDocumentSyncSummary`, but `resolveUploads` will **not** push it (it
  respects the deletion intent). Therefore the "Sync now" preview must be
  derived from the **same resolver path as `runSync`**, not from
  `computeDocumentSyncSummary`.

- `runSync` is called by both the automatic sync cycle (`CloudSync.kt:440`,
  `manual = false`) and currently by the manual dialog (`manual = true`). Its
  signature must be preserved for the automatic caller.

## Issue 1 — "Sync now" preview

### `DocumentSync.kt` — extract a single planning function

Introduce one source of truth for "what would this sync do", used by both the
preview and the actual run:

```kotlin
data class SyncPlan(
    val toDownload: List<String>,
    val toUpload: List<String>,
    val toUninstall: List<String>,
    val downloadBytes: Long,   // sum of cloud ZIP sizes for toDownload
    val uploadBytes: Long,     // sum of local install sizes for toUpload (same estimate as enable dialog)
)

/** Pure: sum cloud archive sizes for the given initials. Unit-testable. */
fun sumPlanBytes(initials: List<String>, sizeByInitials: Map<String, Long>): Long =
    initials.sumOf { sizeByInitials[it] ?: 0L }

suspend fun computeSyncPlan(download: Boolean, upload: Boolean, delete: Boolean): SyncPlan
```

- `computeSyncPlan` contains the current `runSync` body up to action resolution:
  `store()` guard → `refreshCache()` → build `cloudDocs` / `localDocs` /
  `blocked` / `syncTimestamps` → `selectSyncActions(resolveDocumentSyncActions(...))`
  → derive `toDownload` (DOWNLOAD/UPGRADE), `toUninstall` (UNINSTALL); `toUpload`
  via `resolveUploads` when `upload`. It additionally sums:
  - `downloadBytes` = `sumPlanBytes(toDownload, cloudSizeByInitials)` where
    `cloudSizeByInitials` is built from the cloud metas (`.size`).
  - `uploadBytes` = `sumPlanBytes(toUpload, localInstallSizeByInitials)` where
    sizes come from `localInstallSizeBytes(book)` (the existing helper).
- When `store()` is null, returns an empty plan (all-empty lists, zero bytes).

`runSync` is refactored to delegate:

```kotlin
suspend fun runSync(download: Boolean, upload: Boolean, delete: Boolean, manual: Boolean) {
    if (!manual && (!DocumentSyncSettings.enabled || !DocumentSyncSettings.isAutoTransferAllowed)) return
    val plan = computeSyncPlan(download, upload, delete)
    if (plan.toUpload.isNotEmpty() || plan.toDownload.isNotEmpty() || plan.toUninstall.isNotEmpty()) {
        DocumentSyncService.start(
            BibleApplication.application,
            pushInitials = plan.toUpload,
            downloadInitials = plan.toDownload,
            uninstallInitials = plan.toUninstall,
        )
    }
}
```

The automatic cycle (`CloudSync.kt:440`) is unchanged — it still calls
`runSync(...)` with the per-device auto toggles.

### `CloudDocumentsActivity.showSyncNowDialog()` — show the preview

Flow:

1. Compute the full plan once, behind the existing non-blocking loading bar:
   `val plan = withContext(Dispatchers.IO) { DocumentSync.computeSyncPlan(true, true, true) }`.
   (Computing with all three directions true yields the counts for every row,
   independent of the remembered checkbox state.)
2. Show the same three-checkbox multi-choice dialog, pre-filled from
   `DocumentSyncSettings.syncNow{Download,Upload,Delete}`. Each label keeps its
   existing descriptive sentence and gains a second line with the count:
   - Download: `<count line>` from `plan.toDownload.size` / `plan.downloadBytes`
   - Upload: from `plan.toUpload.size` / `plan.uploadBytes`
   - Delete: from `plan.toUninstall.size` — **count only, no size** (matches the
     approved mockup; a removal transfers nothing measurable to display)
3. On OK: persist the three checkbox states (as today) and start the transfer
   directly from the already-computed plan, including only the checked
   directions — **no second `refreshCache` / network round-trip**:

   ```kotlin
   val push     = if (checked[1]) plan.toUpload    else emptyList()
   val download = if (checked[0]) plan.toDownload  else emptyList()
   val uninstall= if (checked[2]) plan.toUninstall else emptyList()
   if (push.isNotEmpty() || download.isNotEmpty() || uninstall.isNotEmpty())
       DocumentSyncService.start(this, pushInitials = push, downloadInitials = download, uninstallInitials = uninstall)
   ```

   Starting the service directly (rather than calling `runSync(manual = true)`)
   preserves the previous manual semantics: the block list is already applied
   during resolution, and manual transfers bypass the enabled/Wi-Fi guards.
   Calling `DocumentSyncService.start` directly is consistent with the rest of
   the activity (`performBulkAction`, `performAction` already do so).

The label-count line is built with a count line where the row text is the
existing label plus `"\n"` plus the count string. `CheckedTextView` in the
multi-choice dialog wraps, so a two-line label renders correctly.

### New strings (`app/src/main/res/values/strings.xml`)

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

Label-count selection: size variant when count > 0 and bytes > 0; plain count
when count > 0 and bytes == 0 (size unknown, e.g. local doc with no declared
install size); `cloud_doc_sync_now_count_none` when count == 0. Built with
`resources.getQuantityString(...)` / `Formatter.formatShortFileSize`.

## Issue 2 — include synced documents in cloud storage total

### `DocumentSync.kt`

```kotlin
/** Pure: total cloud archive bytes for non-deleted documents. Unit-testable. */
fun sumCloudBytes(metas: List<DocumentSyncMeta>): Long =
    metas.filterNot { it.deleted }.sumOf { it.size }

/** Cloud storage used by synced document archives, read from the local listing cache. */
suspend fun cloudBytesUsed(): Long = withContext(Dispatchers.IO) {
    val cacheDao = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()
    sumCloudBytes(cacheDao.all().map { it.toMeta() })
}
```

Read from the local cloud-listing cache (no network) — fast, consistent with
the existing instant `bytesUsed()` DB reads. If doc sync has never populated the
cache, the sum is 0 (correct).

### `CloudSync.bytesUsed()`

```kotlin
suspend fun bytesUsed(): Long =
    DatabaseContainer.databaseAccessorFactories.asyncMap { it.invoke().bytesUsed }.sum() +
        DocumentSync.cloudBytesUsed()
```

No UI change: `cloud_sync_info` already renders `CloudSync.bytesUsed()` and now
includes document archives.

## Testing

- **`sumCloudBytes`** (new, pure): non-deleted summed, tombstones excluded, empty
  list = 0, sizes added correctly.
- **`sumPlanBytes`** (new, pure): missing initials contribute 0, sums match,
  empty = 0.
- Plan-list derivation (download/upload/uninstall sets) is already covered by
  `DocumentSyncResolverTest` (`resolveDocumentSyncActions`, `resolveUploads`,
  `selectSyncActions`) — no new resolver tests needed; the planning function only
  composes those tested pieces plus the byte sums.

`computeSyncPlan`, `cloudBytesUsed`, and the dialog wiring touch the DB / network
/ Android `Context` and are verified by the existing build + manual on-device
check, not by new JVM unit tests.

## Out of scope

- No network refresh on opening Sync settings for Issue 2 (cache-based, by
  decision).
- No change to the automatic sync cycle behaviour.
- No change to the enable-autosync dialog.
