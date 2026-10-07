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

package net.bible.service.installzip

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.UserMessages
import net.bible.android.database.BookmarkDatabase
import net.bible.android.view.activity.page.UpdateMainBibleActivityDocuments
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.unzipInputStream
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.bookmarksDbStats
import net.bible.service.device.ProgressNotificationManager
import net.bible.service.sword.epub.EPUB_OPTIMIZER_VERSION
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.epubInitials
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants
import org.crosswire.jsword.book.sword.SwordGenBook
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val NOTIFICATION_ID = 7

/** Same literal channel id `BibleApplication` creates as `ERROR_NOTIFICATION_CHANNEL` -- kept as
 *  a plain literal here (not imported) because that constant is private, mirroring how
 *  `AgentForegroundService.permissionNotificationChannel()` reuses it. */
private const val GENERIC_NOTIFICATION_CHANNEL = "generic-notifications"

/**
 * Thin Android foreground-[Service] shell around [InstallServiceController] (the headless,
 * platform-agnostic install state machine built in A1-A4): owns the app-scoped [controller]
 * instance, wires its [InstallServiceController.JobDeps] to real `contentResolver`/`BackupControl`/
 * `SwordBookPath` collaborators, surfaces progress/action-required notifications, and posts the
 * terminal events (`UserMessages.toast` / [UpdateMainBibleActivityDocuments] /
 * [InstallZipProgress]) classic `InstallZip` posted directly from its Activity.
 *
 * Every job the [controller] runs is driven by data forwarded from THIS Service's own
 * `contentResolver`/`cacheDir`/`getString` -- never by a classic Activity -- so a caller only
 * needs to hand it a list of content [Uri]s via [enqueueIntent]/[start]; the returned/started
 * [Intent] carries [Intent.FLAG_GRANT_READ_URI_PERMISSION] (+ a [ClipData] for more than one uri)
 * so this Service inherits the launching Activity's read grant on each uri without needing its
 * own persistable permission.
 */
class DocumentInstallService : Service() {

    companion object {
        private const val EXTRA_ACTION = "sourceAction"

        /** Number of currently-bound host UIs (Plan B increments/decrements this while a
         *  screen is actively observing [controller]). Zero here -- this task only defines the
         *  seam; while bound, the action-required notification is suppressed because the host
         *  is expected to surface [InstallPhase.AwaitingDecision] in-app instead. */
        private var boundHostCount = 0
        fun hostBound() { boundHostCount++ }
        fun hostUnbound() { if (boundHostCount > 0) boundHostCount-- }
        private fun isHostBound() = boundHostCount > 0

        /** Single app-scoped [InstallServiceController], shared between this Service and any
         *  bound host UI (Plan B), so both observe the very same [InstallServiceController.jobs]. */
        private val _controller: InstallServiceController by lazy {
            InstallServiceController(
                runnerFactory = { InstallJobRunner(InstallInspector { file -> determineLocalFileType(file) }, AndroidInstallCommitter()) },
                newJobId = { UUID.randomUUID().toString() },
                onTerminal = { _, phase -> postTerminalEvents(phase) },
            )
        }
        val controller: InstallServiceController get() = _controller

        fun resolveDecision(jobId: JobId, proceed: Boolean) = controller.resolveDecision(jobId, proceed)
        fun cancel(jobId: JobId) = controller.cancel(jobId)

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        /**
         * Builds the enqueue [Intent] for [uris], forwarding the caller's own read grant on each
         * uri (`FLAG_GRANT_READ_URI_PERMISSION`; a single uri goes on [Intent.setData], more than
         * one via a [ClipData] so every item -- not just the first -- carries the grant). [action]
         * is the originating action (`ACTION_VIEW`/`ACTION_SEND`/...), stored per-batch for parity
         * with classic `InstallZip`'s dispatch; null for a picker-originated batch.
         */
        fun enqueueIntent(context: Context, uris: List<Uri>, action: String? = null): Intent {
            require(uris.isNotEmpty()) { "enqueueIntent requires at least one uri" }
            return Intent(context, DocumentInstallService::class.java).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                action?.let { putExtra(EXTRA_ACTION, it) }
                if (uris.size == 1) {
                    data = uris[0]
                } else {
                    clipData = ClipData.newRawUri(null, uris[0]).apply {
                        for (uri in uris.drop(1)) addItem(ClipData.Item(uri))
                    }
                }
            }
        }

