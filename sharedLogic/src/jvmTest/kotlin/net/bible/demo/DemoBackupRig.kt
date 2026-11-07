package net.bible.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.control.page.window.WindowControl
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.ios.StubCoreStrings
import net.bible.service.db.newTestDir
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Source

/**
 * A real [BackupControl] (the code every platform runs) over the database directory [dir], with recording doubles for
 * its collaborators. The generator uses it for `zipDatabaseFiles()`, the round-trip test for the restore flow.
 * Needs a started Koin with [FixedDateTimeFormats] (BackupControl stamps zip entries through it).
 */
internal class DemoBackupRig(dir: String) {
    val dialogs = AppDialogController()
    val notices = RecordingNotifier()
    val host = ByteBackupHost()
    private val filesDir = newTestDir().toPath()
    private val dirs = object : AppDirectories {
        override val internalFilesDir = filesDir
        override val modulesDir = filesDir / "modules"
        override val manualInstallDir = filesDir / "jsword"
        override val manualInstallDir2 = filesDir / "sword"
        override val manualReadingPlanDir = filesDir / "readingplans"
    }
    private val env = DirEnvironment(dir)
    private val settings = MapSettings()
    private val cloudSync = CloudSync(
        dialogs, StubCoreStrings(), env, settings,
        object : CloudAdapterFactory {
            override val available = listOf("NEXT_CLOUD")
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter = error("no sign-in in a backup test")
        },
        SyncScheduler { error("no sync in a backup test") },
        object : DocumentSyncStarter {
            override fun pushDocuments(initials: List<String>) = error("unused")
            override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) = error("unused")
            override fun stop() = error("unused")
        },
        DocumentSyncProvider { error("no document sync in a backup test") },
        DocumentSyncSettings({ error("unused") }, { error("unused") }, { error("unused") }, OrderedLauncher(CoroutineScope(Job())), env),
    )
    val backup = BackupControl(dialogs, StubCoreStrings(), env, settings, WindowControl(), cloudSync, notices, dirs)
}

internal class RecordingNotifier : UserNotifier {
    val errors = mutableListOf<String>()
    override fun showInfo(message: String, long: Boolean) {}
    override fun showError(message: String, cause: Throwable?) { errors += message }
}

/** Serves a fresh [Buffer] over the given bytes on every [openInput], as a picked file would. */
internal class ByteBackupHost : BackupHost {
    val errors = mutableListOf<String>()
    private var bytes = ByteArray(0)
    fun over(zip: ByteArray) = apply { bytes = zip }
    override suspend fun openInput(source: PickedSource): Source = Buffer().write(bytes)
    override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
    override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
                                 subject: String?, message: String?, chooserTitle: String): DeliveryResult = error("unused")
    override suspend fun restoreModules() = Unit
    override suspend fun showErrorReport(message: String, cause: Throwable?) { errors += message }
}

private class DirEnvironment(private val dir: String) : AppEnvironment {
    override val isInitialized = true
    override val deviceIdentifier = "demo-device"
    override val isDiscrete = false
    override val isBeta = false
    override val isDebugMode = false
    override val applicationNameMedium = "AndBible"
    override val packageName = "net.bible.demo"
    override val versionNumber = 1L
    override val isMeteredNetwork = false
    override val megabytesFree = 1000L
    override val tmpDir = "$dir/tmp"
    override val cacheDir = "$dir/cache"
    override val dbBackupPath = "$dir/backups"
    override fun databasePath(name: String) = "$dir/$name"
    override fun deleteDatabase(name: String) = true
    override val lastCrashTime = 0L
    override val apkPath: String? = null
}

private class MapSettings : AppSettings {
    val m = mutableMapOf<String, Any?>()
    override fun getString(key: String, default: String?) = m[key] as String? ?: default
    override fun getLong(key: String, default: Long) = m[key] as Long? ?: default
    override fun getInt(key: String, default: Int) = m[key] as Int? ?: default
    override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
    override fun getDouble(key: String, default: Double) = m[key] as Double? ?: default
    override fun getFloat(key: String, default: Float) = m[key] as Float? ?: default
    override fun setString(key: String, value: String?) { m[key] = value }
    override fun setLong(key: String, value: Long?) { m[key] = value }
    override fun setInt(key: String, value: Int?) { m[key] = value }
    override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
    override fun setDouble(key: String, value: Double?) { m[key] = value }
    override fun setFloat(key: String, value: Float?) { m[key] = value }
}

internal object FixedDateTimeFormats : DateTimeFormats {
    override fun shortDate(epochMs: Long) = "2026-01-01"
    override fun shortTime(epochMs: Long) = "00:00"
    override fun relativeTimeSpan(epochMs: Long, nowMs: Long) = "now"
    override fun pattern(pattern: String, epochMs: Long) = "20260101000000"
}
