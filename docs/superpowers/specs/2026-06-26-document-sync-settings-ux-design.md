# Document Sync — Settings UX Simplification Design

**Date:** 2026-06-26
**Status:** Approved (design phase)
**Builds on:** [2026-06-24-document-sync-design.md](2026-06-24-document-sync-design.md),
[2026-06-26-document-sync-corrections-design.md](2026-06-26-document-sync-corrections-design.md),
[2026-06-26-document-sync-background-design.md](2026-06-26-document-sync-background-design.md)

## Summary

A UX pass over document sync, found during hands-on testing. The current model
exposes too many switches and a confusing in-activity "setup mode". This design
simplifies to a single rule: **when document sync is on, behaviour is fully
automatic — sync everything**; the user does not pick what syncs. Fine-grained,
per-document control happens after the fact (or manually) in the Synced
Documents view, and those choices (block list, tombstones) are honoured by the
automatic sync too.

Six changes:

1. Remove the separate "Automatic document sync" option — sync is always
   automatic when enabled.
2. Move the "Documents" master toggle into the existing **Synchronization
   categories** group, alongside Bookmarks/Workspaces/etc. Drop the dedicated
   "Documents" preference category entirely.
3. Move "Sync documents on Wi-Fi only" into the **General sync** group, shown
   only while document sync is enabled.
4. Replace the in-activity "setup mode" screen with a one-shot **AlertDialog**
   shown each time the toggle is switched on: it reports how many documents /
   how many MB will be downloaded and uploaded, and starts the sync on confirm.
5. Make the **Synced Documents** view reachable whenever a cloud adapter is
   configured (not gated on the document-sync toggle), enabling manual sync.
   Add a manual **"Sync now"** action (one-time full sync) to it.
6. **Cache** the cloud document listing locally (in the non-backed-up
   `TemporaryDatabase`) so the view opens instantly and remains usable as the
   access gate when sign-in is momentarily unavailable.

## Goals

- One mental model: document sync on = automatic, sync everything.
- Fewer, clearer switches; no in-activity setup mode.
- Manual sync possible without enabling automatic sync.
- The view opens fast (from cache) and degrades gracefully offline.
- Honour existing per-device choices (block list) and deletions (tombstones) in
  both automatic and manual sync.

## Non-goals

- No per-document selection at enable time. The enable dialog is all-or-nothing
  ("sync everything"); granular control is done afterwards in the view.
- No change to the underlying sync engine (`DocumentSync`, `DocumentSyncService`,
  resolver, tombstones) beyond removing the `automatic` flag.
- No multi-version retention, no new sync category in the cloud.

## 1. Settings screen structure (`sync_settings.xml`)

The dedicated `document_sync_category` group is removed. Document-sync
preferences are distributed into the two existing groups:

```
General sync  (sync_general)
  sync_adapter, cloud_sync_reset, cloud_sync_info,
  cloud_sync_server_url, cloud_sync_username, cloud_sync_password,
  cloud_sync_folder_path
  sync_documents_wifi_only      ← moved here; visible only while documents sync on
  document_sync_manage          ← "Synced documents"; enabled only when a cloud adapter is configured

Synchronization categories  (sync_category)
  sync_enable_bookmarks, sync_enable_workspaces, sync_enable_readingplans,
  sync_enable_mydocuments, sync_enable_ai_settings, sync_enable_progress
  sync_enable_documents         ← master toggle moved here
```

- **Remove** the `sync_documents_automatic` preference.
- **`sync_enable_documents`** moves into `sync_category` as a plain
  `SwitchPreferenceCompat` like its siblings (its enable flow is in §2).
- **`sync_documents_wifi_only`** moves into `sync_general`. Its visibility is
  driven dynamically by `DocumentSyncSettings.enabled` (shown when on, hidden
  when off) rather than the static `dependency` attribute, because the master
  toggle now lives in a different group and the enable commit is deferred to the
  dialog (§2).
- **`document_sync_manage`** ("Synced documents") moves into `sync_general`. It
  is **enabled only when a cloud adapter is configured** (the same condition
  that makes any cloud sync possible). When no adapter is configured it is shown
  but disabled, with a summary hint.

## 2. "Documents" toggle flow (`SyncSettings.kt`)

The master toggle no longer branches on auto/manual mode (there is only
automatic). On **off→on** (every time, not just the first):

1. Ensure sign-in: `CloudSync.signedIn` or `CloudSync.signIn(activity)`. If it
   fails or is cancelled → leave the toggle **off** (return false, re-read in
   `onResume`).
2. `DocumentSync.scan()` in the background → compute, honouring the block list:
   - downloads = cloud-only + update-available items,
   - uploads = local-only items,
   - their counts and summed sizes.
3. Show an **AlertDialog**: "Download X documents (Y MB), upload Z documents
   (W MB). Continue?" Append a Wi-Fi note when `wifiOnly && isMeteredNetwork`.
4. **Confirm** → `DocumentSyncSettings.enabled = true`,
   `DocumentSyncService.start(push = local-only, download = cloud-only+updates)`,
   reflect the toggle on, reveal the Wi-Fi-only row.
5. **Cancel** → no change; toggle stays off.

On **on→off**: `DocumentSyncSettings.enabled = false`, hide the Wi-Fi-only row.
Nothing is removed locally (consistent with the deletion design — disabling sync
is not a delete).

The toggle is never flipped on optimistically; `onResume` reflects the real
`DocumentSyncSettings.enabled` state (preserving the existing pattern for when
the user backs out).

The count/size computation is extracted as a pure function over a scan result so
it can be unit-tested (uploads/downloads counts + summed sizes from a
`List<DocumentStatusItem>`), independent of Android.

## 3. `CloudDocumentsActivity` changes

