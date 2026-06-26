# Document Sync Background Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every document-sync data transfer (manual, auto-on-install, auto-pull) through a new `DocumentSyncService` foreground service with one per-document progress notification, so transfers never block the UI.

**Architecture:** A new foreground service (`foregroundServiceType="dataSync"`) owns a thread-safe op queue. Callers enqueue `Push`/`Download` ops via `DocumentSyncService.start(context, pushInitials, downloadInitials)`; a single consumer coroutine drains the queue, calling the existing `DocumentSync.pushDocument` / `downloadAndInstall` workers, updating a progress notification, and posting `DocumentSyncProgressEvent` over `ABEventBus`. `CloudDocumentsActivity` enqueues instead of running a modal `Hourglass`, and refreshes on the completion event. The auto paths (`BookInstallWatcher`, `DocumentSync.pullDocuments`) enqueue to the same service after their existing guards.

**Tech Stack:** Kotlin, Android Service / foreground service, kotlinx.coroutines, greenrobot EventBus (`ABEventBus`), JUnit (`kotlin.test`-style on JVM unit tests).

## Global Constraints

- New files use the copyright header: `Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.` (no "Martin Denham"). Edited existing files: update year to `2026` and ensure `Sykerö Software / Tuomas Airaksinen` is present (keep "Martin Denham" if already there).
- Kotlin/Java convention: import classes, use simple names — never fully-qualified names in code.
- All user-facing strings go to `app/src/main/res/values/strings.xml` in **English** only. Never hardcode user-visible text.
- UI must work in dark, light, monochrome/e-ink, and no-animations modes. The progress bar is a determinate horizontal bar (no spinner), works in grayscale.
- Kotlin-only change → run `./gradlew testStandardGoogleplayDebugUnitTest` for unit tests. All Gradle commands require `dangerouslyDisableSandbox: true`. Do **not** run Vue tests.
- Tests live in `app/src/test/java/...`.

---

### Task 1: Pure op model + helper functions (TDD)

Pure, Android-free logic in its own file so it is trivially unit-testable on the JVM.

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncOps.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncOpsTest.kt`

**Interfaces:**
- Produces:
  - `sealed class DocumentSyncOp { abstract val initials: String; data class Push(override val initials: String); data class Download(override val initials: String) }`
  - `fun buildDocumentSyncOps(pushInitials: List<String>, downloadInitials: List<String>): List<DocumentSyncOp>` — pushes first, then downloads, order preserved.
  - `fun documentSyncProgressText(name: String, current: Int, total: Int): String` — returns `"$name ($current/$total)"`.
  - `fun shouldAutoUpload(enabled: Boolean, automatic: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean` — `enabled && automatic && !blocked && autoTransferAllowed`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncOpsTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSyncOpsTest {
    @Test
    fun buildsPushesThenDownloadsInOrder() {
        val ops = buildDocumentSyncOps(listOf("KJV", "FinRK"), listOf("ESV"))
        assertEquals(
            listOf(
                DocumentSyncOp.Push("KJV"),
                DocumentSyncOp.Push("FinRK"),
                DocumentSyncOp.Download("ESV"),
            ),
            ops,
        )
    }

    @Test
    fun buildsEmptyWhenNoInitials() {
        assertEquals(emptyList<DocumentSyncOp>(), buildDocumentSyncOps(emptyList(), emptyList()))
    }

    @Test
    fun progressTextFormatsNameAndCounts() {
        assertEquals("KJV (2/5)", documentSyncProgressText("KJV", 2, 5))
        assertEquals("ESV (1/1)", documentSyncProgressText("ESV", 1, 1))
    }

    @Test
    fun shouldAutoUploadOnlyWhenAllConditionsMet() {
        assertTrue(shouldAutoUpload(enabled = true, automatic = true, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = false, automatic = true, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, automatic = false, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, automatic = true, blocked = true, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, automatic = true, blocked = false, autoTransferAllowed = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncOpsTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — unresolved references `buildDocumentSyncOps`, `DocumentSyncOp`, etc.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncOps.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.cloudsync.documents

/** A single document-sync transfer the [DocumentSyncService] queue can process. */
sealed class DocumentSyncOp {
    abstract val initials: String
    /** Upload a locally installed document to the cloud. */
    data class Push(override val initials: String) : DocumentSyncOp()
    /** Download + install a document from the cloud. */
    data class Download(override val initials: String) : DocumentSyncOp()
}

/** Builds the ordered op list for a transfer batch: all pushes first, then all downloads. */
fun buildDocumentSyncOps(pushInitials: List<String>, downloadInitials: List<String>): List<DocumentSyncOp> =
    pushInitials.map { DocumentSyncOp.Push(it) } + downloadInitials.map { DocumentSyncOp.Download(it) }

/** Notification/progress label, e.g. "KJV (2/5)". */
fun documentSyncProgressText(name: String, current: Int, total: Int): String = "$name ($current/$total)"

/** Whether an installed document should be auto-uploaded on install. */
fun shouldAutoUpload(enabled: Boolean, automatic: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean =
    enabled && automatic && !blocked && autoTransferAllowed
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncOpsTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncOps.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncOpsTest.kt
git commit -m "$(cat <<'EOF'
Add DocumentSyncOp model and pure helpers for background sync

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV
EOF
)"
```

---

### Task 2: DocumentSyncService foreground service + progress event

Creates the service that drains the op queue, shows a per-document progress notification, and posts progress events. Nothing calls it yet — this task is self-contained (compiles, manifest-registered). Service/foreground wiring is not unit-tested (verified by manual testing in Task 3).

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `<service>` next to the existing `SyncService` declaration, ~line 374–385)
- Modify: `app/src/main/res/values/strings.xml` (add `document_sync_notification_title`)

