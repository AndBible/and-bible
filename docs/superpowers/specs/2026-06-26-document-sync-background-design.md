# Document Sync — Background Service & Unified Notification Design

**Date:** 2026-06-26
**Status:** Approved (design phase)
**Builds on:** [2026-06-24-document-sync-design.md](2026-06-24-document-sync-design.md),
[2026-06-26-document-sync-corrections-design.md](2026-06-26-document-sync-corrections-design.md)

## Summary

Make **every** document-sync data transfer run in a background foreground
service with a single, consistent per-document progress notification, and stop
blocking the UI. Found during hands-on testing: starting sync from the setup
screen ran behind a modal `Hourglass` on the activity's `lifecycleScope` — it
froze the screen and was tied to the activity lifecycle.

Today document transfers happen in three places, inconsistently:

1. **Manual actions** (`CloudDocumentsActivity`: setup "Start syncing", bulk
   download, per-item download/push) — modal `Hourglass`, blocks the UI.
2. **Auto-upload on install** (`BookInstallWatcher.bookAdded` →
   `DocumentSync.uploadDocument`) — runs silently on a background scope, no
   notification.
3. **Auto-pull in the sync cycle** (`CloudSync.synchronize` →
   `DocumentSync.pullDocuments`) — only visible under the DB sync's generic
   "Synchronizing" notification, not per-document.

All three are routed through a new `DocumentSyncService` so they share one
per-document progress notification and never block the UI.

## Goals

- No document transfer blocks the app UI.
- A single, consistent per-document progress notification for **all** document
  transfers (manual + auto-on-install + auto-pull).
- Transfers survive leaving `CloudDocumentsActivity` (not tied to its lifecycle).
- Preserve existing guards: auto paths respect `wifi_only`; manual paths bypass
  it (the user has already decided).

## Non-goals

- No per-row progress bars in the management list — one shared in-activity
  indicator plus the notification is enough.
- No WorkManager network constraints for these transfers. Manual actions are
  explicit and bypass `wifi_only`; the auto paths already gate on
  `isAutoTransferAllowed` before enqueuing.
- No change to the DB sync notification or `SyncService` itself.

## Scope: what goes background, what stays inline

| Operation | Path |
|---|---|
| Setup "Start syncing" (bulk push + download) | `DocumentSyncService` |
| Bulk download (normal selection mode) | `DocumentSyncService` |
| Per-item download / push (3-dot menu) | `DocumentSyncService` |
| Auto-upload on install (`BookInstallWatcher`) | `DocumentSyncService` (after auto-guards) |
| Auto-pull downloads/upgrades (sync cycle) | `DocumentSyncService` (after auto-guards) |
| Remove from cloud (tombstone write) | **inline** (lightweight cloud-metadata write) |
| Block / unblock on this device | **inline** (instant local pref) |
| Auto-pull uninstalls (tombstone-driven) | **inline** (local delete, no transfer) |

Remove-from-cloud keeps its existing brief modal (`runSyncAction`); block/unblock
keep their instant inline behaviour.

## Components

### 1. `DocumentSyncService` (`service/cloudsync/documents/`)

A foreground service modelled on `SyncService`.

- **Manifest:** new `<service android:name=".service.cloudsync.documents.DocumentSyncService"
  android:foregroundServiceType="dataSync" />`.
- **Launch helper** (companion):
  ```kotlin
  fun start(context: Context, pushInitials: List<String>, downloadInitials: List<String>)
  ```
  Builds an intent with two `ArrayList<String>` extras and calls
  `startForegroundService`, mirroring `CloudSync.start()` — including the
  `ForegroundServiceStartNotAllowedException` / `IllegalStateException` guard
  (Android 14+ dataSync quota; background-start restrictions). On failure it
  logs and returns; it does not crash.
- **Queue:** a thread-safe queue of `DocumentSyncOp`. `onStartCommand` decodes
  the intent into ops and appends them. If the processing loop is not already
  running, it starts the foreground notification and a single IO coroutine that
  drains the queue until empty; if it is running, the new ops simply extend the
  queue (combined progress, `total` grows dynamically).
- **Op model:**
  ```kotlin
  sealed class DocumentSyncOp {
      data class Push(val initials: String) : DocumentSyncOp()
      data class Download(val initials: String) : DocumentSyncOp()
  }
  ```
  `Push` → `DocumentSync.pushDocument(book)` (resolves the book from
  `Books.installed()`; skips if gone). `Download` → `DocumentSync.downloadAndInstall(initials)`.
- **Per-op error handling:** `try/catch` around each op → log + post
  `BibleApplication.ErrorNotificationEvent(R.string.sync_error)` (same as DB
  sync), then continue with the next op.
- **Notification:** `SYNC_NOTIFICATION_CHANNEL` (or `CALC_NOTIFICATION_CHANNEL`
  in discrete builds), a distinct notification ID (not `SYNC_NOTIFICATION_ID`).
  Title from a new string; content text from `progressText(current, total, name)`
  (e.g. "KJV (2/5)") and `setProgress(total, current, false)` for the bar. Small
  icon `ic_syncdb_24dp`
  (`ic_calc_24` in discrete), matching `SyncService`.
