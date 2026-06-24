# Document Sync Design

**Date:** 2026-06-24
**Status:** Approved (design phase)

## Summary

Synchronize installed Bible documents (SWORD modules, MyBible, MySword, eSword,
EPUB) across a user's devices using the **same cloud backend** as the existing
device sync (`CloudAdapter` — Google Drive / Nextcloud). When a user installs a
document on one device — by **any** method (repository download, sideloaded zip,
MyBible/MySword/eSword/EPUB import) — the **complete document** is copied to the
cloud store and re-installed on the user's other devices.

The whole document is copied (not just metadata + re-download) because
sideloaded and custom-repository documents cannot be reliably re-downloaded on
another device; copying the actual files works regardless of install method.

This also eases onboarding: enabling sync on a fresh device brings down all
documents already in the cloud store.

## Goals

- Sync all document types and all install methods.
- Track and propagate versions ("newest wins", auto-upgrade on other devices).
- Provide a per-device way to prevent a document from syncing to *this* device.
- Provide an explicit, visible onboarding step when sync is enabled.
- Provide a management view of what the cloud store contains and let the user
  manage it.
- Support both automatic and manual (semi-automatic) sync modes.
- Be network-aware (large data transfers): WiFi-only option for auto sync.

## Non-goals

- No real-time/continuous sync — runs on the existing sync cycle + on
  install/uninstall events + on manual action.
- No multi-version retention in the cloud — only the newest version is kept.
- No automatic row-level conflict merge — last-writer-wins is sufficient for
  document install/uninstall (low frequency).

## Key Decisions

| Decision | Choice |
|---|---|
| Sync unit | Whole document, packaged as an `.abmd.zip` archive |
| Cloud manifest | **Cloud folder listing** (no synced database) — "approach B" |
| Deletion | Local-only by default; **optional** propagation via tombstone |
| Versioning | Newest wins, auto-upgrade in auto mode, no forced downgrade |
| Blocking | Per-device, stored locally (not synced) |
| Onboarding | Explicit step; auto mode = download all (with confirmation), manual mode = management view |
| Network | WiFi-only option for auto sync (default on); manual actions always proceed |

### Why approach B (cloud folder listing) over a synced metadata database (A)

A synced manifest database would ride the existing patch-based DB sync, but:
- Its tombstone delete semantics propagate **unconditionally**, which conflicts
  with the "deletion does not propagate by default" requirement. Making it
  optional would mean *not* deleting rows but setting a status flag — i.e.
  reimplementing tombstones inside the DB rather than using the "free" delete.
- The document binaries live in a separate blob store regardless, so approach A
  is a hybrid anyway, plus it adds a Room DB + migration + triggers and couples
  document sync to DB sync being enabled.

Approach B keeps the manifest as the cloud folder listing: simpler, no
migration, deletion is naturally local-only, and "newest wins" is trivial with
the version encoded in the path. Optional deletion propagation is added via an
explicit tombstone marker (see below) — which both approaches need anyway, since
presence/absence alone cannot distinguish "deleted" from "never uploaded".

## Cloud Storage Layout

A new sync folder alongside the existing per-category folders, using the same
`CloudAdapter` backend (same Drive/Nextcloud account and authentication):

```
net.bible.android-sync-DOCUMENTS/
├── {initials}/
│   ├── meta.json            # per-document manifest
│   └── {version}.abmd.zip   # packaged document (BackupControl format)
├── KJV/
│   ├── meta.json
│   └── 2.6.abmd.zip
└── FinRK/
    ├── meta.json
    └── 1.0.abmd.zip
```

`meta.json` per document:

```json
{
  "initials": "KJV",
  "name": "King James Version",
  "documentType": "SWORD",
  "version": "2.6",
  "size": 16384000,
  "language": "en",
  "sourceDevice": "<deviceId>",
  "timestamp": 1740000000000,
  "cipherKey": null,
  "deleted": false
}
```

- `documentType`: `SWORD | MYBIBLE | MYSWORD | ESWORD | EPUB`.
- The **folder listing is the manifest** — no synced database.
  `CloudAdapter.listFiles(parent=DOCUMENTS)` enumerates documents; each
  `meta.json` provides version info.
- Only the **newest version** is kept in the cloud. On version upgrade, the new
  `{version}.abmd.zip` is uploaded, the old one removed, and `meta.json` updated.
- `cipherKey` is stored for encrypted SWORD modules so they remain usable on a
  new device. This is the user's own private cloud account — the same trust
  boundary as the document data itself.
- `deleted: true` is a tombstone for optional deletion propagation.

### Per-document folder vs single shared manifest

Per-document folders avoid write conflicts when multiple devices upload
concurrently. A single shared `manifest.json` would be clobbered by concurrent
writers.

## Sync Flow

### A. Upload (new/updated document → cloud)

Triggered from the existing `BookInstallWatcher.bookAdded()` hook. When a
document is installed by any method:

1. Check: document sync enabled? Auto mode on? Document **not** device-blocked?
   If any is false → no upload (in manual mode it waits for the user to push it
   from the management view).