**Interfaces:**
- Consumes (from Task 1): `DocumentSyncOp`, `buildDocumentSyncOps`, `documentSyncProgressText`.
- Consumes (existing): `DocumentSync.pushDocument(book: Book)`, `DocumentSync.downloadAndInstall(initials: String)`, `Books.installed().getBook(initials)`, `SYNC_NOTIFICATION_CHANNEL`, `CALC_NOTIFICATION_CHANNEL`, `BibleApplication.application`, `BibleApplication.ErrorNotificationEvent`.
- Produces (for Tasks 3 & 4):
  - `DocumentSyncService.start(context: Context, pushInitials: List<String>, downloadInitials: List<String>)`
  - `class DocumentSyncProgressEvent(val running: Boolean, val current: Int, val total: Int, val currentName: String?)`

- [ ] **Step 1: Add the notification-title string**

In `app/src/main/res/values/strings.xml`, add near the other `cloud_doc_*` strings (around line 1915):

```xml
<string name="document_sync_notification_title">Syncing documents</string>
```

- [ ] **Step 2: Create the service**

Create `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncService.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.cloudsync.documents

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.service.cloudsync.SYNC_NOTIFICATION_CHANNEL
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.Books
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val DOC_SYNC_NOTIFICATION_ID = 6
private const val WAKELOCK_TAG = "andbible:docsync-wakelock"
private const val WAKELOCK_TIMEOUT_MS = 30L * 60 * 1000 // 30 minutes
private const val TAG = "DocumentSyncService"

/**
 * Posted on [ABEventBus] as document transfers progress. [running] is false once the
 * queue is drained. [current]/[total] count completed-or-in-progress vs total ops in
 * the active batch; [currentName] is the document being transferred (null at start/end).
 */
class DocumentSyncProgressEvent(
    val running: Boolean,
    val current: Int,
    val total: Int,
    val currentName: String?,
)

/**
 * Foreground service that runs all document-sync transfers (push/download) off the UI
 * thread, with a single per-document progress notification. Callers enqueue ops via
 * [start]; a single consumer coroutine drains the queue and posts [DocumentSyncProgressEvent].
 */
class DocumentSyncService : Service() {
    companion object {
        private const val START = "action_document_sync"
        private const val EXTRA_PUSH = "pushInitials"
        private const val EXTRA_DOWNLOAD = "downloadInitials"

        /**
         * Enqueues a transfer batch and starts the foreground service. Manual callers pass
         * explicit pushes/downloads (they bypass the wifi-only guard by design); auto callers
         * must apply their guards first. No-op if both lists are empty.
         */
        fun start(context: Context, pushInitials: List<String>, downloadInitials: List<String>) {
            if (pushInitials.isEmpty() && downloadInitials.isEmpty()) return
            val intent = Intent(context, DocumentSyncService::class.java).apply {
                action = START
                putStringArrayListExtra(EXTRA_PUSH, ArrayList(pushInitials))
                putStringArrayListExtra(EXTRA_DOWNLOAD, ArrayList(downloadInitials))
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                // e.g. Android 12+ background-start restriction or 14+ dataSync quota.
                Log.e(TAG, "Could not start document sync service", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<DocumentSyncOp>()
    private val active = AtomicBoolean(false)
    private val total = AtomicInteger(0)
    private val done = AtomicInteger(0)

    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val powerManager get() = getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != START) { stopSelfSafe(); return START_NOT_STICKY }
        val push = intent.getStringArrayListExtra(EXTRA_PUSH) ?: arrayListOf()
        val download = intent.getStringArrayListExtra(EXTRA_DOWNLOAD) ?: arrayListOf()
        val ops = buildDocumentSyncOps(push, download)
        if (ops.isEmpty()) { stopSelfSafe(); return START_NOT_STICKY }

        // fresh == this batch starts a new drain session (no drain currently running).
        val fresh = active.compareAndSet(false, true)
        if (fresh) { done.set(0); total.set(0) }
        queue.addAll(ops)
        total.addAndGet(ops.size)
        if (fresh) {
            startForegroundSafe()
            drain()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafe() {
        try {
            startForeground(DOC_SYNC_NOTIFICATION_ID, buildNotification(null, 0, total.get()))
        } catch (e: Exception) {
            // Android 14+ may refuse a dataSync FGS once the daily quota is exhausted.
            // Keep processing in the background scope rather than crashing.
            Log.e(TAG, "Could not start foreground; processing in background", e)
        }
        acquireWakeLock()
    }

    private fun drain() = scope.launch {
        ABEventBus.post(DocumentSyncProgressEvent(true, 0, total.get(), null))
        while (true) {
            val op = queue.poll()
            if (op == null) {
                // No more work: relinquish ownership, then re-check for a late enqueue.
                active.set(false)
                if (queue.peek() == null) break
                if (!active.compareAndSet(false, true)) break // another start() took over
                continue
            }
            val current = done.get() + 1
            val totalNow = total.get()
            updateNotification(op.initials, current, totalNow)
            ABEventBus.post(DocumentSyncProgressEvent(true, current, totalNow, op.initials))
            try {
                when (op) {
                    is DocumentSyncOp.Push -> Books.installed().getBook(op.initials)?.let { DocumentSync.pushDocument(it) }
                    is DocumentSyncOp.Download -> DocumentSync.downloadAndInstall(op.initials)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Document sync op failed: ${op.initials}", e)
                ABEventBus.post(BibleApplication.ErrorNotificationEvent(R.string.sync_error))
            }
            done.incrementAndGet()
        }
        ABEventBus.post(DocumentSyncProgressEvent(false, done.get(), total.get(), null))
        stopSelfSafe()
    }

    private fun notificationChannel(): String =
        if (BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else SYNC_NOTIFICATION_CHANNEL

    private fun buildNotification(name: String?, current: Int, total: Int) =
        NotificationCompat.Builder(this, notificationChannel())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setOngoing(true)
            .setSmallIcon(if (CommonUtils.isDiscrete) R.drawable.ic_calc_24 else R.drawable.ic_syncdb_24dp)
            .setContentTitle(getString(R.string.document_sync_notification_title))
            .apply {
                if (name != null) {
                    setContentText(documentSyncProgressText(name, current, total))
                    setProgress(total, current, false)
                }
            }
            .build()

    private fun updateNotification(name: String, current: Int, total: Int) {
        notificationManager.notify(DOC_SYNC_NOTIFICATION_ID, buildNotification(name, current, total))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).also {
            it.acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopSelfSafe() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Step 3: Register the service in the manifest**

In `app/src/main/AndroidManifest.xml`, immediately after the existing `SyncService` `<service>` block (the one with `android:name="net.bible.service.cloudsync.SyncService"`), add:

```xml
        <service
            android:name="net.bible.service.cloudsync.documents.DocumentSyncService"
            android:foregroundServiceType="dataSync" />
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncService.kt \
        app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
