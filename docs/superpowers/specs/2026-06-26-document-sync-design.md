# Document Sync — Consolidated Design

**Date:** 2026-06-26
**Status:** Implemented

This is the single source of truth for the document-sync feature. It consolidates and
supersedes the earlier design notes (initial design, testing corrections, background
service, and settings-UX simplification), folding in the refinements made during
hands-on testing. It describes the feature as built.

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

**Core model:** when document sync is enabled, behaviour is fully automatic — sync
everything. The user does not choose what syncs. Per-document control (block a document
from this device, remove from the cloud) and manual sync happen afterwards in the Synced
Documents management view.

## Goals

- Sync all document types and all install methods.
- Track and propagate versions ("newest wins", auto-upgrade on other devices).
- Per-device opt-out (block a document from auto-downloading to *this* device).
- A management view of the cloud store + local documents, with manual actions.
- Network-aware: Wi-Fi-only option for automatic transfers.
- All transfers run in the background (foreground service) with a progress notification;
  the UI never blocks.

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
- `deleted: true` is a tombstone for deletion propagation.
- `size` is the exact packaged (zip) size; the management view falls back to the installed
  module size (`SwordBookMetaData` install size) for a local-only document not yet uploaded,
  so its upload size isn't shown as 0.

## Settings (`sync_settings.xml`, `SyncSettings.kt`)

There is **no separate "Automatic" toggle** — sync is always automatic when enabled.
Document-sync preferences live in two groups:

```
General sync
  (adapter, reset, cloud info, server/username/password/folder)

Synchronization categories
  Bookmarks · Workspaces · Reading plans · My documents · AI settings · Progress
  Documents                       ← master toggle, alongside the others

Document sync   (category shown only when signed in)
  Sync documents on Wi-Fi only    ← shown only while Documents sync is enabled
  Synced documents  ▸             ← opens the management view (icons: Wi-Fi, cloud)
```

- The **Document sync** category is visible whenever signed in (`CloudSync.signedIn`, a
  proxy for "a cloud adapter is configured" — `cloudAdapter` is internal). This keeps
  **Synced documents** reachable for manual sync even when automatic sync is off.
- **Wi-Fi-only** is shown only while document sync is enabled.

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

- **`scan()`** — when signed in, fetch the live cloud listing AND refresh the cache; offline,
  read the cache. Builds the unified status list (`DocumentStatusItem`) via `buildStatusItems`,
  combining cloud metas with locally installed books (excluding pseudo-books and MyDocuments).
- **`scanCached()`** — cache-only, no network; used to render the view instantly on open.
- **`refreshCache()`** — refresh the cloud-listing cache from the network (called after every
  service drain, and on every `pullDocuments`).
