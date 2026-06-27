# Document Sync — Consolidated Design

**Date:** 2026-06-26 (last consolidated 2026-06-27)
**Status:** Implemented

This is the single source of truth for the document-sync feature. It consolidates and
supersedes all earlier design notes — the initial design, testing corrections, background
service, settings-UX simplification, the **per-device direction control** notes, and the
**removed-documents view** notes — folding in the refinements made during hands-on testing.
It describes the feature as built.

## Summary

Synchronize installed Bible documents (SWORD modules, MyBible, MySword, eSword, EPUB)
across a user's devices using the **same cloud backend** as the existing device sync
(`CloudAdapter` — Google Drive / Nextcloud). When a document is installed on one device
— by any method (repository download, sideloaded zip, MyBible/MySword/eSword/EPUB import)
— the **complete document** is packaged and copied to the cloud and re-installed on the
user's other devices.

The whole document is copied (packaged as an `.abmd.zip`) rather than re-downloaded,
because sideloaded and custom-repository documents can't be reliably re-fetched on another
device. Enabling sync on a fresh device also brings down everything already in the cloud.

**Core model:** when document sync is enabled, behaviour is automatic. By default it syncs
everything in both directions; three per-device toggles (**auto-download / auto-upload /
auto-remove**, all default on) let a device opt out of any automatic operation without
losing manual control. Per-document control (block, remove, restore) and a manual "Sync
now" happen in the Synced Documents management view.

## Goals

- Sync all document types and all install methods.
- Track and propagate versions ("newest wins", auto-upgrade on other devices).
- Per-device control of automatic behaviour: independent download / upload / delete toggles.
- Per-device opt-out (block a document from auto-downloading to *this* device).
- A management view of the cloud store + local documents, with manual actions, including an
  optional view of removed (tombstoned) documents.
- Network-aware: Wi-Fi-only option for automatic transfers.
- All transfers — and all cloud mutations — run in the background (foreground service) with a
  progress notification; the UI never blocks and operations survive leaving the view.

## Non-goals