Add DocumentSyncService foreground service for background document transfers

Drains a push/download op queue with a per-document progress notification
and posts DocumentSyncProgressEvent. Not wired to callers yet.

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV
EOF
)"
```

---

### Task 3: Wire CloudDocumentsActivity transfers to the service (non-modal)

Replace the modal `Hourglass` path for transfers with service enqueue + a non-modal in-activity progress indicator. Keep the modal only for remove-from-cloud. Verified by manual on-device testing.

**Files:**
- Modify: `app/src/main/res/layout/activity_cloud_documents.xml` (add progress row)
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`
- Modify: `app/src/main/res/values/strings.xml` (add `document_sync_started`)

**Interfaces:**
- Consumes (from Task 2): `DocumentSyncService.start(...)`, `DocumentSyncProgressEvent`, `documentSyncProgressText(...)`.

- [ ] **Step 1: Add the toast string**

In `app/src/main/res/values/strings.xml`, near `document_sync_notification_title`:

```xml
<string name="document_sync_started">Document sync started</string>
```

- [ ] **Step 2: Add the non-modal progress row to the layout**

In `app/src/main/res/layout/activity_cloud_documents.xml`, insert this block immediately after the `header` `TextView` (before the filter-row `LinearLayout`):

```xml
    <LinearLayout
        android:id="@+id/syncProgress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="16dp"
        android:paddingVertical="4dp"
        android:visibility="gone">

        <ProgressBar
            android:id="@+id/syncProgressBar"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1" />

        <TextView
            android:id="@+id/syncProgressText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:textColor="?android:attr/textColorSecondary"
            android:textSize="13sp" />
    </LinearLayout>
```

