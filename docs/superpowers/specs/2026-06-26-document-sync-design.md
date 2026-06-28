# Document Sync — Consolidated Design

**Date:** 2026-06-26 (last consolidated 2026-06-28)
**Status:** Implemented

This is the single source of truth for the document-sync feature. It consolidates and
supersedes all earlier design notes — the initial design, testing corrections, background
service, settings-UX simplification, the **per-device direction control** notes, the
**removed-documents view** notes, the **incremental cloud-listing** design, the
**"Sync now" preview & cloud-storage-accounting** follow-up, and the **management-view
improvements** (device-only "do not sync to cloud", bulk actions via a Contextual Action
Bar, and the "Only on this device" filter) — folding in the refinements
made during hands-on testing. It describes the feature as built.

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
- The **cloud info** line (`cloud_sync_info` = `CloudSync.bytesUsed()`) now includes the space
  used by synced *document archives*, not just the synced databases (bookmarks, workspaces, …).
  `CloudSync.bytesUsed()` adds `DocumentSync.cloudBytesUsed()`, which sums the cloud ZIP sizes of
  non-deleted documents from the **local listing cache** (no network) — fast and consistent with
  the existing instant DB-size reads. The summed quantity is `DocumentSyncMeta.size` (the exact
  packaged ZIP size recorded at push time), **not** the unpacked install size; tombstones are
  excluded. If the cache has never been populated the contribution is 0. There is deliberately no
  network refresh when opening Sync settings (cache-based by decision).

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
- **`refreshCache()`** — **incrementally** refresh the cloud-listing cache from the network (called
  after every service drain, and on every sync run): fetch only metas changed since the stored
  watermark, merge them into the cache (upsert + purge) via the pure `mergeCloudListing`, and advance
  the watermark. With nothing changed this is two round-trips and no downloads. See *Cloud-listing
  cache* below.
- **`resetListingCache()`** — clears the cache + watermark (keeping user prefs) so the next
  `scan()`/`refreshCache()` cold-starts a full authoritative listing. Backs the manual "Re-scan from
  cloud" action; recovery path for a rare clock-skew silent miss.
- **`pushDocument(book)`** — package + upload if the cloud is missing/older; record sync timestamp.
  Re-checks the cloud (skips packaging if same/newer already there). Used for Push and Restore.
- **`downloadAndInstall(initials)`** — download newest zip + install (integrity-checks the zip size
  against the meta before installing; `installingFromSync` suppresses the echo auto-push).
