# Document Sync — Incremental Cloud Listing

**Date:** 2026-06-27
**Status:** Design

Builds on [2026-06-26-document-sync-design.md](2026-06-26-document-sync-design.md). It changes
how the cloud document listing is refreshed, and where document-sync's per-device state lives.
Nothing about the cloud storage layout, resolver, service, or management view changes.

## Problem

`DocumentStore.listDocuments()` enumerates every document folder and downloads **every**
`meta.json`: `getFolders(root)` + per document `listFiles(meta.json)` + `download`, i.e. roughly
`1 + 2N` round-trips for `N` synced documents (bounded-concurrency fan-out, `asyncMap(6)`). This
runs on **every sync cycle** (`runSync → refreshCache`) and **after every background-service drain**.

With tens or hundreds of synced documents this dominates sync latency: a sync in which *nothing
changed* still pays the full `1 + 2N` cost. The existing **DB sync** does not have this problem —
it lists minimally by timestamp and a no-op sync is near-instant. Document sync should match that.

## How DB sync lists minimally (the model we copy)

DB sync uses an append-only patch model: each sync writes a new patch file whose `createdTime` only
ever increases; old patches are never modified. It stores a per-category `lastSynchronized`
watermark and queries:

```kotlin
adapter.listFiles(parentsIds = deviceFolders, createdTimeAtLeast = lastSynchronized)
```

The backend (`CloudAdapter.listFiles` — implemented server-side on both Google Drive and NextCloud)
returns only files created since the watermark. In steady state the result is empty → near-zero work.

### Why this transfers to document sync

`DocumentStore.writeMeta` commits a meta change by **deleting the old `meta.json` and uploading a
fresh one**. Every meta mutation (initial upload, version upgrade, tombstone) therefore produces a
file with a **new `createdTime`**. So `createdTime` of a document's `meta.json` already tracks "when
this document's metadata last changed" — exactly the property `createdTimeAtLeast` needs. (Had
`writeMeta` updated in place, `createdTime` would be stale and this optimization would be impossible.)

### `createdTime` semantics per adapter (and the residual risk)

- **Google Drive:** `createdTime` is server-assigned. Deriving the watermark from observed
  server `createdTime` values means a new meta reliably has `createdTime` greater than any previously
  observed value. Effectively exact.
- **NextCloud:** `upload` sets the creation timestamp from the **uploading device's wall clock at
  second resolution** (`System.currentTimeMillis() / 1000`). A device whose clock lags behind the
  watermark another device has already observed can write a `meta.json` whose `createdTime` falls
  *below* that watermark — and then a `createdTimeAtLeast` query silently misses it until a full
  rescan.

This silent-miss risk is **the same risk DB sync already accepts** on the same NextCloud adapter
with the same time-watermark mechanism, and phone clocks are normally NTP-synced (skew of seconds).
We accept it for parity and mitigate with a small overlap margin plus an explicit user-triggered
reset (below) — a recovery path DB sync does not even offer.

## Design

### Storage: rename `CacheDatabase` → `DocumentSyncDatabase`

All of document-sync's per-device state moves into one database, renamed from `CacheDatabase`
(it stops being a generic "pure derived caches" home and becomes document-sync-specific):

- **`DocumentSyncDatabase`** — file `document-sync.sqlite3`, `DOCUMENT_SYNC_DATABASE_VERSION = 1`.
  **Not backed up** (absent from `ALL_DB_FILENAMES`), **not synced** (absent from
  `SyncableDatabaseDefinition`), and **cleared in its entirety on cloud sign-out**.
- Contents (four tables):
  - **`DocumentSyncPreferences`** — a **singleton entity** (fixed `@PrimaryKey = SINGLETON_ID`),
    following the `GlobalAiSettings` / `AiSettings` pattern: DAO `get(): DocumentSyncPreferences?`
    (`SELECT … LIMIT 1`) + `set()` (`@Insert REPLACE`); the `DocumentSyncSettings` accessor object
    reads `dao.get() ?: DocumentSyncPreferences()` and writes via `update { copy(...) }`. (The
    *entity* is `DocumentSyncPreferences`; `DocumentSyncSettings` is the accessor `object` over it —
    they are deliberately distinct so there are not two types named `DocumentSyncSettings`.) Holds
    the **user preferences only**: `enabled`, `wifiOnly`, `autoDownload`, `autoUpload`, `autoDelete`,
    `syncNowDownload`, `syncNowUpload`, `syncNowDelete`, `showRemovedDocuments`, `blockList: Set<String>`.
  - **`CloudListingState`** — a second singleton entity holding the operational **listing
    watermark** (`watermark: Long`). Kept separate from settings: the watermark is derived sync
    state, not a user preference.
  - **`CloudDocumentSyncTimestamp(initials @PrimaryKey, timestamp: Long)`** — per-document
    last-sync timestamps (replacing the `doc_sync_ts_*` keys), a keyed table for natural per-document
    upsert.
  - **`CachedCloudDocument`** table + `CloudDocumentCacheDao` (moved as-is).