- **`pushDocument(book)`** — package + upload if the cloud is missing/older; record sync timestamp.
- **`downloadAndInstall(initials)`** — download newest zip + install.
- **`removeFromCloud(initials)`** — write a tombstone (newest timestamp). With sync **enabled**,
  also delete the local copy on this device (so it isn't orphaned); with sync **off**, keep the
  local copy. Records this device's sync timestamp = tombstone timestamp so its own next pull is
  a no-op.
- **`pullDocuments(automaticOnly)`** — the scheduled (automatic) pull requires `enabled` +
  `isAutoTransferAllowed`; manual "Sync now" (`automaticOnly = false`) bypasses both. Refreshes
  the cache, resolves actions, performs uninstalls inline, and enqueues downloads/upgrades on
  the service.

### Resolver (`resolveDocumentSyncActions`)

Per cloud document, in order: tombstone → `UNINSTALL` if locally installed and the tombstone is
**strictly newer** than the last sync timestamp (equal = NONE, which is why the initiating
device doesn't re-delete); blocked → `SKIP_BLOCKED`; not installed → `DOWNLOAD`; cloud newer →
`UPGRADE`; else `NONE`. "Newest wins" via `Version` comparison.

## Background transfers (`DocumentSyncService`)

A foreground service (`dataSync`) runs **all** document transfers off the UI thread, with one
progress notification, surviving the activity lifecycle.

- **Ops:** `Push`, `Download`, `Remove` (a thread-safe queue; a single IO coroutine drains it;
  `START_NOT_STICKY`; partial wakelock; fresh-vs-own-drain guard so a finishing drain can't tear
  down a freshly started one).
- **Routed through it:** enable-time bulk sync, per-item download/push, bulk download, auto-upload
  on install (after guards), auto-pull downloads, and remove-from-cloud.
- **Notification:** title "Syncing documents"; content states the direction —
  "Downloading X (n/m)" / "Uploading X (n/m)" / "Removing X (n/m)". The progress bar is
  **indeterminate** (per-document byte progress isn't available; an op count would sit at 100%
  for a single document). Uses the sync/calc channel and a distinct notification ID.
- After draining, **`refreshCache()`** so the management view reflects the new state even if it
  wasn't open during the sync.
- Posts `DocumentSyncProgressEvent(running, …)` on `ABEventBus` (running=true repeatedly per op,
  running=false once at the end).

`SyncService` (DB sync, "Synchronizing…") and `DocumentSyncService` are separate foreground
services with separate notifications; both can be briefly visible during an auto-sync cycle.
DB sync stays generic because it syncs categories in parallel (`asyncMap`).

## Cloud-listing cache (`TemporaryDatabase`)

`CachedCloudDocument` (mirrors `DocumentSyncMeta`) + `CloudDocumentCacheDao` (replace-all / all /
clear) live in `TemporaryDatabase` — a non-backed-up, non-synced Room DB — in a dedicated
instance `cloud-documents-cache.sqlite3` (`TEMPORARY_DATABASE_VERSION` 2, migration adds the
table). Pure derived data.

- Written whenever `scan()`/`pullDocuments()` fetch a live listing, and after every service drain.
- **With automatic sync on**, the management view trusts the cache on open and does **not** hit
  the network (the sync cycle keeps it fresh); pull-to-refresh is available. **With sync off**,
  the view refreshes from the network on open.

## Management view (`CloudDocumentsActivity`)

Opened from the Synced Documents settings link (and reachable whenever signed in). A unified list
of local + cloud documents.

- **Access gate (`openOrGate`):** sign in if needed; render the cached listing immediately; if not
  signed in AND no cache → toast + finish; otherwise show. Refresh from the network on open only
  when sync is off (see cache section).
- **Rows (`item_cloud_document.xml`):** type icon by `BookCategory` (matching the Download list),
  name, and a subtitle of version · size · status text. Colours match the Download Documents list
  (`@color/grey_600` icons, theme `textAppearance`). No separate status icon — status is in the
  subtitle text. Grayscale/e-ink safe.
- **Filters:** name search + status spinner + category spinner.
- **Loading indicator:** a Material `LinearProgressIndicator` overlaid at the top (flush under the
  title bar), shown via a `setBusy` counter for local async ops plus a boolean for the repeating
  transfer events, so it stays on continuously across overlapping operations without flicker and
  without getting stuck. Pull-to-refresh keeps its own swipe spinner.
- **Per-item popup menu (`documentMenuActions`, pure + tested):** only relevant actions —
  Download (cloud-only or cloud newer), Push (local-only or local newer), Remove (cloud copy
  exists), Block/Unblock (cloud copy exists). A fully-synced item has no Push.
- **Remove semantics:** with sync **enabled** the action is "Remove from all devices" — tombstone
  + delete the local copy here + propagate to other devices; with sync **off** it's "Remove from
  cloud" — cloud only, local copy kept. Suppressed when it would delete an undeletable local
  document (e.g. the last Bible — reuses `Book.canDelete`). Confirmation dialog adapts to the mode.
  The list updates **optimistically** on confirm (`applyOptimisticRemoval`), with the
  post-completion refresh confirming/reverting.
- **Block / unblock:** instant — updates the row in memory (a local SharedPreferences set), no
  network re-scan.
- **Selection mode:** entered by long-press (no menu item). Only downloadable items are
  selectable; already-installed/synced rows are dimmed with the checkbox reserved (INVISIBLE) so
  the layout stays aligned. Bulk action downloads the selected items. Long-pressing when nothing
  is downloadable shows an "all already downloaded" toast. The overflow menu hides in selection
  mode.
- **"Sync now"** (overflow, shown only when automatic sync is off): runs a full manual
  pull+push cycle via the service, bypassing Wi-Fi-only, honouring the block list and tombstones.

## Network policy & block list

- **Wi-Fi-only** (default on): automatic transfers wait for an unmetered connection
  (`isAutoTransferAllowed = !wifiOnly || !isMeteredNetwork`); manual actions always proceed.
- **Block list:** a per-device SharedPreferences set of initials that won't auto-download to this
  device. Not synced. Honoured by the resolver and `shouldAutoUpload`.

## Auto paths

- **Install:** `BookInstallWatcher.bookAdded` → `shouldAutoUpload(enabled, blocked,
  autoTransferAllowed)` → `DocumentSyncService.start(push=[initials])`.
- **Sync cycle:** `CloudSync.synchronize()` calls `DocumentSync.pullDocuments(automaticOnly = true)`
  after the DB sync; downloads/upgrades go to the service, uninstalls run inline.

## Testing

Kotlin-only → `./gradlew testStandardGoogleplayDebugUnitTest`. Pure, unit-tested logic:

- `resolveDocumentSyncActions` — download/upgrade/uninstall/skip/none, including the
  equal-timestamp tombstone invariant.
- `buildDocumentSyncOps` — push-then-download-then-remove order.
- `shouldAutoUpload` — guard combinations.
- `computeDocumentSyncSummary` — uploads/downloads split + sizes, block-list exclusion.
- `documentMenuActions` — relevant actions per status, last-Bible suppression by sync state.
- `applyOptimisticRemoval` — drop vs local-only by sync state.
- `filterCloudDocuments` — status × name × category.
- `CloudDocumentCacheMapping` — `DocumentSyncMeta` ↔ `CachedCloudDocument` round-trip.

Service/activity wiring (dialogs, sign-in/cache gate, notification, loading bar, selection mode)
is verified by manual on-device testing.

## Key components

- `service/cloudsync/documents/`: `DocumentSync`, `DocumentSyncService`, `DocumentSyncOps`,
  `DocumentSyncResolver`, `DocumentSyncMeta`, `DocumentSyncSettings`, `DocumentStore`,
  `DocumentArchiver`, `DocumentBlockList`, `CloudDocumentCacheMapping`.
- `database/`: `CachedCloudDocument` + `CloudDocumentCacheDao` in `TemporaryDatabase`.
- `view/activity/cloud/`: `CloudDocumentsActivity`, `CloudDocumentsAdapter`.
- Touchpoints: `BookInstallWatcher`, `CloudSync.synchronize`, `SyncSettings`, `sync_settings.xml`,
  `DocumentControl.canDelete`, `strings.xml`.

## Future considerations

- Per-document byte transfer progress (needs a cloud-adapter progress callback).
- Total cloud storage display / cap warning for very large stores.
