# Document Sync — Optional Display of Removed (Tombstoned) Documents

**Date:** 2026-06-27
**Status:** Design

Extends the document-sync feature (see `2026-06-26-document-sync-design.md` and
`2026-06-27-document-sync-direction-control-design.md`) with an **optional view of removed
documents** — cloud tombstones — in the Synced Documents management view, plus per-row actions to
restore or permanently purge them.

## Motivation

When a document is removed from the cloud, `DocumentStore.writeTombstone` commits a `deleted = true`
`meta.json` and **deletes the `.abmd.zip` archive**. Only the tombstone meta remains. The
management view filters these out (`buildStatusItems`: `cloudMetas.filter { !it.deleted }`), so a
removed document simply disappears.

This leaves three gaps:

1. **Awareness / history** — there is no way to see what was removed from the cloud, or to
   understand why a document vanished from the user's other devices.
2. **Restore** — a removal cannot be undone from the UI, even though the document may still be
   installed locally on a device.
3. **Cleanup** — tombstones accumulate in the cloud folder with no way to clear them.

## Key constraint: the archive is gone

Because `writeTombstone` deletes the `.abmd.zip`, **restore-by-download is impossible** — there is
no data in the cloud to fetch. A tombstoned document can only be restored by **re-pushing from a
device that still has it installed locally**. This shapes the restore action below: it is offered
only when the document is installed on this device.

## Goals

- Optionally show tombstoned (removed) documents in the management view, off by default.
- Toggle visibility from the overflow (3-dot) menu, persisted per device.
- A status-filter entry to isolate removed documents, shown only while the toggle is on.
- Per-row **Restore to cloud** (re-push the local copy) — only when installed locally.
- Per-row **Remove from cloud history** (purge the tombstone meta), with a propagation warning.

## Non-goals

- No restore-by-download (the archive no longer exists in the cloud).
- No change to the tombstone write path, the resolver, the enable flow, the cloud layout, or any
  existing per-item action.
- No automatic purging of tombstones — purge is an explicit, manual, per-row action.

## Data model (`DocumentSync`)

`DocumentStatusItem` gains one field:

- **`cloudDeleted: Boolean`** — the cloud meta for this initials is a tombstone (`deleted = true`).

`buildStatusItems` gains an `includeDeleted: Boolean = false` parameter:

- **`includeDeleted = false` (default):** behaves exactly as today — tombstones are filtered out
  (`cloudMetas.filter { !it.deleted }`). **No regression.** A document that is tombstoned in the
  cloud but still installed locally continues to show as a normal local-only row, exactly as now.
- **`includeDeleted = true`:** tombstone metas are included as their own rows. Per initials the
  cloud holds exactly one `meta.json` (either live or a tombstone, never both), so live and
  tombstone maps are disjoint. For a tombstone row:
  - `cloudDeleted = true`; `cloudVersion` = the last known version from the tombstone meta (for
    display); `name` / `category` fall back to the tombstone meta when not installed locally.
  - `cloudOnly` / `localOnly` / `updateAvailable` / `localNewer` are computed **as if the cloud copy
    were absent** (there is no downloadable copy): a locally installed tombstone → `localOnly = true`,
    a not-installed tombstone (`b == null`) → neither cloud-only nor local-only, just `cloudDeleted`.

`allInitials` becomes `live.keys + local.keys` and, when `includeDeleted`, `+ tombstone.keys`.

`scan(includeDeleted: Boolean = false)` and `scanCached(includeDeleted: Boolean = false)` thread the
flag through. The default keeps the enable-flow caller (`SyncSettings.scan()` for the upload/download
summary) unchanged; the management view passes `DocumentSyncSettings.showRemovedDocuments`.

## Visibility toggle (overflow menu)

A new checkable overflow item **`MENU_SHOW_REMOVED`** — *"Show removed documents"* — default off.

- State persisted in **`DocumentSyncSettings.showRemovedDocuments`** (a per-device preference, like
  the block list and Wi-Fi-only). Not synced.
- Visible whenever signed in, hidden in selection mode (mirrors the existing "Sync now" item).
- Toggling re-runs the scan with the new `includeDeleted` value and re-renders, and rebuilds the
  status-filter spinner (see below).

## Status filter (`CloudDocFilter`, spinner)

- New enum value **`CloudDocFilter.REMOVED`**, appended as the **last** value so the existing
  positional mapping `CloudDocFilter.entries[spinnerPosition]` stays correct when REMOVED is the
  trailing spinner item.
- `filterCloudDocuments`: `CloudDocFilter.REMOVED -> item.cloudDeleted`.
- The spinner stops using the static `android:entries="@array/cloud_doc_status_filters"` and is
  populated **programmatically** from a label list that appends the REMOVED label **only when
  `showRemovedDocuments` is on**. Because REMOVED is the last enum value, positions 0..4
  (ALL..BLOCKED) keep mapping correctly whether or not REMOVED is present.