- **`removeFromCloud(initials)`** — write a tombstone (newest timestamp). With sync **enabled**,
  also delete the local copy on this device (so it isn't orphaned); with sync **off**, keep the
  local copy. Records this device's sync timestamp = tombstone timestamp so its own next pull is
  a no-op. **Marks the cached entry `deleted` up front** (before the slow network writes) so the
  management view is correct if reopened mid-operation; if the cloud tombstone write fails the mark is
  reverted (otherwise an incremental refresh wouldn't re-fetch the still-live meta to self-correct).
- **`purgeTombstone(initials)`** — permanently delete a tombstone's whole cloud folder
  (`DocumentStore.deleteDocument`), then `refreshCache()`. **Deletes the cached entry up front**
  for the same reopen-correctness reason. Manual only.
- **`uninstallLocal(initials)`** — uninstall the local copy after a remote removal (tombstone-driven),
  honouring the last-deletable guard (never removes the last Bible). Looks the book up by initials so
  it can run from the service queue.
- **`computeSyncPlan(download, upload, delete)`** — the single source of truth for "what would this
  sync do", shared by both the actual run and the manual "Sync now" preview. Contains the resolution
  body: `store()` guard → `refreshCache()` → build `cloudDocs` / `localDocs` / `blocked` /
  `syncTimestamps` → `selectSyncActions(resolveDocumentSyncActions(...))` → derive `toDownload`
  (DOWNLOAD/UPGRADE), `toUninstall` (UNINSTALL), and (when `upload`) `toUpload` via `resolveUploads`.
  It also sums transfer bytes into a `SyncPlan(toDownload, toUpload, toUninstall, downloadBytes,
  uploadBytes)`: `downloadBytes` = cloud ZIP sizes of `toDownload` (from the cloud metas),
  `uploadBytes` = local install sizes of `toUpload` (`localInstallSizeBytes`, the same estimate as the
  enable dialog). The pure helper `sumPlanBytes(initials, sizeByInitials)` does the summing and is
  unit-tested. When `store()` is null it returns an empty plan (empty lists, zero bytes).
- **`runSync(download, upload, delete, manual)`** — the single code path for both the automatic
  cycle and manual "Sync now" (generalises the former `pullDocuments`). Automatic runs require
  `enabled` + `isAutoTransferAllowed`; a `manual` run bypasses both. It delegates to
  `computeSyncPlan(...)` and **enqueues all of the resulting work on the service**: `toDownload` as
  Download ops, `toUninstall` (tombstone-driven) as Uninstall ops, and `toUpload` as Push ops.
  Because the plan is derived from the same resolver path, a removal intent is respected (a
  document tombstoned in the cloud but still installed locally is **not** re-pushed).

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

## Per-device state & cloud-listing cache (`DocumentSyncDatabase`)

All of document-sync's per-device state lives in one Room database, **`DocumentSyncDatabase`**
(`document-sync.sqlite3`, `DOCUMENT_SYNC_DATABASE_VERSION` 1) — **not backed up** (absent from
`ALL_DB_FILENAMES`), **not synced** (absent from `SyncableDatabaseDefinition`), and **cleared in its
entirety on cloud sign-out**. Document-sync state is entirely device-local (the cloud account itself is
re-established per device), so there is nothing to back up or sync; co-locating it makes that lifecycle
explicit. Four tables:

- **`DocumentSyncPreferences`** — a singleton entity (fixed `@PrimaryKey`), `AiSettings`-style (DAO
  `get(): DocumentSyncPreferences?` + `@Insert REPLACE`; the `DocumentSyncSettings` accessor reads
  `dao.get() ?: DocumentSyncPreferences()` and writes `update { copy(...) }`). Holds the **user
  preferences**: `enabled`, `wifiOnly`, `autoDownload`, `autoUpload`, `autoDelete`, `syncNow*`,
  `showRemovedDocuments`, and `blockList: Set<String>`. (The entity is `DocumentSyncPreferences`;
  `DocumentSyncSettings` is the accessor `object` over it.)
- **`CloudListingState`** — a second singleton holding the operational **listing watermark**
  (`watermark: Long`). Kept separate from prefs: it is derived sync state, and it must share the
  cache's lifecycle (a stale watermark over an empty cache would silently hide documents).
- **`CloudDocumentSyncTimestamp(initials @PrimaryKey, timestamp)`** — per-document last-sync timestamps.
- **`CachedCloudDocument`** (mirrors `DocumentSyncMeta`) + `CloudDocumentCacheDao`: the cloud-listing
  cache. DAO offers `all` / `insertAll` / `replaceAll` / `clear`, plus `deleteByInitials` and
  `markDeleted` for up-front optimistic mutations (purge / remove; reverted if the cloud write fails).

Cache behaviour:
- Refreshed incrementally whenever `scan()` / `runSync()` fetch a live listing, and after every service
  drain (see *Incremental listing* below).
- **With automatic sync on**, the management view trusts the cache on open and does **not** hit the
  network (the sync cycle keeps it fresh); pull-to-refresh is available. **With sync off**, the view
  refreshes from the network on open.
- The cache includes tombstones, so the removed-documents view and the up-front optimistic cache
  mutations work without a network round-trip.

**Sign-out** wipes the whole `DocumentSyncDatabase` (prefs + cache + watermark + timestamps) in one
transaction, and `CloudSync.signOut()` first stops any running `DocumentSyncService`. The next sign-in
starts from defaults — correct, because it may target a different cloud account. (No migration of the
old `SettingsDatabase` keys / `cloud-documents-cache.sqlite3` file: document sync is unreleased, so
those are left dead and the orphaned cache file is deleted on first run.)

### Incremental listing

Listing the cloud store used to download **every** `meta.json` (`~1 + 2N` round-trips for `N`
documents) on every sync cycle and after every service drain — so a no-op sync still paid the full
cost. This now mirrors DB sync's minimal listing:

- **`DocumentStore.listChangedDocuments(watermark)`** — one `getFolders(root)` (current folders →
  new-document discovery + purge detection) + one batched `listFiles(name = meta.json,
  createdTimeAtLeast = max(0, watermark − MARGIN))`, then download/parse **only** the changed metas
  (bounded concurrency, `asyncMap(6)`; each `readMeta` is an independent read-only fetch with its own
  unique temp file, so the fan-out is safe). Returns the parsed metas, the set of all current folder
  names, and the matched/failed `createdTime`s. `listDocuments()` is just `listChangedDocuments(0)`,
  and **cold start is `watermark = 0`** (returns everything) — same code path.
- This works because `DocumentStore.writeMeta` commits a meta change by **deleting and re-uploading**
  `meta.json`, so every mutation (upload, upgrade, tombstone) gets a fresh `createdTime` — exactly what
  `createdTimeAtLeast` needs.
- **`mergeCloudListing` (pure, unit-tested)** — upserts changed metas, purges cache rows whose folder
  vanished (`initials ∉ currentInitials`), and advances the watermark **without ever stepping over a
  failure**: no failures → `max(matchedCreatedTimes)`; some failures → `min(failedCreatedTimes) − 1`
  (advance past every strictly-older success, leave the earliest failure and newer to be re-fetched).
  Floored at the old watermark, so it never regresses.
- **Trailing MARGIN** (a few seconds): `createdTimeAtLeast = watermark − MARGIN` re-queries a small
  window each cycle to absorb NextCloud's one-second timestamp resolution and minor clock skew (the
  same accepted risk DB sync runs). Re-reading a cached meta is idempotent. The **"Re-scan from cloud"**
  reset action (`resetListingCache`, cold-starts a full listing) is the explicit recovery for the rare
  silent-miss case — a path DB sync does not even offer.

The single-threaded transfer drain is intentionally *not* parallelised (install safety, above).
Per-operation single-document reads (`pushDocument`'s cloud re-check, `removeFromCloud`,
`downloadAndInstall`) touch one folder and are unchanged.

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
  a local copy remains). A device-only document the user has marked "do not sync to cloud" (blocked
  while local-only) reads "Won't sync to cloud"; a blocked cloud-backed document reads "Blocked".