        /** Builds [enqueueIntent] and starts this Service with it. */
        fun start(context: Context, uris: List<Uri>, action: String? = null) {
            startServiceCompat(context, enqueueIntent(context, uris, action))
        }

        /**
         * Fires once per job's terminal [InstallPhase] (from [InstallServiceController.onTerminal],
         * potentially off the main thread -- [ABEventBus.post] is safe to call from any thread).
         * Mirrors classic `InstallZip`/`ZipHandler`/`installFromFile`'s post-install side effects.
         */
        private fun postTerminalEvents(phase: InstallPhase) {
            ABEventBus.post(UpdateMainBibleActivityDocuments())
            when (phase) {
                is InstallPhase.Done -> UserMessages.toast(R.string.install_zip_successfull)
                is InstallPhase.Cancelled -> UserMessages.toast(R.string.install_zip_canceled)
                is InstallPhase.Error -> {
                    val toastRes = if (phase.messageKey == R.string.sqlite_invalid_file) R.string.invalid_module
                    else R.string.error_occurred
                    UserMessages.toast(toastRes)
                }
                else -> {}
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var jobsCollectorJob: Job? = null
    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    /** Guards [Service.onStartCommand] against the case a job cycle completed and drained
     *  `controller.jobs` back to empty BEFORE any job was ever observed by THIS instance's
     *  collector (the very first emission from the shared, app-scoped [controller] can already be
     *  an empty list on a fresh Service instance) -- only stop once real work has actually run. */
    private var hasSeenActiveJob = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        jobsCollectorJob = serviceScope.launch {
            controller.jobs.collect { jobs -> onJobsChanged(jobs) }
        }
    }

    override fun onDestroy() {
        jobsCollectorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            stopSelfSafe()
            return START_NOT_STICKY
        }
        // Must happen synchronously, on every START intent -- Android requires a matching
        // startForeground() within ~5s of startForegroundService() or it kills the process with
        // ForegroundServiceDidNotStartInTimeException. Idempotent on the same notification id, so
        // a second batch arriving while a previous one is still draining is harmless.
        startForeground(NOTIFICATION_ID, progressNotification(null))

        val action = intent?.getStringExtra(EXTRA_ACTION)
        val sources = uris.map { uri -> buildSource(uri, action) }
        val deps = buildDeps()
        serviceScope.launch { controller.enqueue(sources, deps) }
        return START_NOT_STICKY
    }