**Rationale.** Document-sync state is entirely device-local: the cloud account itself (OAuth /
NextCloud credentials) is device-local and re-established per device, so document-sync setup is
re-done per device anyway — there is nothing to back up or sync. Co-locating settings, cache,
watermark, and per-doc timestamps in one sign-out-scoped database makes that lifecycle explicit and
removes a coupling hazard: the watermark is meaningless without the cache it describes, so the two
must share a lifecycle. (Were the watermark backed up while the cache was not, a restore would leave
a stale watermark over an empty cache and silently hide documents.)

**Sign-out.** Clears the whole `DocumentSyncDatabase` (settings + cache + watermark + timestamps).
On the next sign-in everything starts from defaults — a clean slate, which is correct because a new
sign-in may target a different cloud account; the previous setup's block list and toggles belonged
to the previous setup.

**No migration.** Existing `SettingsDatabase` keys (`sync_enable_documents`,
`sync_documents_blocked`, `doc_sync_ts_*`, …) are left dead; document-sync settings reset to
defaults once on upgrade. The orphaned `cloud-documents-cache.sqlite3` file is deleted on first run.
(Acceptable because document sync is unreleased; only branch test devices reset, once.)

### Unified listing path

A single `DocumentStore` method serves both the full and incremental cases:

```kotlin
suspend fun listChangedDocuments(watermark: Long): ChangedListing
// ChangedListing(
//   changedMetas: List<DocumentSyncMeta>,   // metas matched by the query that parsed OK
//   currentInitials: Set<String>,           // names of all folders that exist right now
//   matchedCreatedTimes: List<Long>,         // createdTime of every matched meta (from the listing)
//   failedCreatedTimes: List<Long>,          // createdTime of matched metas that failed download/parse
// )
```

The listing step (`listFiles`) yields a `createdTime` for every matched meta *before* download, so
both the successfully-parsed metas and the createdTimes of any that subsequently failed are known.

1. `getFolders(root)` → current folders (id + name). One call. Provides both **new-document
   discovery** (a new document is a new folder) and **purge detection** (a folder that vanished).
2. `listFiles(parentsIds = folderIds, name = meta.json, createdTimeAtLeast = max(0, watermark − MARGIN))`
   → only changed/new metas. One batched call.
3. Download + parse only those metas.

**Cold start is `watermark = 0`** — `createdTimeAtLeast = 0` returns every meta, so cold start and
incremental are the same code path. A standalone full list is `listChangedDocuments(0)`.

### `refreshCache()` becomes an incremental merge

```
w = db.watermark ?: 0                               // 0 ⇒ cold start (empty cache / post sign-out / post reset)
(changed, currentInitials) = store.listChangedDocuments(w)
cache = mergeCloudListing(cache, changed, currentInitials, w)   // pure, unit-tested
db.watermark = cache.newWatermark
```