- **Filters:** name search + status spinner + category spinner. Status filters are
  `ALL · INSTALLED · CLOUD · UPDATES · BLOCKED · DEVICE_ONLY · REMOVED`. **Only on this device**
  (`DEVICE_ONLY = localOnly && !cloudDeleted`) shows documents installed here but absent from the cloud
  — the strict subset of INSTALLED (`!cloudOnly && !cloudDeleted`) that excludes cloud-backed local
  copies; the candidates for pushing or for marking "do not sync to cloud". The status spinner is built
  programmatically; `CloudDocFilter.REMOVED` is the **last** enum value (with `DEVICE_ONLY` immediately
  before it) and its "Removed" entry is appended only while the "Show removed documents" toggle is on
  (so the position→enum mapping stays correct for the other filters). Removed documents appear under
  **ALL** and **REMOVED**, and are excluded from INSTALLED / CLOUD / UPDATES / BLOCKED / DEVICE_ONLY.
  Turning the toggle off while REMOVED is selected resets the selection to ALL.
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
- **Per-item popup menu (`documentMenuActions`, pure + tested):** only relevant actions. Labels are
  resolved by the pure `actionLabelRes(action, localOnly, syncEnabled)`.
  - Normal rows: Download (cloud-only or cloud newer), Push (local-only or local newer), Remove (cloud
    copy exists), Block/Unblock. A fully-synced item has no Push. Block/Unblock are offered for **every**
    row (including device-only). The label is context-sensitive: for a **local-only** row, Block reads
    "Do not sync to cloud" / Unblock "Sync to cloud"; for a **cloud-backed** row, "Block" / "Unblock"
    (which also blocks auto-download to this device).
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
- **Block / unblock (incl. "do not sync to cloud"):** instant — updates the row in memory (a local set
  in `DocumentSyncDatabase`), no network. The same per-device block list backs both directions:
  blocking a device-only document excludes it from auto-upload (`resolveUploads`), blocking a
  cloud-backed one also excludes it from auto-download (the resolver). One concept, context-sensitive
  wording.
- **Selection mode (Contextual Action Bar):** entered by long-press (no menu item); **every** row is
  selectable (checkbox shown for all rows, no dimming). The CAB title shows the selected count and
  overlays the app bar (so the normal overflow is hidden). Its actions are the **union** of the
  per-item actions over the selection (`bulkMenuActions`, pure + tested): Download / Upload / Remove as
  ActionBar icons, and Do-not-sync / Allow-sync / Restore / Purge in the overflow. An action is offered
  when **at least one** selected row supports it; running it applies only to that supporting subset
  (`applicableInitials`, pure + tested), and a brief toast reports any skipped rows ("Skipped N not
  applicable"). Remove and Purge confirm with a count-based dialog (the single-item last-Bible guard is
  inherited via `documentMenuActions`, so an undeletable Bible is simply never in the Remove subset);
  the rest dispatch straight to `DocumentSyncService`. The selection is resolved against the full list,
  not the filtered view, so it survives a filter change.
