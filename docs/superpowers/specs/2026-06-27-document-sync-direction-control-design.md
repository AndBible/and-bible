# Document Sync — Per-Device Direction Control

**Date:** 2026-06-27
**Status:** Design

Extends the document-sync feature (see `2026-06-26-document-sync-design.md`) with granular,
per-device control over **which sync operations run** — replacing the current all-or-nothing
automatic model. Also fixes a bug: "Sync now" is currently download-only and never uploads.

## Motivation

Today, enabling document sync makes a device fully bidirectional and automatic: every installed
document is auto-uploaded, every cloud document is auto-downloaded, and every removal propagates.
There is no way to say "this device should receive documents but never push its own".

The driving case: a device used for testing production builds, where many throwaway modules get
installed. The user wants the curated cloud set to download to it, but does **not** want its test
modules leaking to every other device.

A second, related problem: the manual "Sync now" action is misleadingly named. It runs
`pullDocuments(automaticOnly = false)`, which the resolver only ever resolves to
DOWNLOAD / UPGRADE / UNINSTALL — it **never pushes** local documents to the cloud. There is no
manual bulk-upload at all (only auto-upload on install, enable-time bulk, and per-item Push).

## Goals

- Per-device control of automatic behaviour, split into three operations: **download**,
  **upload**, **delete** (removal propagation). All default on (current behaviour preserved).
- Make "Sync now" a true full sync, with a per-run operation picker (download / upload / delete).
- Uploads (auto and manual) honour the per-device block list, like downloads already do.

## Non-goals

- No "send-only" preset or named modes — the three independent toggles cover the cases.
- No change to per-item actions, selection-mode bulk download, remove-from-cloud semantics, the
  enable flow, the cloud layout, or the resolver's "newest wins" rules.
- No retroactive guarantees: turning a toggle on does not replay history; it governs future
  automatic runs (the next sync cycle and install events).

## The three automatic operations

Three independent per-device preferences in `DocumentSyncSettings`, all default `true`:

| Pref          | Gates                                                                 |
|---------------|-----------------------------------------------------------------------|
| `autoDownload`| Auto DOWNLOAD / UPGRADE in the sync cycle.                            |
| `autoUpload`  | Auto upload — **both** install-time (`BookInstallWatcher`) **and** the sync-cycle push of local-only / local-newer documents. |
| `autoDelete`  | Auto UNINSTALL (tombstone-driven removal propagation) in the sync cycle. |

These gate **automatic** behaviour only. Manual actions (per-item Download/Push/Remove,
selection-mode bulk download, "Sync now") always work and are unaffected by the toggles — they are
explicit user decisions. The toggles are hidden when document sync is disabled, reinforcing that
they only shape automatic behaviour.

The block list and Wi-Fi-only policy continue to apply to all automatic transfers.

### Why uploads run in the sync cycle too

`autoUpload` deliberately gates both the install-time push **and** the sync cycle. A user expects
"automatic sync, uploads on" to eventually push everything installed on the device — gating only
the install event would be a confusing partial behaviour (a document installed while uploads were
off, or before sync was set up, would never propagate). Install-time push stays for snappy
propagation; the cycle is the catch-all. Both share one `resolveUploads` implementation.

## Settings UI (`sync_settings.xml`, `SyncSettings.kt`)

The existing **Document sync** category (visible when signed in) gains three switches, shown only
while document sync is enabled — mirroring the existing Wi-Fi-only visibility:

```
Document sync                       (category shown when signed in)
  Auto-download                     ← download new/updated documents automatically   [enabled only]
  Auto-upload                       ← upload installed documents automatically         [enabled only]
  Auto-remove                       ← apply removals from other devices automatically  [enabled only]
  Sync on Wi-Fi only                                                                   [enabled only]
  Synced documents  ▸
```

`updateDocumentSyncVisibility()` extends to toggle the three new switches' visibility off
`DocumentSyncSettings.enabled`, alongside the existing Wi-Fi-only switch.

## "Sync now" — full sync with an operation picker

- **Visibility:** shown whenever signed in (the current `!DocumentSyncSettings.enabled` condition
  is removed), still hidden in selection mode. The operation dialog makes it safe to expose even
  with automatic sync on, and lets the user force a sync at any time.