2. Read cloud `{initials}/meta.json` if present. Compare versions
   (`org.crosswire.common.util.Version` from the `Version` property).
   - Cloud missing or older → package `{version}.abmd.zip`, upload, delete the
     old version zip, write/update `meta.json`.
   - Cloud same or newer → do nothing (newest wins).
3. Upload runs in the background (work queue); it does not block installation.

**Crash/interruption safety:** upload writes `{version}.abmd.zip.tmp` first;
only after a successful upload does `meta.json` point to the committed file
(atomic "commit" via meta). An incomplete upload is never visible as complete to
other devices.

### B. Download / pull (cloud → device)

Runs as part of the normal sync cycle (same `SyncService` scheduling as DB
sync), during onboarding, and on manual action from the management view.

1. List cloud documents (`listFiles`), read each `meta.json`.
2. For each cloud document, decide an action by comparing to local
   `Books.installed()` state and the per-device block list:

| Cloud | Local | Auto-mode action |
|---|---|---|
| present, no tombstone | not installed, not blocked | download + install |
| present, newer version | installed older | download + upgrade |
| present | installed, blocked on this device | skip |
| tombstone (`deleted:true`), timestamp newer than local install | installed | **uninstall locally** (optional deletion propagation) |
| present, timestamp older than local | installed newer | nothing (local wins) |

In manual mode the same table is computed but nothing happens automatically; the
management view shows these as statuses and the user chooses.

### C. Deletion flow (tombstone)

- **Default:** a local uninstall (`SwordBookDriver.delete` etc.) does not touch
  the cloud. The cloud and other devices keep the document.
- **Optional "remove from sync":** writes `meta.json` → `deleted: true` with a
  new timestamp and removes `{version}.abmd.zip`. Other auto-mode devices then
  uninstall locally (table row 4).
- **Re-install** with a timestamp newer than the tombstone overwrites it — the
  document "comes back to life". Last-writer-wins via version path + timestamp.
- A setting controls whether an uninstall silently removes only locally
  (default) or prompts ("this device only / also from sync").

### D. Version upgrade

A document upgraded on device A → upload flow (A) replaces the cloud version →
device B's pull flow (B, row 2) auto-upgrades in auto mode.

## Modes, Network Policy, Blocking, Settings

### Settings (`sync_settings.xml`, a "Documents" group)

| Setting | Key | Default | Meaning |
|---|---|---|---|
| Document sync enabled | `sync_enable_documents` | off | Master switch (uses cloud account). Turning on → onboarding step. |
| Automatic sync | `sync_documents_automatic` | on (when master on) | On = auto-upload + auto-download. Off = manual only via management view. |
| WiFi only | `sync_documents_wifi_only` | **on** | Auto sync (upload + download) only on unmetered networks. On mobile data it waits for WiFi. Manual actions from the management view always proceed. |
| Auto-trigger on WiFi | `sync_documents_auto_on_wifi` | off (under consideration) | When WiFi becomes available, opportunistically run document sync. |

### Network policy implementation

Document auto sync adds a network constraint to the existing `SyncService`
scheduling:

- `wifi_only` on → auto upload/download requires `NetworkType.UNMETERED`
  (WorkManager constraint). On mobile data the work waits.
- `wifi_only` off → any connection.
- **Manual** actions (management view "download now" / "push to cloud") bypass
  this — the user has already decided.
- A WorkManager unmetered constraint effectively also provides "sync when WiFi
  becomes available", so the separate `auto_on_wifi` switch is likely
  unnecessary at first. Start with just the constraint; add the explicit switch
  only if a need emerges.

### Per-device block (device-local, NOT synced)

- Storage: a local list (SharedPreferences set or a small local Room table) of
  initials that **this device** does not auto-download.
- Set from the management view ("don't sync to this device").
- A blocked document is skipped by the pull flow. If already installed and then
  blocked, it is not auto-removed (the block only affects automatic download);
  the user can remove it separately.

### Cloud account dependency

Document sync requires the same `CloudAdapters.current` authentication as DB
sync. If the user is not signed in, turning on the master switch starts
`CloudSync.signIn()` (as the other categories do).

## Management View (Cloud Documents)

A new `CloudDocumentsActivity`, opened from the sync settings and — when sync is
enabled — also from the document chooser 3-dot menu and the Download Documents
screen. It is both the manual-mode control surface and the auto-mode fine-tuning
surface, and (in setup mode) the onboarding screen.

### Unified list of local + cloud documents, each with a status

| Status | Meaning | Actions |
|---|---|---|
| Synced | Installed locally + in cloud, same version | Remove from cloud; Block on device |
| Local only | Installed, not in cloud | Push to cloud |
| Cloud only | In cloud, not installed | Download + install |
| Update available | Cloud newer (or local newer) | Update / push newest |
| Blocked | In cloud, blocked on this device | Remove block |
| Uploading/Downloading | Transfer in progress | Progress bar |

- **Per row:** document name, type icon (SWORD/MyBible/…), version, size, status.
- **Top bar:** total cloud size, "Download all" / "Push all", free disk space.
- **Filters:** Installed / In cloud / Updates / Blocked.
- **Selection mode** (checkboxes) + bottom bulk-action bar — useful generally
  and reused by onboarding.