- **"Sync now"** (overflow, shown whenever signed in, hidden in selection mode): a full manual sync with
  an **operation picker that previews the transfer**. Behind the non-blocking loading bar it computes
  the plan once with all three directions on (`DocumentSync.computeSyncPlan(true, true, true)`, on
  `Dispatchers.IO`), so every row's count is available regardless of the remembered checkbox state.
  The AlertDialog shows the three Download / Upload / Delete checkboxes (pre-filled from the remembered
  `syncNow*` prefs), each label keeping its descriptive sentence plus a second line with the count:
  Download from `plan.toDownload`/`downloadBytes`, Upload from `plan.toUpload`/`uploadBytes`, Delete
  from `plan.toUninstall` (**count only, no size** — a removal transfers nothing measurable). The count
  line uses the `cloud_doc_sync_now_count_size` plural when count > 0 and bytes > 0, the
  `cloud_doc_sync_now_count` plural when count > 0 but size is unknown (bytes == 0), and
  `cloud_doc_sync_now_count_none` ("Nothing to transfer") when count == 0 (`getQuantityString` +
  `Formatter.formatShortFileSize`; `CheckedTextView` wraps the two-line label). On confirm it persists
  the checkbox states and starts the transfer **directly from the already-computed plan** (including
  only the checked directions) — `DocumentSyncService.start(...)` with no second `refreshCache`/network
  round-trip. Starting the service directly (rather than `runSync(manual = true)`) keeps the manual
  semantics: the block list is already applied during resolution and manual transfers bypass the
  `enabled`/Wi-Fi guards. This makes "Sync now" genuinely bidirectional (it previously never uploaded).
- **Overflow order & icons:** Sync now → Re-scan from cloud → Show removed documents, each with an icon
  (forced visible in the ActionBar overflow via `onMenuOpened` + `setOptionalIconsVisible`).
- **"Re-scan from cloud"** (overflow): calls `resetListingCache()` then a full `scan()` — clears the
  cache + watermark and cold-starts an authoritative listing. The recovery for a clock-skew silent miss.

## Network policy & block list

- **Wi-Fi-only** (default on): automatic transfers wait for an unmetered connection
  (`isAutoTransferAllowed = !wifiOnly || !isMeteredNetwork`); manual actions always proceed.
- **Block list:** a per-device set of initials (stored in `DocumentSyncDatabase`) that this device opts
  out of syncing — excluded from auto-download **and** auto-upload **and** tombstone-driven uninstall.
  Not synced. Honoured by the resolver, `shouldAutoUpload`, and `resolveUploads`. Surfaced in the
  management view per row: as "Block" for a cloud-backed document (no auto-download here) and as
  "Do not sync to cloud" for a device-only document (no auto-upload).

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
- `sumPlanBytes` — missing initials contribute 0, sums match the size map, empty input = 0.
- `sumCloudBytes` — non-deleted metas summed, tombstones excluded, empty list = 0.
- `assembleStatusItems` — tombstone include/exclude by `includeDeleted`; tombstone+local → local-only +
  `cloudDeleted`; no-regression on the default path.
- `documentMenuActions` — relevant actions per status, last-Bible suppression, Block/Unblock offered
  for local-only rows too, and tombstone rows (Restore only when installed locally; Purge always; never
  Download/Push/Remove/Block).
- `actionLabelRes` — context-sensitive Block/Unblock labels (local-only "do not sync to cloud" vs
  cloud "block"), and Remove wording by `syncEnabled`.
- `bulkMenuActions` / `applicableInitials` — union of per-item actions over a heterogeneous selection
  in canonical order; per-action applicable subset (download skips non-downloadable, block skips
  already-blocked, remove respects the last-Bible guard).
- `applyOptimisticRemoval` / `applyOptimisticPurge` — expected list state per sync/install state.
- `filterCloudDocuments` — status (incl. REMOVED and DEVICE_ONLY) × name × category; DEVICE_ONLY is the
  strict local-only subset distinct from INSTALLED; tombstones excluded from every non-removed status
  filter even when they carry blocked/update flags.
- `CloudDocumentCacheMapping` — `DocumentSyncMeta` ↔ `CachedCloudDocument` round-trip.
- `mergeCloudListing` — upsert of changed metas; purge of vanished folders; watermark advances to the
  max matched `createdTime` with no failures, and only to just below the earliest failure otherwise
  (never steps over a failure, never pinned by one); cold start (`watermark = 0`) ingests the full set.
- `DocumentSyncSettings` / `DocumentSyncEntities` — singleton get-or-default, independent-column
  round-trips, per-key timestamp isolation, watermark + block-list round-trip.
