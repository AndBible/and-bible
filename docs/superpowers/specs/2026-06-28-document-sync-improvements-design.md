# Document Sync — Management-View Improvements

**Date:** 2026-06-28
**Status:** Design
**Builds on:** [2026-06-26-document-sync-design.md](2026-06-26-document-sync-design.md) (the consolidated source of truth for the feature as built)

Three refinements to the Synced Documents management view (`CloudDocumentsActivity`),
all confined to the management UI and the pure helpers it uses. No cloud-format, database,
or sync-engine changes.

## Summary

1. **"Do not sync to cloud" for device-only documents.** A locally installed document that is
   not in the cloud (local-only) currently has no way to opt out of automatic upload. Surface the
   existing per-device block list for these rows with a clearer, context-sensitive label.
2. **Full bulk actions in selection mode.** Selection mode currently supports only bulk download.
   Replace it with an Android Contextual Action Bar (CAB) exposing every per-document action
   (download, upload, remove, do-not-sync/allow-sync, restore, purge) over any selection.
3. **"Only on this device" filter.** Add a status-filter entry that shows documents installed on
   this device only (not in the cloud) — distinct from the existing "Installed" filter, which
   includes documents that are also in the cloud.

## Motivation

The block list already excludes a document from **both** auto-upload (`resolveUploads`) and
auto-download (`resolveDocumentSyncActions`) on this device. But `documentMenuActions` only offers
the Block action when `!item.localOnly`, so a device-only document can never be marked — when
`autoUpload` is on it will always be pushed. Improvement 1 closes that gap by reusing the existing
mechanism; no new state, migration, or resolver branch.

Improvements 2 and 3 round out the management view: bulk operations beyond download, and a filter
that isolates exactly the documents that exist nowhere but this device — the natural candidates for
either pushing to the cloud or marking "do not sync".

## Improvement 1 — "Do not sync to cloud" for device-only documents

**Mechanism:** reuse the existing `DocumentBlockList` set (per-device, not synced, stored in
`DocumentSyncDatabase`). No data-model, migration, or engine changes. Blocking a local-only
document excludes it from `resolveUploads`, so `autoUpload` will not push it; the local copy stays
installed and usable.

**Changes (`CloudDocumentsActivity` / `CloudDocumentsAdapter`):**

- `documentMenuActions`: drop the `!item.localOnly` condition guarding the Block action so BLOCK is
  offered for local-only rows too. Tombstone rows are unchanged (no Block — they keep only
  Restore/Purge). The existing `UNBLOCK` branch already applies to any blocked row.
- `actionLabel` becomes context-sensitive (takes the item, or its `localOnly` flag):
  - local-only row: `BLOCK` → "Do not sync to cloud", `UNBLOCK` → "Sync to cloud"
  - cloud row: `BLOCK` → "Block" (existing), `UNBLOCK` → "Unblock" (existing)
- `statusText`: a blocked local-only row reads "Won't sync to cloud"; any other blocked row keeps
  "Blocked". (The `blocked` check stays ahead of the `localOnly` check; only its body branches.)

**New strings:** `cloud_doc_action_dont_sync` ("Do not sync to cloud"),
`cloud_doc_action_allow_sync` ("Sync to cloud"), `cloud_doc_status_wont_sync`
("Won't sync to cloud"). (English only; other languages handled separately.)

**Interactions:** a blocked local-only row appears under the BLOCKED filter and the new
DEVICE_ONLY filter (and ALL). Block/unblock stays instant and in-memory (no network), unchanged.

## Improvement 2 — Full bulk actions (Contextual Action Bar)

Replace the single-action bottom bar (`bottomBar` / `primaryAction`) with a `startSupportActionMode`
CAB. Long-pressing any row enters selection mode with that row selected; tapping a row toggles it.
The previous "nothing to download" guard and `isDownloadable` selectability restriction are removed —
**every row is selectable.** The CAB title shows the selected count.

**CAB menu:** common actions as icons — Download (`ic_cloud_download_24dp`), Upload
(`ic_cloud_upload_24dp`), Remove (delete icon); overflow — Do-not-sync / Allow-sync (block/unblock),
Restore to cloud, Remove from history (purge). While the CAB is active it overlays the app bar, so
the normal options menu (Sync now / Re-scan / Show removed) is hidden automatically.

**Action resolution (pure, reuses single-item logic):**

- `bulkMenuActions(selected: List<DocumentStatusItem>, syncEnabled: Boolean): List<CloudDocAction>`
  — the union of `documentMenuActions(item, syncEnabled)` over the selection, in the canonical
  `CloudDocAction` order. An action appears in the CAB when **at least one** selected row supports it.
- For each chosen action, the applicable subset is the rows where
  `action in documentMenuActions(item, syncEnabled)`. The action runs on that subset only; rows that
  don't support it are skipped.
- After dispatch, a short toast summarises: e.g. "Downloading 2 · skipped 3" (skipped omitted when
  zero). A pure `bulkActionSummary(applied: Int, skipped: Int)` builds the message text from string
  resources.

**Execution:**