- **Wakelock:** `PARTIAL_WAKE_LOCK` acquired with a generous timeout, released in
  `finally`.
- **Lifecycle:** `START_NOT_STICKY` (no point re-running an empty queue after a
  kill). Stops foreground + `stopSelf()` when the queue drains.

### 2. Progress events

```kotlin
class DocumentSyncProgressEvent(
    val running: Boolean,
    val current: Int,
    val total: Int,
    val currentName: String?,
)
```

Posted via `ABEventBus`: on each op start (`running = true`) and on completion
(`running = false`). `CloudDocumentsActivity` subscribes while resumed.

### 3. `CloudDocumentsActivity` changes

- Register/unregister for `DocumentSyncProgressEvent` (existing `ABEventBus`
  register/unregister pattern). On `running = false` → `refresh()`. While
  running → update a non-modal progress indicator.
- New non-modal progress row in `activity_cloud_documents.xml` (a `ProgressBar`
  + text), visible only while a transfer is running. Works in grayscale (e-ink)
  and respects the "no animations" setting (indeterminate only if animations are
  on; otherwise a determinate/static bar).
- `performSetupSync()`: set `DocumentSyncSettings.enabled = true`, apply device
  blocks inline (fast local pref writes), then
  `DocumentSyncService.start(push, download)`, then `exitSetupMode()` +
  `refresh()` + a brief "sync started" toast. No modal.
- `performBulkAction()` and per-item DOWNLOAD/PUSH: enqueue to the service, exit
  selection mode (no modal).
- `runSyncAction` (the modal `Hourglass` path) is kept **only** for
  remove-from-cloud.

### 4. `DocumentSync` / `BookInstallWatcher` changes (auto paths)

- **Auto-upload:** `BookInstallWatcher.bookAdded` currently calls
  `DocumentSync.uploadDocument(book)` on `syncScope`. Replace with: evaluate the
  auto guards (`enabled && automatic && !blocked && isAutoTransferAllowed`) via a
  small helper `DocumentSync.shouldAutoUpload(book)`, and if true call
  `DocumentSyncService.start(context, push = listOf(book.initials), download = emptyList())`.
  The guard stays at the call site because the service itself bypasses
  `wifi_only` (it serves manual transfers too).
- **Auto-pull:** `DocumentSync.pullDocuments(automaticOnly = true)` keeps its
  entry guards (`enabled`, and for the automatic path `automatic` &&
  `isAutoTransferAllowed`) and its action resolution. It performs
  `UNINSTALL` actions inline (local delete, no transfer), collects the
  `DOWNLOAD`/`UPGRADE` initials, and if any, calls
  `DocumentSyncService.start(context, push = emptyList(), download = initials)`
  instead of downloading inline. The `PULL_DOWNLOAD_BUDGET_MS` time-budget /
  `deferred` logic is removed — the service owns the transfers, so the sync cycle
  returns quickly and nothing needs deferring.

Because `pullDocuments` runs inside `CloudSync.synchronize()` (under
`SyncService`'s foreground notification), starting `DocumentSyncService` from
there is a clean handoff: the DB sync notification finishes, the document
notification continues independently.

### 5. Strings (`strings.xml`, English)

- Notification title (e.g. "Syncing documents").
- Notification progress content (current document name; count handled by the
  progress bar).
- In-activity progress text.
- "Document sync started" toast.

## Testing

Kotlin-only change → `./gradlew testStandardGoogleplayDebugUnitTest`. No Vue
tests. Service and Activity wiring is verified by manual on-device testing
(consistent with the corrections spec's treatment of Activity-level wiring).

Extract the pure logic and unit-test it:

- **Intent op encode/decode** — a pure helper converting (pushInitials,
  downloadInitials) ↔ ordered `List<DocumentSyncOp>` (pushes then downloads).
  Round-trip and empty-list cases.
- **Progress text formatting** — `progressText(current, total, name)` produces
  the expected "name (current/total)" style string; boundary cases (total = 1,
  null name).
- **`shouldAutoUpload(...)` decision** — if cheaply unit-testable given its
  dependencies; otherwise covered by manual testing. (Guard combinations:
  disabled, manual mode, blocked, metered + wifi_only.)

## Components touched

- **New:** `service/cloudsync/documents/DocumentSyncService.kt` (+ op model,
  pure helpers).
- `AndroidManifest.xml` — new service declaration.
- `CloudDocumentsActivity.kt` — enqueue instead of modal for transfers; progress
  event subscription; non-modal indicator; `runSyncAction` kept only for remove.
- `res/layout/activity_cloud_documents.xml` — non-modal progress row.
- `DocumentSync.kt` — `shouldAutoUpload` helper; `pullDocuments` enqueues
  downloads and drops the time-budget; (auto-upload logic moves to the call
  site).
- `BookInstallWatcher.kt` — auto-upload routes through `DocumentSyncService`.
- `strings.xml` — notification + toast + progress strings.

## Explicitly not done

- No per-row progress bars in the list.
- No WorkManager constraints (manual bypasses `wifi_only`; auto gates before
  enqueue).
- No changes to `SyncService` or the DB sync notification.
- No retry/backoff queue persistence across process death (`START_NOT_STICKY`);
  a missed transfer is picked up by the next manual action or sync cycle.