- Sign-out wipes the whole `DocumentSyncDatabase` (prefs + cache + watermark + timestamps).

Service/activity wiring (dialogs, sign-in/cache gate, notification, loading bar, selection mode,
spinner rebuild, the operation picker with its transfer preview, the show-removed toggle, the
re-scan/reset action, the cloud-storage figure including document archives, and the adapter
`createdTimeAtLeast` server-side filtering on both Google Drive and NextCloud) is verified by
manual on-device testing. `computeSyncPlan` and `cloudBytesUsed` touch the DB/network/`Context`
and are covered by the build + manual check (their pure pieces — `sumPlanBytes`, `sumCloudBytes`,
and the resolver functions — are unit-tested).

## Key components

- `service/cloudsync/documents/`: `DocumentSync` (`runSync`, `computeSyncPlan`/`SyncPlan`/`sumPlanBytes`,
  `cloudBytesUsed`/`sumCloudBytes`, `scan`/`scanCached` with `includeDeleted`,
  `refreshCache` (incremental), `resetListingCache`, `onSignOut`, `assembleStatusItems`/`LocalDoc`,
  `pushDocument`, `downloadAndInstall`, `removeFromCloud`, `purgeTombstone`, `uninstallLocal`),
  `DocumentSyncService` (Push/Download/Remove/Purge/Uninstall ops; `start`/`stop`),
  `DocumentSyncOps` (`buildDocumentSyncOps`, `selectSyncActions`, `shouldAutoUpload`),
  `DocumentSyncResolver` (`resolveDocumentSyncActions`, `resolveUploads`),
  `DocumentListingMerge` (`mergeCloudListing`), `DocumentSyncMeta`,
  `DocumentSyncSettings` (accessor over the `DocumentSyncDatabase` singletons: toggles, watermark,
  per-doc timestamps, block list, remembered Sync-now choice),
  `DocumentStore` (`listChangedDocuments`/`listDocuments`, `readMeta`, `writeMeta`, `writeTombstone`,
  `deleteDocument`), `DocumentArchiver`, `DocumentBlockList`, `CloudDocumentCacheMapping`.
- `database/`: `DocumentSyncDatabase` (`document-sync.sqlite3`; not backed up / not synced / wiped on
  sign-out) holding `DocumentSyncPreferences` + `CloudListingState` singletons,
  `CloudDocumentSyncTimestamp`, and `CachedCloudDocument` + `CloudDocumentCacheDao`
  (`deleteByInitials`, `markDeleted`); registered in `DatabaseContainer` (closed on reset; first-run
  deletion of the old `cloud-documents-cache.sqlite3`).
- `view/activity/cloud/`: `CloudDocumentsActivity` (`documentMenuActions`, `actionLabelRes`,
  `bulkMenuActions`/`applicableInitials`, `filterCloudDocuments`,
  `applyOptimisticRemoval`/`applyOptimisticPurge`, `setupStatusFilter`, `renderFromCache`,
  the re-scan/reset action, the selection-mode `ActionMode` Contextual Action Bar, `CloudDocAction`,
  `CloudDocFilter` incl. `DEVICE_ONLY`), `CloudDocumentsAdapter` (every row selectable in selection mode).
- `res/`: `ic_cloud_off_24dp`, `ic_cloud_download_24dp`, `ic_cloud_upload_24dp`, `ic_delete_24dp`,
  `sync_settings.xml`, `item_cloud_document.xml`, `menu/cloud_documents_selection.xml` (the CAB menu),
  `strings.xml`.
- Touchpoints: `BookInstallWatcher`, `CloudSync.synchronize`/`signOut`/`bytesUsed`, `SyncSettings`,
  `DocumentControl.canDelete`.

## Future considerations

- Per-document byte transfer progress (needs a cloud-adapter progress callback).
- A cloud-storage cap warning for very large stores (the total is now displayed in Sync settings).
- A bulk "purge all tombstones" action, and a removed-count indicator, if tombstones get numerous.
- De-duplicate the per-push `listDocuments()` in large bulk uploads (list once, thread metas through
  the batch) — only if bulk uploads prove slow after the parallel-listing win.
- Confirm `OwnCloudClient` thread-safety for the unbounded NextCloud `listFiles` fan-out (a DB-sync
  concern, not document sync).
- A per-document sequence number in `meta.json` could make incremental detection skew-proof (like DB
  sync's patch numbers), removing the residual NextCloud clock-skew risk — only worth it if skew misses
  prove real in practice.
- If a generic (non-document-sync) derived cache is ever needed, it gets its own database rather than
  reusing `DocumentSyncDatabase`.