- **On tap:** an `AlertDialog` with three checkboxes — **Download**, **Upload**, **Delete** —
  pre-filled from the **remembered last manual choice** (a new pref; first run = all three checked).
  Confirm persists the choice and runs the selected operations; cancel does nothing.
- **Run:** bypasses the `enabled` and Wi-Fi-only guards (explicit manual action), honours the block
  list. Download → DOWNLOAD/UPGRADE; Delete → UNINSTALL; Upload → push local-only + local-newer.

This fixes the bug: "Sync now" can now upload, and is genuinely bidirectional.

## Engine (`DocumentSync`)

`pullDocuments(automaticOnly)` is generalised so one code path serves both the automatic cycle and
the manual run. Conceptually:

```
runSync(download, upload, delete, manual):
    if !manual and not (enabled and isAutoTransferAllowed): return    # existing automatic guard
    fetch cloud listing; refresh cache
    actions = resolveDocumentSyncActions(...)                         # unchanged resolver
    actions = selectSyncActions(actions, allowDownload = download, allowDelete = delete)
    run UNINSTALL inline; collect DOWNLOAD/UPGRADE initials
    if upload: pushInitials = resolveUploads(localDocs, cloudDocs, blocked, ::versionIsNewer)
    DocumentSyncService.start(push = pushInitials, download = downloadInitials)
```

- **Automatic cycle** (`CloudSync.synchronize`): `runSync(autoDownload, autoUpload, autoDelete,
  manual = false)`.
- **Manual "Sync now"**: `runSync(dlg.download, dlg.upload, dlg.delete, manual = true)`.

### New pure functions (unit-tested)

- `selectSyncActions(actions, allowDownload, allowDelete)` → filtered action list. With
  `allowDownload = false`, DOWNLOAD/UPGRADE are dropped; with `allowDelete = false`, UNINSTALL is
  dropped; SKIP_BLOCKED/NONE always dropped from execution. Pure.
- `resolveUploads(localDocs, cloudDocs, blocked, isNewer)` → initials to push: local-only, plus
  local-newer-than-cloud, minus blocked. Pure. (Overlaps `computeDocumentSyncSummary`'s upload
  split; factor the shared logic so both use one implementation.)

### Changed signatures

- `shouldAutoUpload(enabled, autoUpload, blocked, autoTransferAllowed)` — adds the `autoUpload`
  gate. `BookInstallWatcher` passes `DocumentSyncSettings.autoUpload`.

## Strings (`strings.xml`, English only)

New titles/summaries for Auto-download, Auto-upload, Auto-remove; the Sync-now dialog title, body
("Choose which operations to run"), and the three checkbox labels. All user-facing text goes
through resources — no hardcoded strings.

## Testing

Kotlin-only → `./gradlew testStandardGoogleplayDebugUnitTest`. Pure, unit-tested logic:

- `selectSyncActions` — download-off drops DOWNLOAD/UPGRADE; delete-off drops UNINSTALL; both-off
  leaves nothing executable; both-on preserves the resolver output.
- `resolveUploads` — local-only included, local-newer included, fully-synced excluded, blocked
  excluded, cloud-only excluded.
- `shouldAutoUpload` — the new `autoUpload` gate across the enabled/blocked/autoTransferAllowed
  combinations.

Dialog wiring (checkbox persistence, visibility, running the chosen operations) is verified by
manual on-device testing, consistent with the rest of the feature's UI wiring.

## Touchpoints

- `service/cloudsync/documents/`: `DocumentSyncSettings` (three prefs + remembered Sync-now choice),
  `DocumentSync` (`runSync`, `resolveUploads`), `DocumentSyncOps` (`selectSyncActions`,
  `shouldAutoUpload` signature).
- `android/control/versification/BookInstallWatcher` (pass `autoUpload`).
- `android/view/activity/settings/SyncSettings` + `res/xml/sync_settings.xml` (three switches,
  visibility).
- `android/view/activity/cloud/CloudDocumentsActivity` (Sync-now dialog, always-visible menu item).
- `res/values/strings.xml`.

## Future considerations (unchanged from the base spec)

- Per-document byte transfer progress.
- Total cloud storage display / cap warning.