### Actions

- **Download from cloud** (cloud-only / update) — bypasses `wifi_only` (explicit).
- **Push to cloud** (local-only / manual mode) — bypasses `wifi_only`.
- **Remove from cloud** — writes a tombstone (Deletion flow). Confirmation
  dialog, since in auto mode this can remove it from other devices too.
- **Block / unblock on this device** — per-device block list.

### Theme / e-ink

Statuses must not rely on color alone — use icons + text so monochrome/e-ink
works. Progress bars and icons must work in grayscale. Respect the "no
animations" setting.

### Localization

All strings go to `strings.xml` (Android side, not Vue), in English.

## Onboarding = management view in "setup mode"

Enabling document sync reuses the management view rather than a separate summary
screen.

**Auto mode, first enable:**

1. Sign in (`signIn`), scan cloud + local documents.
2. Open the management view in **setup mode**:
   - One-time intro header: "Document sync enabled — choose what to sync."
   - **All items pre-selected** (auto default = sync everything).
   - Header shows: N↑ to push (X MB), M↓ to download (Y MB), free disk space.
   - The user can deselect items:
     - deselecting a cloud-only item → device block (not downloaded here).
     - deselecting a local-only item → not pushed to cloud.
   - **Bottom bar: "Start syncing (N↑ X MB · M↓ Y MB)"** → bulk operation runs
     in the background with progress.
   - WiFi note if `wifi_only` and on mobile data.

**Manual mode:** same view, but no pre-selection and no Start CTA — the normal
management view; the user chooses.

**After setup mode:** the same view becomes the normal management view (intro
header and Start CTA disappear). The user has learned the management view in the
process.

Implementation: one `CloudDocumentsActivity` with a `setupMode: Boolean`
parameter.

## Components and Code Touchpoints

### New components (`service/cloudsync/documents/`)

- **`DocumentSync`** — core orchestrator. Uses `CloudAdapters.current`. Methods:
  `uploadDocument(book)`, `pullDocuments()`, `removeFromCloud(initials)`
  (tombstone), `scan()` (returns the unified status list for the management view).
- **`DocumentArchiver`** — packaging/extraction. Refactor
  `BackupControl.createModulesZip` into a per-document function; installation
  delegates to the existing `InstallZip` logic
  (`installZipFile` / `installFromFile` / `installEpub`). Per-type on-disk layout
  per the Cloud Storage Layout section.
- **`DocumentSyncMeta`** — `meta.json` serialization (kotlinx.serialization).
- **`DocumentBlockList`** — per-device block list (local, not synced).

### New UI

- **`CloudDocumentsActivity`** + layout (View Binding): unified list, filters,
  selection mode + bulk bottom bar, setup mode.

### Changes to existing code

- **`BookInstallWatcher.bookAdded()/bookRemoved()`** — upload trigger on
  install; uninstall flow (default: don't touch cloud; per setting, prompt /
  propagate tombstone).
- **`SyncSettings.kt` + `sync_settings.xml`** — a new "Documents" group (4
  settings) + link to the management view.
- **`SyncService` / `CloudSync.start()`** — wire `DocumentSync.pullDocuments()`
  into the sync cycle with network constraints (WorkManager unmetered constraint
  when `wifi_only`).
- **Document chooser 3-dot menu + Download screen** — menu entry to the
  management view (visible when sync is on).
- **`strings.xml`** — all new strings (English).
- **`BackupControl`** — refactor to extract per-document zipping (Step 0 style,
  possibly its own commit).

### Explicitly not done

No new Room database, no migration, no DB-sync triggers — the manifest is the
cloud folder listing (the advantage of approach B).

## Testing

- **`DocumentArchiver`** (unit): package + extract each type
  (SWORD/MyBible/MySword/eSword/EPUB) into a temp dir; verify round-trip (files
  identical). Reuse `BackupControl` test scaffolding if present.
- **Version logic** (unit): `Version` comparison — newest wins, no downgrade,
  tombstone timestamp comparison (install newer than tombstone revives).
- **Status resolution** (unit): each row of the pull-flow table — given (cloud
  meta, local version, blocked?) → expected action. This is the most critical
  logic.
- **`DocumentBlockList`** (unit): block prevents auto-download, does not touch an
  already-installed document.
- **`DocumentSyncMeta`** (unit): JSON round-trip, missing/unknown fields
  (forward compatibility).
- **Network policy** (unit): `wifi_only` blocks auto transfer on metered
  network; manual bypasses. (Constraint logic unit test.)

Kotlin-only change → `./gradlew testStandardGoogleplayDebugUnitTest`. No Vue
tests (pure Android change).

## Open Questions / Future Considerations

- Whether the explicit `sync_documents_auto_on_wifi` switch is needed, or the
  WorkManager unmetered constraint suffices.
- Whether to expose the per-document size in the list eagerly (requires reading
  each `meta.json`) or lazily.
- Total cloud storage usage display and any cap/warning for very large stores.