- [ ] **Step 3: Register for progress events and handle them**

In `CloudDocumentsActivity.kt`:

Add imports:
```kotlin
import android.widget.Toast
import net.bible.android.control.event.ABEventBus
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.DocumentSyncProgressEvent
import net.bible.service.cloudsync.documents.documentSyncProgressText
```

At the end of `onCreate(...)`, after `refresh()`, add:
```kotlin
        ABEventBus.register(this)
```

Add an `onDestroy` override (place it after `onBackPressed`):
```kotlin
    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    @Suppress("unused") // called by greenrobot EventBus on the main thread
    fun onEventMainThread(event: DocumentSyncProgressEvent) {
        if (event.running) {
            binding.syncProgress.visibility = View.VISIBLE
            binding.syncProgressBar.max = event.total
            binding.syncProgressBar.progress = event.current
            binding.syncProgressText.text = event.currentName
                ?.let { documentSyncProgressText(it, event.current, event.total) }
                ?: getString(R.string.synchronizing)
        } else {
            binding.syncProgress.visibility = View.GONE
            refresh()
        }
    }
```

- [ ] **Step 4: Route setup, bulk, and per-item transfers through the service**

Replace `performSetupSync()` with:
```kotlin
    private fun performSetupSync() {
        // "Start syncing" is the commit point: enable document sync now (auto mode
        // defers enabling until here, so backing out of setup leaves sync off).
        DocumentSyncSettings.enabled = true
        val selected = adapter.getSelectedInitials()
        val toPush = allItems.filter { it.initials in selected && it.localOnly }.map { it.initials }
        val toDownload = allItems.filter { it.initials in selected && (it.cloudOnly || it.updateAvailable) }.map { it.initials }
        // Cloud-only items the user opted out of get blocked so they aren't pulled later.
        val toBlock = allItems.filter { it.initials !in selected && it.cloudOnly }.map { it.initials }
        for (initials in toBlock) DocumentSyncSettings.blockList.block(initials)
        DocumentSyncService.start(this, pushInitials = toPush, downloadInitials = toDownload)
        if (toPush.isNotEmpty() || toDownload.isNotEmpty()) {
            Toast.makeText(this, R.string.document_sync_started, Toast.LENGTH_SHORT).show()
        }
        exitSetupMode()
        refresh()
    }
```