### Remove setup mode

Delete `EXTRA_SETUP_MODE`, `setupMode`, `pendingSetup`, `enterSetupMode()`,
`exitSetupMode()`, `performSetupSync()`, `updateSetupHeader()`, the intro
`header` view, and the "Start syncing" bottom bar wiring. The enable-time
summary now lives in the dialog (§2). The bottom bar / selection mode remains
only for the normal bulk-download affordance.

### Access logic on open

The view requires a usable cloud listing:

1. If not signed in → `CloudSync.signIn`.
2. Signed in → `scan()` (cloud + local), refresh the cache (§4).
3. Sign-in cancelled/failed → if a **cache** exists, show the cached listing;
   otherwise `finish()` with a toast ("Sign in to manage synced documents").

The settings link is already disabled unless an adapter is configured, so the
no-adapter case normally cannot reach the activity; the toast is the safety net.

### "Sync now" (manual one-time sync)

A new overflow (3-dot) menu item runs a full one-time push+pull cycle manually:
push local-only documents, download cloud-only/updates — bypassing the Wi-Fi-only
restriction (the user chose it) while honouring the block list and tombstones
(same resolution as automatic sync). **Hidden when automatic sync
(`DocumentSyncSettings.enabled`) is on**, where it would merely duplicate the
running automatic sync; it is meant for the manual situation (adapter configured
but document sync toggle off).

Pull-to-refresh and the per-item download/push/remove/block actions are
unchanged.

## 4. Cloud listing cache (`TemporaryDatabase`)

`TemporaryDatabase` is a plain `RoomDatabase` that is **not** in the backup set
and is **not** sync-enabled — the right home for a throwaway cache. It is
already instantiated twice (`temporary.sqlite3`, `choose-document.sqlite3`).

- New entity `CachedCloudDocument` (the `DocumentSyncMeta` fields: initials,
  name, documentType, version, size, language, timestamp, category, deleted) +
  `CloudDocumentCacheDao` (replace-all, query-all, clear) added to the
  `TemporaryDatabase` schema.
- New instance `cloudDocumentsCacheDb` (`cloud-documents-cache.sqlite3`) in
  `DatabaseContainer`.
- Bump `TEMPORARY_DATABASE_VERSION` 1→2 and add a `CREATE TABLE` migration to
  `temporaryMigrations` (applies to all `TemporaryDatabase` files; harmless for
  the existing two).
- `DocumentSync.scan()` writes the fresh cloud listing to the cache whenever it
  obtains one from the network, and reads from the cache when the network
  listing is unavailable. The activity can render the cached listing immediately
  and refresh from the network in the background.
- The cache is pure derived data: never backed up, never synced.

## 5. Removing the `automatic` flag (all call sites)

- **`DocumentSyncSettings`**: remove the `automatic` property and the `AUTOMATIC`
  key constant.
- **`DocumentSyncOps.shouldAutoUpload(...)`**: drop the `automatic` parameter →
  `enabled && !blocked && autoTransferAllowed`. Update `DocumentSyncOpsTest`.
- **`DocumentSync.pullDocuments(automaticOnly)`**: remove the `automatic` check
  (the automatic path keeps `enabled` + `isAutoTransferAllowed`). The
  `automaticOnly` parameter stays — it distinguishes the scheduled pull (honours
  Wi-Fi-only) from the manual "Sync now" (bypasses it).
- **`BookInstallWatcher.bookAdded`**: drop the `automatic` argument from the
  `shouldAutoUpload` call.

## 6. Strings & testing

### Strings (`strings.xml`, English)

- **Remove:** `document_sync_automatic_title`, `document_sync_automatic_summary`,
  `cloud_doc_setup_intro`, `cloud_doc_setup_start`.
- **Add:** enable-dialog title/message, "Sync now" menu label, the
  sign-in-required access toast, and a disabled-state summary hint for the
  Synced Documents link when no adapter is configured.
- **Reuse:** `cloud_doc_header_totals` (now the dialog body) and
  `cloud_doc_wifi_waiting` (now the dialog's Wi-Fi note).

### Tests

Kotlin-only change → `./gradlew testStandardGoogleplayDebugUnitTest`. No Vue tests.

- **`shouldAutoUpload`** without the `automatic` parameter — guard combinations
  (disabled, blocked, metered+wifiOnly).
- **Enable-dialog computation** — a pure function: given a `scan()` result,
  produce upload/download counts and summed sizes, honouring the block list.
- **Cache DAO round-trip** — replace-all then query-all returns the same set;
  clear empties it; `category`/`deleted` survive.
- Activity-level wiring (toggle dialog, sign-in/cache access gate, setup-mode
  removal, "Sync now" visibility, Wi-Fi-only row visibility) is verified by
  manual on-device testing, consistent with the prior document-sync specs.

## Implementation order

Phased, each phase its own commit(s):

1. **Settings restructure + `automatic` removal** — `sync_settings.xml`,
   `SyncSettings.kt`, `DocumentSyncSettings.kt`, `DocumentSyncOps.kt`,
   `DocumentSync.pullDocuments`, `BookInstallWatcher.kt`, strings; tests for
   `shouldAutoUpload`.
2. **Cloud listing cache** — `TemporaryDatabase` entity/DAO + migration,
   `DatabaseContainer` instance, `DocumentSync.scan()` cache read/write; cache
   DAO test.
3. **Enable flow + activity** — enable AlertDialog in `SyncSettings.kt`,
   `CloudDocumentsActivity` setup-mode removal, access gate, "Sync now"; pure
   count/size function + its test.

## Explicitly not done

- No per-document selection at enable time.
- No change to the sync engine, resolver, or tombstone semantics beyond the
  `automatic` removal.
- No backup/sync of the cache.