`mergeCloudListing` (pure function):
- **Upsert** each successfully-read changed meta into the cache.
- **Purge:** drop cache rows whose `initials ∉ currentInitials`.
- **Watermark advance — never step over a failure:**
  - No failures → `newWatermark = max(oldWatermark, max(matchedCreatedTimes))`.
  - Some failures → `newWatermark = max(oldWatermark, min(failedCreatedTimes) − 1)`: advance past
    every meta strictly older than the earliest failure (so those successes aren't re-read), but
    leave the earliest failure and everything newer to be re-fetched next cycle. Successfully-read
    metas are still upserted regardless.

  This never skips a transiently failed meta, and — unlike "don't advance at all on any failure" —
  it can't be pinned in place by one persistently-corrupt meta into re-reading an ever-growing
  window. A genuinely permanent failure (corrupt cloud meta) is logged and recoverable via reset.

`scan()` / `runSync()` route through `refreshCache()` (incremental) and then read the assembled
status list from the cache via `scanCached()`. Per-operation single-document reads
(`pushDocument`'s cloud re-check, `removeFromCloud`, `downloadAndInstall`) already touch a single
folder and are not the bottleneck — they are unchanged.

### Trigger policy

- **Incremental:** the automatic sync cycle and every other refresh.
- **Full (authoritative):** only cold start — i.e. whenever the watermark is absent (empty cache,
  fresh sign-in, post-reset). This *is* the incremental path with `watermark = 0`.
- **Reset / re-scan** — a new overflow (3-dot) menu action in `CloudDocumentsActivity`: clears
  `DocumentSyncDatabase` cache + watermark (state), forcing the next refresh to cold-start a full
  authoritative listing. This is the explicit recovery for the rare clock-skew silent-miss case.

### The margin

`createdTimeAtLeast = watermark − MARGIN` re-queries a small trailing window (a few seconds) each
cycle to absorb NextCloud's one-second timestamp resolution and minor clock skew. Re-reading a meta
already in the cache is idempotent (upsert). The margin is a cheap safety overlap, **not** a
correctness guarantee against arbitrary skew — the reset action is that guarantee.

## Error handling

- **Per-meta download/parse failure:** skip that document (keep its existing cache row, if any),
  and do not advance the watermark this cycle so it is retried. Logged.
- **`getFolders` / `listFiles` (network) failure:** abort the refresh, leave cache and watermark
  unchanged (stale but valid); retried on the next cycle. Same as today.

## Testing

- **`mergeCloudListing` (pure, unit-tested)** — the heart of the change:
  - upsert of changed metas;
  - purge of cache rows whose folder vanished (`initials ∉ currentInitials`);
  - watermark advances to the max matched `createdTime` when nothing failed;
  - watermark advances only to just below the earliest failed `createdTime` when something failed
    (never steps over a failure, never pinned by one);
  - cold start (`watermark = 0`) ingests the full set.
- **`CloudDocumentCacheMapping`** round-trip — already covered.
- **Sign-out clears `DocumentSyncDatabase`** (settings + cache + watermark) — testable against the DB.
- The adapter `createdTimeAtLeast` server-side filtering remains verified by manual / on-device
  testing on both Google Drive and NextCloud, as for DB sync.

## Key components

- `database/Databases.kt`: rename `CacheDatabase` → `DocumentSyncDatabase` (new file name); add the
  `DocumentSyncPreferences` + `CloudListingState` singleton entities and the `CloudDocumentSyncTimestamp`
  table alongside `CachedCloudDocument`, with their DAOs; `DatabaseContainer` registration + first-run
  deletion of the old `cloud-documents-cache.sqlite3`.
- `cloudsync/documents/DocumentSyncSettings.kt`: becomes an accessor `object` over the two singleton
  entities (settings + listing state) and the timestamp DAO, following the `AiSettings` pattern
  (`get() ?: default`, `update { copy(...) }`), instead of `CommonUtils.settings`. Adds `watermark`
  get/set; `syncTimestamp(initials)` / `setSyncTimestamp` now read/write the
  `CloudDocumentSyncTimestamp` table; `blockList` is backed by the settings singleton's `Set<String>`.
- `cloudsync/documents/DocumentStore.kt`: add `listChangedDocuments(watermark)`; `listDocuments()`
  becomes `listChangedDocuments(0)`.
- `cloudsync/documents/DocumentSync.kt`: `refreshCache()` incremental merge via the new pure
  `mergeCloudListing`; clear watermark on the reset action and on sign-out.
- `view/activity/cloud/CloudDocumentsActivity.kt`: "Re-scan / reset" overflow action.
- Sign-out path (`CloudSync` sign-out / cache-clear): clear the whole `DocumentSyncDatabase`.

## Non-goals

- No change to the cloud storage layout, `meta.json` schema, resolver, transfer service, or
  management view (beyond the one reset menu action).
- Not eliminating the cold-start cost — a first sync on a device still downloads all metas once.
- Not guaranteeing against arbitrary clock skew — bounded by the margin and recovered by reset,
  matching DB sync's accepted risk profile.

## Future considerations

- If a generic (non-document-sync) cache is ever needed, it gets its own database rather than
  reusing `DocumentSyncDatabase`.
- A per-document sequence number in `meta.json` could make incremental detection skew-proof (like
  DB sync's patch numbers), removing the residual NextCloud risk — only worth it if skew misses
  prove real in practice.