    private fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        intent.clipData?.let { clip ->
            val fromClip = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            if (fromClip.isNotEmpty()) return fromClip
        }
        return listOfNotNull(intent.data)
    }

    // --- InstallSource / JobDeps wiring (mirrors classic InstallZip's collaborators) ---

    private fun buildSource(uri: Uri, action: String?): InstallSource =
        InstallSource(uri, action, getDisplayName(uri), getMimeType(uri))

    /** Mirrors classic `InstallZip.getDisplayName` exactly. */
    private fun getDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.isLast) return null
            it.moveToFirst()
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) null else it.getString(idx)
        }

    /** Mirrors classic `InstallZip.getMimeType` exactly. */
    private fun getMimeType(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.isLast) return null
            it.moveToFirst()
            val idx = it.getColumnIndex("mime_type")
            if (idx < 0) null else it.getString(idx)
        }

    private fun buildDeps(): InstallServiceController.JobDeps = InstallServiceController.JobDeps(
        acquireDir = File(cacheDir, "installzip"),
        openStream = { source ->
            contentResolver.openInputStream(source.uri) ?: throw FileNotFoundException(source.uri.toString())
        },
        isStudyPadExport = { source ->
            AndBibleBackupManifest.fromUri(source.uri)?.backupType == BackupType.STUDYPAD_EXPORT
        },
        studyPadStats = { localFile -> studyPadStatsFor(localFile) },
        swordZipScan = { localFile -> scanSwordZip(localFile) },
        epubUpgradeCheck = { displayName -> checkEpubUpgrade(displayName) },
    )

    /** Mirrors classic `InstallZip.installStudyPads`'s unzip + stats step, operating on the
     *  already-acquired local file instead of re-opening the original uri. */
    private suspend fun studyPadStatsFor(localFile: File): Pair<String, File> {
        val unzipFolder = File(BackupControl.internalDbBackupDir, "unzip")
        unzipInputStream(localFile.inputStream(), unzipFolder)
        val dbFile = File(unzipFolder, "db/${BookmarkDatabase.dbFileName}")
        val stats = bookmarksDbStats(SyncableDatabaseDefinition.BOOKMARKS, dbFile)
        return stats to unzipFolder
    }

    // --- Notifications ---

    private fun notificationChannel(): String =
        if (BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL
        else ProgressNotificationManager.PROGRESS_NOTIFICATION_CHANNEL

    private fun actionRequiredChannel(): String =
        if (BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else GENERIC_NOTIFICATION_CHANNEL

    private fun notificationTitleRes(): Int =
        if (CommonUtils.isDiscrete) R.string.install_zip_module_discrete else R.string.install_zip_module

    private fun notificationIcon(): Int =
        if (CommonUtils.isDiscrete) R.drawable.ic_calc_24 else R.drawable.ic_ichtys

    private fun buildMainActivityIntent(): PendingIntent {
        // reading-host re-typing T8b: same flags, same PendingIntent, the reading host instead of
        // the classic Activity.
        val intent = NavHostComposeActivity.intentFor(this, NavRoutes.READING).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Notification builders below: Text from the Application, whose Resources follow `locale_pref` live (F114); the Service's own context does not. */
    private fun progressNotification(contentText: String?): Notification =
        NotificationCompat.Builder(this, notificationChannel())
            .setSmallIcon(notificationIcon())
            .setContentTitle(BibleApplication.application.getString(notificationTitleRes()))
            .apply { if (contentText != null) setContentText(contentText) }
            .setContentIntent(buildMainActivityIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun actionRequiredNotification(request: DecisionRequest): Notification =
        NotificationCompat.Builder(this, actionRequiredChannel())
            .setSmallIcon(notificationIcon())
            .setContentTitle(BibleApplication.application.getString(notificationTitleRes()))
            .setContentText(decisionText(request))
            .setContentIntent(buildMainActivityIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

    private fun decisionText(request: DecisionRequest): String = when (request) {
        is DecisionRequest.Overwrite -> BibleApplication.application.getString(R.string.overwrite_files_title)
        is DecisionRequest.EpubUpgrade -> BibleApplication.application.getString(R.string.bookmark_warning)
        is DecisionRequest.StudyPadImport -> request.statsText
    }

    private fun progressText(job: InstallJobState): String? = when (val phase = job.phase) {
        is InstallPhase.Acquiring -> listOfNotNull(job.displayName, "${phase.percent}%").joinToString(" ")
        is InstallPhase.Committing -> listOfNotNull(job.displayName, "${phase.percent}%").joinToString(" ")
        else -> job.displayName
    }

    /** [InstallServiceController.jobs] observer: keeps the notification current and stops the
     *  Service once the queue has fully drained. Runs for the whole Service lifetime (started once
     *  in [onCreate]), across however many [onStartCommand] batches arrive. */
    private fun onJobsChanged(jobs: List<InstallJobState>) {
        if (jobs.isEmpty()) {
            if (hasSeenActiveJob) stopSelfSafe()
            return
        }
        hasSeenActiveJob = true

        val awaiting = jobs.firstOrNull { it.phase is InstallPhase.AwaitingDecision }
        if (awaiting != null && !isHostBound()) {
            val request = (awaiting.phase as InstallPhase.AwaitingDecision).request
            notificationManager.notify(NOTIFICATION_ID, actionRequiredNotification(request))
            return
        }

        val active = jobs.first()
        notificationManager.notify(NOTIFICATION_ID, progressNotification(progressText(active)))
        reportInstallZipProgressFor(active.phase)
    }

    /** Feeds the legacy status-text bus (`InstallZipProgress`, also reported by `EpubOptimization`)
     *  at the two phases classic `InstallZip`'s `updateProgress`/`installZipLabel` actually
     *  displayed distinct text for. */
    private fun reportInstallZipProgressFor(phase: InstallPhase) {
        val messageRes = when (phase) {
            is InstallPhase.Inspecting -> R.string.checking_zip_file
            is InstallPhase.Committing -> R.string.extracting_zip_file
            else -> return
        }
        InstallZipProgress.report(getString(messageRes))
    }

    private fun stopSelfSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}

/** File-based counterpart of `CommonUtils.determineFileType(Uri)`, run against the already-
 *  acquired local file so [InstallInspector] never needs to reopen the (possibly single-use)
 *  original uri stream just to sniff the header. Delegates to [CommonUtils.determineFileType]
 *  (wrapping the local [File] as a `file://` [Uri]) rather than re-implementing the magic-byte
 *  sniffing here, so the two can never silently drift apart. (The exact SQLite3/ZIP magic --
 *  a NUL-terminated `"SQLite format 3\u0000"` and the precise `"PK\u0003\u0004"` ZIP
 *  local-file-header signature -- is easy to get subtly wrong, or fragile, if duplicated as a
 *  hand-typed literal with embedded raw control bytes.) `internal` (not `private`) so
 *  [DocumentInstallServiceTest] can drive it directly as a regression lock. */
internal suspend fun determineLocalFileType(file: File): BackupControl.AbDbFileType =
    CommonUtils.determineFileType(Uri.fromFile(file))

/**
 * Adapts classic `ZipHandler.checkZipFile`'s entry-enumeration + classification to operate on an
 * already-acquired local [File] and return a pure [InstallInspector.SwordZipScan] instead of
 * throwing [net.bible.android.view.activity.installzip.ModulesExists]/[net.bible.android.view.activity.installzip.EpubFile]/
 * [net.bible.android.view.activity.installzip.InvalidModule]. Existing-file paths are plain
 * download-dir-relative paths (classic additionally ran `.canonicalPath` on the relative
 * `File`, which resolves against the JVM's working directory rather than the download dir --
 * not reproduced here, as it only ever fed a display string).
 */
private fun scanSwordZip(localFile: File): InstallInspector.SwordZipScan {
    val targetDirectory = SwordBookPath.getSwordDownloadDir()
    val existingFiles = mutableListOf<String>()
    val otherFiles = mutableListOf<String>()
    var modsDirFound = false
    var modulesFound = false
    var totalEntries = 0

    ZipInputStream(localFile.inputStream()).use { zin ->
        var entry: ZipEntry? = try {
            zin.nextEntry
        } catch (e: IllegalArgumentException) {
            null
        } ?: return InstallInspector.SwordZipScan(emptyList(), 0, isEpub = false, invalid = true)

        while (entry != null) {
            totalEntries++
            val name = entry.name.replace('\\', '/')
            val targetFile = File(targetDirectory, name)
            if (!entry.isDirectory && targetFile.exists()) {
                existingFiles.add(targetFile.relativeTo(targetDirectory).path)
            }
            when {
                name.startsWith(SwordConstants.DIR_CONF + "/") && name.endsWith(SwordConstants.EXTENSION_CONF) ->
                    modsDirFound = true
                name.startsWith(SwordConstants.DIR_CONF + "/") -> Unit // ignore directory entry
                name.startsWith(SwordConstants.DIR_DATA + "/") -> modulesFound = true
                name.startsWith("epub/") || name.startsWith("mysword/") || name.startsWith("mybible/") || name.startsWith("esword/") -> {
                    modulesFound = true
                    modsDirFound = true
                }
                name == ANDBIBLE_BACKUP_MANIFEST_FILENAME -> Unit
                else -> otherFiles.add(name)
            }
            entry = zin.nextEntry
        }
    }

    if (otherFiles.isNotEmpty()) {
        val isEpub = otherFiles.any { it == "META-INF/container.xml" }
        return InstallInspector.SwordZipScan(existingFiles, totalEntries, isEpub = isEpub, invalid = !isEpub)
    }
    if (!(modsDirFound && modulesFound)) {
        return InstallInspector.SwordZipScan(existingFiles, totalEntries, isEpub = false, invalid = true)
    }
    return InstallInspector.SwordZipScan(existingFiles, totalEntries, isEpub = false, invalid = false)
}

/**
 * Mirrors classic `InstallZip.installEpub`'s optimizer-version + bookmark-count test: an upgrade
 * confirmation is needed only when the epub is already installed, has genuine bookmarks/notes
 * against it, AND its persisted optimizer version is behind the current one.
 */
private suspend fun checkEpubUpgrade(displayName: String): Boolean {
    val dir = File(SharedConstants.modulesDir, "epub/$displayName")
    if (!dir.exists()) return false
    val initials = epubInitials(displayName)
    val book = Books.installed().getBook(initials)
    val optimizerVersion = ((book as? SwordGenBook)?.backend as? EpubBackend)?.state?.optimizerVersion ?: 1
    val bookmarkCount = if (DatabaseContainer.ready) {
        DatabaseContainer.instance.bookmarkDb.bookmarkDao().genericBookmarkCountFor(initials)
    } else 0
    return bookmarkCount > 0 && optimizerVersion < EPUB_OPTIMIZER_VERSION
}