Replace `performBulkAction()` with:
```kotlin
    private fun performBulkAction() {
        val selected = adapter.getSelectedInitials()
        val toDownload = allItems.filter { it.initials in selected && (it.cloudOnly || it.updateAvailable) }.map { it.initials }
        DocumentSyncService.start(this, pushInitials = emptyList(), downloadInitials = toDownload)
        exitSelectionMode()
    }
```

In `performAction(...)`, replace the `DOWNLOAD` and `PUSH` branches with:
```kotlin
            CloudDocAction.DOWNLOAD -> DocumentSyncService.start(this, emptyList(), listOf(item.initials))
            CloudDocAction.PUSH -> DocumentSyncService.start(this, listOf(item.initials), emptyList())
```

- [ ] **Step 5: Remove now-dead code**

`runSyncAction` is now used only by `confirmRemoveFromCloud`; keep it. Remove the now-unused `Books` import if no other reference remains. Verify:

Run: `grep -n "Books\.\|Books$" app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`
Expected: no matches → delete `import org.crosswire.jsword.book.Books`. If matches remain, keep the import.

- [ ] **Step 6: Build to verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Manual on-device verification**

Build + install: `./gradlew assembleStandardGithubDebug` then `adb install -r app/build/outputs/apk/standardGithub/debug/app-standard-github-debug.apk` (with `dangerouslyDisableSandbox: true`).
Verify:
- Enable document sync (auto mode) → setup screen → "Start syncing": the screen does NOT freeze; a "Document sync started" toast appears; a "Syncing documents — X (n/total)" notification appears; the app stays usable; the list refreshes when done.
- Per-item download/push from the 3-dot menu also runs via the notification, non-blocking.
- Remove-from-cloud still uses the brief modal (unchanged).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/main/res/layout/activity_cloud_documents.xml app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
Run CloudDocumentsActivity transfers in background via DocumentSyncService

Setup "Start syncing", bulk download, and per-item download/push now enqueue
to the foreground service with a progress notification instead of blocking the
UI behind a modal. Remove-from-cloud keeps its brief modal.

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV
EOF
)"
```

---

### Task 4: Route auto paths (install upload + sync-cycle pull) through the service

Make auto-upload-on-install and auto-pull use the same service, so the notification is unified everywhere. Drop the obsolete pull time-budget.

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt`
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt`

**Interfaces:**
- Consumes (from Tasks 1 & 2): `shouldAutoUpload(...)`, `DocumentSyncService.start(...)`.

- [ ] **Step 1: Route auto-upload-on-install through the service**

In `BookInstallWatcher.kt`, replace the `bookAdded` document-sync block:
```kotlin
                if (DocumentSyncSettings.enabled && DocumentSyncSettings.automatic) {
                    syncScope.launch {
                        try { DocumentSync.uploadDocument(book) }
                        catch (e: Exception) { Log.e(TAG, "Document sync upload failed for ${book.initials}", e) }
                    }
                }
```
with:
```kotlin
                if (shouldAutoUpload(
                        DocumentSyncSettings.enabled,
                        DocumentSyncSettings.automatic,
                        DocumentSyncSettings.blockList.isBlocked(book.initials),
                        DocumentSyncSettings.isAutoTransferAllowed,
                    )
                ) {
                    DocumentSyncService.start(
                        BibleApplication.application,
                        pushInitials = listOf(book.initials),
                        downloadInitials = emptyList(),
                    )
                }