- Download / Upload (push) / Restore (= push): collect the applicable initials and dispatch one
  `DocumentSyncService.start(...)` batch (push list + download list together).
- Remove and Purge: a single confirmation `AlertDialog` whose message states the count
  ("Remove 4 documents from all devices?" / cloud-only wording per `DocumentSyncSettings.enabled`,
  mirroring the single-item dialogs). On confirm, dispatch one service batch and apply
  `applyOptimisticRemoval` / `applyOptimisticPurge` per affected row. The last-Bible guard is already
  encoded in `documentMenuActions` (REMOVE_CLOUD is dropped for an undeletable local copy when sync is
  on), so an undeletable Bible is simply never in the Remove subset.
- Do-not-sync / Allow-sync (block/unblock): update the block list and the in-memory list per affected
  row; no network, no service.

The CAB closes after an action runs (matching the current exit-selection-mode behaviour); the
post-transfer completion event drives the reconciling re-scan, as today.

**Adapter changes (`CloudDocumentsAdapter`):**

- Remove `isDownloadable` gating: in selection mode every row shows a visible, checkable checkbox; no
  dimming, no INVISIBLE reserved slot.
- `submit()` retains in the selection every still-present row (drop only initials no longer in the
  list), instead of retaining only downloadable ones.
- Drop the `onNothingToDownload` callback.

## Improvement 3 — "Only on this device" filter

- `CloudDocFilter`: insert `DEVICE_ONLY` **before** `REMOVED` →
  `ALL, INSTALLED, CLOUD, UPDATES, BLOCKED, DEVICE_ONLY, REMOVED`. REMOVED must stay last because the
  spinner appends its "Removed" label conditionally and relies on position→ordinal alignment.
- `filterCloudDocuments`: `CloudDocFilter.DEVICE_ONLY -> item.localOnly && !item.cloudDeleted`.
  (Independent of `blocked`: the filter shows all device-only documents, blocked or not.)
- `setupStatusFilter`: add the "Only on this device" label after the BLOCKED label and before the
  conditionally-appended "Removed" label, preserving the position→`CloudDocFilter.entries` mapping.
- New string `cloud_doc_filter_device_only` ("Only on this device").

This differs from INSTALLED (`!cloudOnly && !cloudDeleted`, i.e. anything installed locally including
cloud-backed copies): DEVICE_ONLY is the strict subset that exists nowhere but this device.

## Out of scope / non-goals

- No change to the cloud storage layout, `DocumentSyncMeta`, `DocumentSyncDatabase`, the sync engine,
  the resolver/upload logic, or the foreground service ops. Improvement 1 reuses the block list as-is.
- No separate "upload-only" vs "download-only" opt-out: a single per-device block (full opt-out for a
  document) covers the device-only use case; independent directions would be over-engineering with no
  concrete use case.
- No bulk "select all" affordance in this pass (can be added later if the CAB proves cramped).

## Testing

Kotlin-only → `./gradlew testStandardGoogleplayDebugUnitTest`. Pure, unit-tested:

- `filterCloudDocuments` — DEVICE_ONLY includes local-only, excludes cloud-backed and tombstones;
  existing filters unaffected by the new enum value.
- `documentMenuActions` — BLOCK now offered for local-only rows; UNBLOCK for blocked local-only;
  tombstone rows still expose only Restore/Purge; last-Bible REMOVE suppression unchanged.
- `bulkMenuActions` — union over a heterogeneous selection (cloud-only + device-only + synced +
  blocked) in canonical order; an action present iff ≥1 row supports it; empty selection → empty.
- applicable-subset selection — for each action, exactly the supporting rows are returned.
- `bulkActionSummary` — "applied" only when skipped == 0; "applied · skipped" otherwise.

Manual / on-device: CAB lifecycle (enter via long-press, count title, icons + overflow, exit),
context-sensitive Block labels and the "Won't sync to cloud" status text, bulk remove/purge
confirmation with counts and optimistic update, the toast summary, and the new filter entry.

## Key components touched

- `view/activity/cloud/CloudDocumentsActivity.kt`: `CloudDocFilter` (+`DEVICE_ONLY`),
  `filterCloudDocuments`, `documentMenuActions`, `actionLabel` (context), `setupStatusFilter`, the
  new `bulkMenuActions` / applicable-subset / `bulkActionSummary` helpers, the ActionMode callback
  replacing `performBulkAction` / `onSelectionChanged` / bottom-bar wiring.
- `view/activity/cloud/CloudDocumentsAdapter.kt`: selectable-all rows, `statusText` (local-only
  blocked), removal of `isDownloadable` / `onNothingToDownload`, `submit()` retention.
- `res/layout/activity_cloud_documents.xml`: remove the bottom action bar.
- `res/menu/`: a new CAB menu resource (or programmatic menu in the ActionMode callback).
- `res/values/strings.xml`: `cloud_doc_action_dont_sync`, `cloud_doc_action_allow_sync`,
  `cloud_doc_status_wont_sync`, `cloud_doc_filter_device_only`, and bulk-summary/confirm plurals.