- When the toggle is turned **off** while REMOVED is the selected filter, the spinner is rebuilt and
  the selection resets to ALL before re-filtering (no stale empty filter).
- The unused `@array/cloud_doc_status_filters` array is removed.

The existing INSTALLED / CLOUD filters work naturally on tombstone rows (a locally installed
tombstone is INSTALLED via `!cloudOnly`).

## Per-row actions (`documentMenuActions`)

When `item.cloudDeleted`, the menu offers **only**:

- **`RESTORE`** ("Restore to cloud") — added **only when the document is installed locally**
  (`localOnly`). Reuses the existing `pushDocument(book)` engine call, which writes a fresh
  non-deleted meta and uploads the archive, replacing the tombstone with a live document. No
  confirmation dialog — direct, consistent with the existing Push action.
- **`PURGE`** ("Remove from cloud history") — always offered for a tombstone row. Calls a new
  `DocumentSync.purgeTombstone(initials)` that deletes the entire `{initials}/` cloud folder (the
  tombstone `meta.json`) and refreshes the cache.

No Download / Push / Remove-from-cloud / Block actions are offered on a tombstone row.

Two new `CloudDocAction` values: `RESTORE`, `PURGE`, with their own label resources.

## Confirmation dialogs

- **Purge:** a warning `AlertDialog` — the tombstone is the signal that prevents other devices from
  re-uploading the document. Body warns: removing the cloud history entry is permanent, and if the
  document is still installed on another device that has not yet applied the removal, that device
  may re-upload it to the cloud on its next sync. Confirm / Cancel.
- **Restore:** no dialog — direct push, consistent with the existing per-item Push.

## Row subtitle

A tombstone row's status text reads **"Removed from cloud"**, with **"· still installed here"**
appended when the document is installed locally. Status is conveyed by text (never colour alone), so
the row stays grayscale / e-ink safe like the rest of the view.

## Engine (`DocumentStore`, `DocumentSync`)

- `DocumentStore` gains a method to delete a document's whole cloud folder (meta + any residue) by
  initials, used by purge.
- `DocumentSync.purgeTombstone(initials)` — delete the cloud folder, then `refreshCache()`.
- Restore needs no new engine code: `pushDocument(book)` already checks only for a non-deleted
  `existing` meta (null for a tombstone) and proceeds to upload.

## Strings (`strings.xml`, English only)

New resources: the "Show removed documents" menu item, the `cloud_doc_filter_removed` filter label,
the `RESTORE` and `PURGE` action labels, the purge confirmation title and body (with the propagation
warning), and the "Removed from cloud" / "still installed here" subtitle fragments. All user-facing
text goes through resources.

## Testing

Kotlin-only → `./gradlew testStandardGoogleplayDebugUnitTest`. Pure, unit-tested logic:

- `buildStatusItems`:
  - `includeDeleted = false` filters tombstones out (current behaviour preserved).
  - `includeDeleted = true`, tombstone only → a `cloudDeleted` row, not cloud-only, not local-only.
  - `includeDeleted = true`, tombstone + locally installed → `cloudDeleted && localOnly`.
- `documentMenuActions` on a `cloudDeleted` row:
  - locally installed → `[RESTORE, PURGE]`.
  - not installed → `[PURGE]` only.
  - never offers DOWNLOAD / PUSH / REMOVE_CLOUD / BLOCK.
- `filterCloudDocuments` with `CloudDocFilter.REMOVED` keeps only `cloudDeleted` items; the existing
  filters' behaviour on tombstone rows is covered.

`purgeTombstone` (I/O) and the dialog / menu / spinner-rebuild wiring are verified by manual
on-device testing, consistent with the rest of the feature's UI wiring.

## Touchpoints

- `service/cloudsync/documents/`: `DocumentSync` (`DocumentStatusItem.cloudDeleted`,
  `buildStatusItems(includeDeleted)`, `scan`/`scanCached` flag, `purgeTombstone`),
  `DocumentStore` (delete-folder-by-initials), `DocumentSyncSettings` (`showRemovedDocuments` pref).
- `android/view/activity/cloud/CloudDocumentsActivity`: `CloudDocFilter.REMOVED`,
  `filterCloudDocuments`, `documentMenuActions`, `CloudDocAction.RESTORE`/`PURGE`, the overflow
  toggle, programmatic spinner, purge dialog, subtitle text.
- `res/values/strings.xml`, `res/values/arrays.xml` (remove unused filter array).

## Future considerations

- Surface a count of removed documents somewhere, if tombstones become numerous.
- A bulk "purge all tombstones" action if one-by-one purging proves tedious.