```
Update imports: add
```kotlin
import net.bible.android.BibleApplication
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.shouldAutoUpload
```
Remove the now-unused `syncScope` field and the `CoroutineScope`/`Dispatchers`/`launch` imports **only if** `syncScope` is no longer referenced. Verify:

Run: `grep -n "syncScope" app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt`
Expected: no matches after the edit → delete `private val syncScope = CoroutineScope(Dispatchers.IO)` and its three imports (`kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`).

- [ ] **Step 2: Make pullDocuments enqueue downloads and drop the time budget**

In `DocumentSync.kt`:

Add import:
```kotlin
import net.bible.android.BibleApplication
```

Delete the constant:
```kotlin
/** Wall-clock budget for DOWNLOAD/UPGRADE actions in a single pull cycle (ms). */
private const val PULL_DOWNLOAD_BUDGET_MS = 150_000L
```

Replace the body of `pullDocuments(...)` from the action loop onward. The full method becomes:
```kotlin
    suspend fun pullDocuments(automaticOnly: Boolean) {
        if (!DocumentSyncSettings.enabled) return
        if (automaticOnly && (!DocumentSyncSettings.automatic || !DocumentSyncSettings.isAutoTransferAllowed)) return
        val store = store() ?: return
        val cloudMetas = store.listDocuments()
        val local = installedSyncableBooks().associateBy { it.initials }
        val cloudDocs = cloudMetas.map {
            CloudDocument(it.initials, it.name, it.documentType, it.version, it.size, it.timestamp, it.deleted)
        }
        val localDocs = local.mapValues { (i, b) -> LocalDocument(i, DocumentArchiver.documentVersion(b)) }
        val syncTimestamps = local.keys.mapNotNull { i -> DocumentSyncSettings.syncTimestamp(i)?.let { i to it } }.toMap()
        val actions = resolveDocumentSyncActions(
            cloudDocs, localDocs, syncTimestamps,
            DocumentSyncSettings.blockList.all(), ::versionIsNewer)
        val toDownload = mutableListOf<String>()
        for (action in actions) when (action.type) {
            DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> toDownload.add(action.initials)
            DocumentSyncActionType.UNINSTALL -> uninstallLocal(action.initials, local)
            DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
        }
        if (toDownload.isNotEmpty()) {
            DocumentSyncService.start(BibleApplication.application, pushInitials = emptyList(), downloadInitials = toDownload)
        }
    }
```

- [ ] **Step 3: Remove the now-unused uploadDocument**

`DocumentSync.uploadDocument` was only called by `BookInstallWatcher`. Verify and remove:

Run: `grep -rn "\.uploadDocument\|fun uploadDocument" app/src/main/java app/src/test/java`
Expected: only the definition in `DocumentSync.kt` remains → delete the whole `uploadDocument` function:
```kotlin
    suspend fun uploadDocument(book: Book) {
        if (!DocumentSyncSettings.enabled || !DocumentSyncSettings.automatic) return
        if (DocumentSyncSettings.blockList.isBlocked(book.initials)) return
        if (!DocumentSyncSettings.isAutoTransferAllowed) return
        pushDocument(book)
    }
```
If `grep` shows other callers, leave it in place.

- [ ] **Step 4: Build and run unit tests**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSync*"` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL, all matching tests PASS (Task 1 tests + any existing DocumentSync tests).

- [ ] **Step 5: Manual on-device verification**

Build + install (as in Task 3 Step 7). Verify:
- Installing a new document (repository download or sideload) while auto sync is on shows the "Syncing documents" notification (no longer silent), and the app stays usable.
- A second device with cloud documents: enabling sync / a normal sync cycle downloads pending documents via the notification, non-blocking.
- With WiFi-only on + metered network: auto upload/pull does NOT start (guard holds); a manual download from the management view still proceeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt \
        app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt
git commit -m "$(cat <<'EOF'
Route auto document sync (install upload, sync-cycle pull) through service

Auto-upload-on-install and auto-pull downloads now enqueue to
DocumentSyncService, giving every document transfer the same per-document
progress notification. Drops the obsolete pull time-budget and the dead
DocumentSync.uploadDocument.

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV
EOF
)"
```

---

## Notes for the implementer

- **Why a fresh/active flag instead of a Mutex around the drain:** `onStartCommand` runs on the main thread, so `active.compareAndSet` cleanly distinguishes the batch that starts a new drain session (resets counts, calls `startForeground` + `drain`) from later batches that just extend the queue. The drain's poll→`active.set(false)`→re-check→`compareAndSet` tail closes the race where a late `start()` enqueues just as the queue empties.
- **Labels are initials** (e.g. "KJV"), not full names — downloads would otherwise need an extra cloud lookup for the name. Initials are a fine, compact notification label.
- **Wifi-only guard placement:** the service always transfers (manual actions bypass wifi-only by design). Auto callers (`BookInstallWatcher` via `shouldAutoUpload`, `pullDocuments` via its entry guard) check `isAutoTransferAllowed` before enqueuing.
- **No `START_STICKY`:** a killed transfer is re-driven by the next manual action or sync cycle; re-running an empty restart intent would be pointless.
```