- No real-time sync — runs on the existing sync cycle, on install events, and on manual action.
- No multi-version retention in the cloud — only the newest version is kept.
- No automatic row-level conflict merge — last-writer-wins (low frequency).
- No per-document byte-level transfer progress (the cloud adapter doesn't expose it).

## Cloud storage layout

A sync folder alongside the other per-category folders, on the same `CloudAdapter`
account, lowercase to match siblings (`…-sync-bookmarks`, etc.):

```
net.bible.android-sync-documents/
├── {initials}/
│   ├── meta.json            # per-document manifest
│   └── {version}.abmd.zip   # packaged document (BackupControl format)
```

`meta.json` (`DocumentSyncMeta`, kotlinx.serialization, `ignoreUnknownKeys` + `encodeDefaults`):

```json
{
  "initials": "KJV", "name": "King James Version", "documentType": "SWORD",
  "version": "2.6", "size": 16384000, "language": "en", "category": "BIBLE",
  "sourceDevice": "<deviceId>", "timestamp": 1740000000000,
  "cipherKey": null, "deleted": false
}
```

- `documentType`: `SWORD | MYBIBLE | MYSWORD | ESWORD | EPUB`.
- The **folder listing is the manifest** — no synced database. `category` carries the
  `BookCategory` so cloud-only items can be category-filtered/iconned in the view.
- Only the newest version is kept; an upgrade uploads the new zip, deletes the old, updates meta.
- `cipherKey` is stored for encrypted SWORD modules (the user's own private cloud account).
- `deleted: true` is a **tombstone** for deletion propagation. Writing a tombstone (`writeTombstone`)
  commits the `deleted=true` meta and then **deletes the `.abmd.zip` archive** — only the marker
  remains. (Consequence: a removed document can be *restored* only by re-pushing a still-installed
  local copy, never by re-downloading; see Restore.)
- `size` is the exact packaged (zip) size; the management view falls back to the installed
  module size (`SwordBookMetaData` install size) for a local-only document not yet uploaded,
  so its upload size isn't shown as 0.

## Settings (`sync_settings.xml`, `SyncSettings.kt`)

There is **no separate "Automatic" toggle** — sync is automatic when enabled, shaped by the
three per-device operation toggles. Document-sync preferences live in two groups:

```
General sync
  (adapter, reset, cloud info, server/username/password/folder)

Synchronization categories
  Bookmarks · Workspaces · Reading plans · My documents · AI settings · Progress
  Documents                       ← master toggle, alongside the others

Document sync   (category shown only when signed in)
  Auto-download                   ← download new/updated documents automatically   [enabled only]
  Auto-upload                     ← upload installed documents automatically        [enabled only]
  Auto-remove                     ← apply removals from other devices automatically [enabled only]
  Sync documents on Wi-Fi only    [enabled only]
  Synced documents  ▸             ← opens the management view (icons: Wi-Fi, cloud)
```

- The **Document sync** category is visible whenever signed in (`CloudSync.signedIn`, a
  proxy for "a cloud adapter is configured" — `cloudAdapter` is internal). This keeps
  **Synced documents** reachable for manual sync even when automatic sync is off.
- The three auto toggles and Wi-Fi-only are shown only while document sync is enabled
  (`updateDocumentSyncVisibility()`), reinforcing that they shape *automatic* behaviour only.

### Per-device automatic-operation toggles

Three independent prefs in `DocumentSyncSettings`, all default `true` (preserving the original
"sync everything" behaviour):

| Pref          | Gates                                                                 |
|---------------|-----------------------------------------------------------------------|
| `autoDownload`| Auto DOWNLOAD / UPGRADE in the sync cycle.                            |
| `autoUpload`  | Auto upload — **both** install-time (`BookInstallWatcher`) **and** the sync-cycle push of local-only / local-newer documents. |
| `autoDelete`  | Auto UNINSTALL (tombstone-driven removal propagation) in the sync cycle. |

These gate **automatic** behaviour only. Manual actions (per-item Download/Push/Remove/Restore/
Purge, selection-mode bulk download, "Sync now") always work, unaffected by the toggles — they are
explicit user decisions. The driving case for `autoUpload=off`: a device used for testing
throwaway modules that should receive the curated cloud set but never push its own.

### Enable flow

Turning the Documents master toggle **on** (every time, guarded against repeated taps by a
synchronous in-progress flag + a blocking hourglass until the dialog appears):

1. Ensure sign-in (`CloudSync.signIn`). If it fails/cancels → toggle stays off.
2. `DocumentSync.scan()` → compute, honouring the block list, what would be uploaded
   (local-only) and downloaded (cloud-only + update-available), with counts and summed sizes
   (`computeDocumentSyncSummary`).
3. An **AlertDialog** reports it ("Upload N (X) … Download M (Y)"), omitting an empty
   direction and omitting a size when unknown, plus a Wi-Fi note when metered.
4. Confirm → `DocumentSyncSettings.enabled = true` + `DocumentSyncService.start(push, download)`.
   Cancel → no change.

There is no in-activity "setup mode"; the summary is the dialog.

## Sync engine (`DocumentSync`)

- **`scan(includeDeleted = false)`** — when signed in, fetch the live cloud listing AND refresh the
  cache; offline, read the cache. Builds the unified status list (`DocumentStatusItem`) via the
  pure `assembleStatusItems` (see below), combining cloud metas with locally installed books
  (excluding pseudo-books and MyDocuments).
- **`scanCached(includeDeleted = false)`** — cache-only, no network; used to render the view
  instantly on open and to re-render local-only view changes.
- **`refreshCache()`** — refresh the cloud-listing cache from the network (called after every
  service drain, and on every sync run).
- **`pushDocument(book)`** — package + upload if the cloud is missing/older; record sync timestamp.
  Re-checks the cloud (skips packaging if same/newer already there). Used for Push and Restore.
- **`downloadAndInstall(initials)`** — download newest zip + install (integrity-checks the zip size
  against the meta before installing; `installingFromSync` suppresses the echo auto-push).
- **`removeFromCloud(initials)`** — write a tombstone (newest timestamp). With sync **enabled**,
  also delete the local copy on this device (so it isn't orphaned); with sync **off**, keep the
  local copy. Records this device's sync timestamp = tombstone timestamp so its own next pull is
  a no-op. **Marks the cached entry `deleted` up front** (before the slow network writes) so the
  management view is correct if reopened mid-operation.
- **`purgeTombstone(initials)`** — permanently delete a tombstone's whole cloud folder
  (`DocumentStore.deleteDocument`), then `refreshCache()`. **Deletes the cached entry up front**
  for the same reopen-correctness reason. Manual only.
- **`uninstallLocal(initials)`** — uninstall the local copy after a remote removal (tombstone-driven),
  honouring the last-deletable guard (never removes the last Bible). Looks the book up by initials so
  it can run from the service queue.
- **`runSync(download, upload, delete, manual)`** — the single code path for both the automatic
  cycle and manual "Sync now" (generalises the former `pullDocuments`). Automatic runs require
  `enabled` + `isAutoTransferAllowed`; a `manual` run bypasses both. Refreshes the cache, resolves
  actions, filters them by the `download`/`delete` flags (`selectSyncActions`), and **enqueues all
  work on the service**: DOWNLOAD/UPGRADE as Download ops, tombstone-driven UNINSTALLs as Uninstall
  ops, and (when `upload`) local-only / local-newer pushes (`resolveUploads`) as Push ops.

### Resolver and selection (pure, unit-tested)

- **`resolveDocumentSyncActions`** — per cloud document, in order: tombstone → `UNINSTALL` if locally
  installed and the tombstone is **strictly newer** than the last sync timestamp (equal = NONE, which
  is why the initiating device doesn't re-delete); blocked → `SKIP_BLOCKED`; not installed →
  `DOWNLOAD`; cloud newer → `UPGRADE`; else `NONE`. "Newest wins" via `Version` comparison.
- **`selectSyncActions(actions, allowDownload, allowDelete)`** — drops DOWNLOAD/UPGRADE when
  `allowDownload=false` and UNINSTALL when `allowDelete=false`; SKIP_BLOCKED/NONE never execute.
- **`resolveUploads(localDocs, cloudDocs, blocked, isNewer)`** — initials to push: local-only (no cloud
  entry at all) plus local-newer-than a *live* cloud copy, minus blocked. A **tombstoned** cloud entry is
  never auto-pushed: a still-installed local copy of a document deleted elsewhere (auto-delete off, or an
  own "Remove from cloud" with the local copy kept) must not be silently resurrected by the sync cycle —
  restoring is the explicit manual Restore action, which calls `pushDocument` directly.
- **`shouldAutoUpload(enabled, autoUpload, blocked, autoTransferAllowed)`** — guards install-time auto-upload.

### Status assembly (`assembleStatusItems`, pure, unit-tested)

`buildStatusItems` extracts per-`Book` facts into a pure `LocalDoc`, then delegates to the
Android-free `assembleStatusItems(cloudMetas, localDocs, blocked, includeDeleted)`:

- `includeDeleted = false` (default): cloud tombstones are filtered out — a document tombstoned in
  the cloud but still installed locally shows as a normal local-only row (no regression).
- `includeDeleted = true`: tombstones become their own rows. A tombstone has no downloadable archive,
  so it is treated as "no live cloud copy" for the cloudOnly/update/localNewer computations and flagged
  via `DocumentStatusItem.cloudDeleted` (keeping its last-known version for display).

## Background transfers (`DocumentSyncService`)

A foreground service (`dataSync`) runs **all** document transfers and cloud mutations off the UI
thread, with one progress notification, surviving the activity lifecycle. This is deliberate: it
means a slow-network operation can't be cut short by the user navigating away (a real problem —
on a slow link the user assumes the action failed and leaves).

- **Ops:** `Push`, `Download`, `Remove`, `Purge`, `Uninstall` (a thread-safe queue; a **single** IO
  coroutine drains it strictly one-at-a-time; `START_NOT_STICKY`; partial wakelock; fresh-vs-own-drain
  guard so a finishing drain can't tear down a freshly started one). `buildDocumentSyncOps` orders a
  batch: pushes, downloads, removes, purges, uninstalls.
- **Single-threaded by design — do not parallelise the drain.** Download/Push install into one shared
  SWORD module directory and register into the global JSword `Books` singleton; concurrent installs
  would race the registry. The progress model also assumes one in-flight op.
- **Routed through it:** enable-time bulk sync, per-item download/push, Restore (= push), bulk download,
  auto-upload on install (after guards), auto-cycle downloads/uploads/uninstalls, remove-from-cloud,
  and tombstone purge. Nothing mutating runs in the activity's lifecycle scope.
- **Notification:** title "Syncing documents"; content states the direction —
  "Downloading X (n/m)" / "Uploading X (n/m)" / "Removing X (n/m)" (Remove/Purge/Uninstall all read as
  "Removing"). The progress bar is **indeterminate**. Uses the sync/calc channel and a distinct ID.
- After draining, **`refreshCache()`** so the management view reflects the new state even if it
  wasn't open during the sync.
- Posts `DocumentSyncProgressEvent(running, …)` on `ABEventBus` (running=true repeatedly per op,
  running=false once at the end); the activity re-scans on the running=false event.

`SyncService` (DB sync, "Synchronizing…") and `DocumentSyncService` are separate foreground
services with separate notifications; both can be briefly visible during an auto-sync cycle.

## Cloud-listing cache (`CacheDatabase`)

`CachedCloudDocument` (mirrors `DocumentSyncMeta`) + `CloudDocumentCacheDao` live in
`CacheDatabase` (`cloud-documents-cache.sqlite3`, `CACHE_DATABASE_VERSION` 1) — a non-backed-up,
non-synced Room DB that is the dedicated home for pure derived caches (kept separate from
`TemporaryDatabase`, which is single-purpose search scratch, so neither carries the other's schema).
Pure derived data, cleared on cloud sign-out. The DAO offers `all` / `replaceAll` / `clear`, plus
`deleteByInitials` and `markDeleted` for up-front optimistic mutations (purge / remove).

- Written whenever `scan()` / `runSync()` fetch a live listing, and after every service drain.
- **With automatic sync on**, the management view trusts the cache on open and does **not** hit the
  network (the sync cycle keeps it fresh); pull-to-refresh is available. **With sync off**, the view
  refreshes from the network on open.
- The cache includes tombstones (`listDocuments` returns all metas), so the removed-documents view and
  the up-front optimistic cache mutations work without a network round-trip.

### Parallel listing

`DocumentStore.listDocuments()` — the hot path behind scan/refreshCache/runSync/pushDocument/
downloadAndInstall/removeFromCloud — fetches each folder's `meta.json` with **bounded concurrency**
(`asyncMap(6)`) rather than one-at-a-time. Each `readMeta` is an independent read-only fetch (two
round-trips) with its own unique temp file, so the fan-out is safe; the bound of 6 matches the
proven DB-patch download concurrency in `CloudSync`. The single-threaded transfer drain is
intentionally *not* parallelised (install safety, above).

## Management view (`CloudDocumentsActivity`)

Opened from the Synced Documents settings link (and reachable whenever signed in). A unified list
of local + cloud documents.

- **Access gate (`openOrGate`):** sign in if needed; render the cached listing immediately; if not
  signed in AND no cache → toast + finish; otherwise show. Refresh from the network on open only
  when sync is off (see cache section).
- **Rows (`item_cloud_document.xml`):** type icon by `BookCategory` (matching the Download list) —
  except removed (tombstone) rows, which show a distinct **cloud-off** icon (`ic_cloud_off_24dp`).
  Name, plus a subtitle of version · size · status text. Colours match the Download Documents list
  (`@color/grey_600` icons, theme `textAppearance`). Status is conveyed by text (and the icon), never
  colour — grayscale/e-ink safe. Removed rows read "Removed from cloud" (+ "still installed here" when
  a local copy remains).
- **Filters:** name search + status spinner + category spinner. The status spinner is built
  programmatically; `CloudDocFilter.REMOVED` is the **last** enum value and its "Removed" entry is
  appended only while the "Show removed documents" toggle is on (so the position→enum mapping stays
  correct for the other filters). Removed documents appear under **ALL** and **REMOVED**, and are
  excluded from INSTALLED / CLOUD / UPDATES / BLOCKED. Turning the toggle off while REMOVED is selected
  resets the selection to ALL.
- **Show removed documents (overflow toggle):** a checkable item (per-device pref
  `showRemovedDocuments`, default off). Toggling re-renders from the **cache only**
  (`renderFromCache` → `scanCached`) behind the non-blocking loading bar — tombstones are already
  cached, so there is no network fetch.
- **Loading indicator:** a Material `LinearProgressIndicator` overlaid at the top, shown via a
  `setBusy` counter for local async ops plus a boolean for the repeating transfer events, so it stays
  on continuously across overlapping operations without flicker or getting stuck. Pull-to-refresh
  keeps its own swipe spinner. When animations are disabled (default on e-ink), the bar is set to a
  static determinate state up front rather than the animating indeterminate one — visible "working"
  feedback with no continuous motion (e-ink ghosting).
- **Per-item popup menu (`documentMenuActions`, pure + tested):** only relevant actions.
  - Normal rows: Download (cloud-only or cloud newer), Push (local-only or local newer), Remove (cloud
    copy exists), Block/Unblock. A fully-synced item has no Push.
  - Removed (tombstone) rows: **Restore to cloud** (only when installed locally — re-pushes the local
    copy via the service; no dialog, like Push) and **Remove from cloud history** (purge). No
    Download/Push/Remove/Block.
- **Remove semantics:** with sync **enabled** the action is "Remove from all devices" — tombstone +
  delete the local copy here + propagate; with sync **off** it's "Remove from cloud" — cloud only, local
  copy kept. Suppressed when it would delete an undeletable local document (e.g. the last Bible — reuses
  `Book.canDelete`). Confirmation dialog adapts to the mode. The list updates **optimistically** on
  confirm (`applyOptimisticRemoval`), with the service's completion event driving the reconciling re-scan.
- **Purge semantics:** "Remove from cloud history" deletes the tombstone meta via the service. A warning
  dialog notes that a device still holding a local copy may re-upload it on its next sync (the tombstone
  is the signal that prevents that). The list updates optimistically (`applyOptimisticPurge`): an installed
  tombstone becomes a plain local-only row, an uninstalled one drops out.
- **Block / unblock:** instant — updates the row in memory (a local SharedPreferences set), no network.
- **Selection mode:** entered by long-press (no menu item). Only downloadable items are selectable;
  already-installed/synced rows are dimmed with the checkbox reserved (INVISIBLE). Bulk action downloads
  the selected items. The overflow menu hides in selection mode.
- **"Sync now"** (overflow, shown whenever signed in, hidden in selection mode): a full manual sync with
  an **operation picker** — an AlertDialog with Download / Upload / Delete checkboxes, pre-filled from the
  remembered last choice (`syncNow*` prefs). Confirm runs `runSync(download, upload, delete, manual=true)`,
  bypassing the `enabled` and Wi-Fi-only guards but honouring the block list and tombstones. This makes
  "Sync now" genuinely bidirectional (it previously never uploaded).

## Network policy & block list

- **Wi-Fi-only** (default on): automatic transfers wait for an unmetered connection
  (`isAutoTransferAllowed = !wifiOnly || !isMeteredNetwork`); manual actions always proceed.
- **Block list:** a per-device SharedPreferences set of initials that won't auto-download to this
  device. Not synced. Honoured by the resolver and `shouldAutoUpload`, and by `resolveUploads`.

## Auto paths

- **Install:** `BookInstallWatcher.bookAdded` → `shouldAutoUpload(enabled, autoUpload, blocked,
  autoTransferAllowed)` → `DocumentSyncService.start(push=[initials])`.
- **Sync cycle:** `CloudSync.synchronize()` calls `DocumentSync.runSync(autoDownload, autoUpload,
  autoDelete, manual = false)` after the DB sync; all resulting work (downloads, uploads, uninstalls)
  is enqueued on the service.

## Testing

Kotlin-only → `./gradlew testStandardGoogleplayDebugUnitTest`. Pure, unit-tested logic:

- `resolveDocumentSyncActions` — download/upgrade/uninstall/skip/none, incl. the equal-timestamp
  tombstone invariant.
- `selectSyncActions` — download-off drops DOWNLOAD/UPGRADE; delete-off drops UNINSTALL.
- `resolveUploads` — local-only + local-newer included; fully-synced / cloud-only / blocked / tombstoned
  excluded (a still-installed local copy is never auto-pushed over a tombstone).
- `buildDocumentSyncOps` — push → download → remove → purge → uninstall order.
- `shouldAutoUpload` — guard combinations incl. the `autoUpload` gate.
- `computeDocumentSyncSummary` — uploads (local-only + local-newer) / downloads split + sizes,
  block-list exclusion. The upload split mirrors `resolveUploads`.
- `assembleStatusItems` — tombstone include/exclude by `includeDeleted`; tombstone+local → local-only +
  `cloudDeleted`; no-regression on the default path.
- `documentMenuActions` — relevant actions per status, last-Bible suppression, and tombstone rows
  (Restore only when installed locally; Purge always; never Download/Push/Remove/Block).
- `applyOptimisticRemoval` / `applyOptimisticPurge` — expected list state per sync/install state.
- `filterCloudDocuments` — status (incl. REMOVED) × name × category.
- `CloudDocumentCacheMapping` — `DocumentSyncMeta` ↔ `CachedCloudDocument` round-trip.

Service/activity wiring (dialogs, sign-in/cache gate, notification, loading bar, selection mode,
spinner rebuild, the operation picker, the show-removed toggle) is verified by manual on-device testing.

## Key components

- `service/cloudsync/documents/`: `DocumentSync` (`runSync`, `scan`/`scanCached` with `includeDeleted`,
  `assembleStatusItems`/`LocalDoc`, `pushDocument`, `downloadAndInstall`, `removeFromCloud`,
  `purgeTombstone`, `uninstallLocal`), `DocumentSyncService` (Push/Download/Remove/Purge/Uninstall ops),
  `DocumentSyncOps` (`buildDocumentSyncOps`, `selectSyncActions`, `shouldAutoUpload`),
  `DocumentSyncResolver` (`resolveDocumentSyncActions`, `resolveUploads`), `DocumentSyncMeta`,
  `DocumentSyncSettings` (three auto toggles + `showRemovedDocuments` + remembered Sync-now choice),
  `DocumentStore` (parallel `listDocuments`, `deleteDocument`), `DocumentArchiver`, `DocumentBlockList`,
  `CloudDocumentCacheMapping`.
- `database/`: `CachedCloudDocument` + `CloudDocumentCacheDao` (`deleteByInitials`, `markDeleted`) in
  `CacheDatabase` (dedicated pure-derived-cache DB, separate from `TemporaryDatabase`).
- `view/activity/cloud/`: `CloudDocumentsActivity` (`documentMenuActions`, `filterCloudDocuments`,
  `applyOptimisticRemoval`/`applyOptimisticPurge`, `setupStatusFilter`, `renderFromCache`,
  `CloudDocAction`, `CloudDocFilter`), `CloudDocumentsAdapter`.
- `res/`: `ic_cloud_off_24dp`, `sync_settings.xml`, `item_cloud_document.xml`, `strings.xml`.
- Touchpoints: `BookInstallWatcher`, `CloudSync.synchronize`, `SyncSettings`, `DocumentControl.canDelete`.

## Future considerations

- Per-document byte transfer progress (needs a cloud-adapter progress callback).
- Total cloud storage display / cap warning for very large stores.
- A bulk "purge all tombstones" action, and a removed-count indicator, if tombstones get numerous.
- De-duplicate the per-push `listDocuments()` in large bulk uploads (list once, thread metas through
  the batch) — only if bulk uploads prove slow after the parallel-listing win.
- Confirm `OwnCloudClient` thread-safety for the unbounded NextCloud `listFiles` fan-out (a DB-sync
  concern, not document sync).
