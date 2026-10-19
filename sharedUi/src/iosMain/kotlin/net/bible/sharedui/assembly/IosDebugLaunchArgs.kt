package net.bible.sharedui.assembly

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.KJVA
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.IosAppDirectories
import okio.FileSystem
import okio.Path.Companion.toPath
import org.crosswire.jsword.passage.PassageKeyFactory
import org.koin.mp.KoinPlatform
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.native.Platform

/**
 * Debug-only launch arguments for the real-app XCUITests (`iosApp/iosAppUITests/AppUITests.swift`). Callers must
 * check `Platform.isDebugBinary` first; release builds never read them.
 *
 * - `-resetData`: [resetAppData] before startup, so the app starts as a fresh install.
 * - `-openRef <osisRef>`: [openRefOnce] navigates the active window to that reference once reading is shown.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal object IosDebugLaunchArgs {
    private val arguments: List<String> get() = NSProcessInfo.processInfo.arguments.map { it.toString() }

    /** Seeds the persistent domain (not the read-only argument domain), before crash counting. Debug only. */
    fun seedCrashRecovery(settings: net.bible.sharedcore.platform.IosDeviceSettings) {
        if (!Platform.isDebugBinary || "-seedCrashRecovery" !in arguments) return
        settings.putBoolean(net.bible.sharedcore.platform.CrashPreferences.RUNNING, false)
        settings.putInt(net.bible.sharedcore.platform.CrashPreferences.COUNT, 2)
        settings.putLong(net.bible.sharedcore.platform.CrashPreferences.TIME, kotlin.time.Clock.System.now().toEpochMilliseconds())
    }

    /** X-owned debug controls, independent of the H-owned Settings implementation. */
    fun task6Controls(): Boolean = Platform.isDebugBinary && "-task6DebugActions" in arguments

    /** Explicit disposable-simulator reset, never a read-only argument-domain preference override. */
    fun resetTask6CrashState(settings: net.bible.sharedcore.platform.IosDeviceSettings) {
        if (!Platform.isDebugBinary || "-task6ResetCrashState" !in arguments) return
        settings.putBoolean(net.bible.sharedcore.platform.CrashPreferences.RUNNING, false)
        settings.putInt(net.bible.sharedcore.platform.CrashPreferences.COUNT, 0)
        settings.putLong(net.bible.sharedcore.platform.CrashPreferences.TIME, 0L)
    }

    /** Disposable cross-platform acceptance only; no persisted override or production namespace change. */
    fun syncNamespace(): String? = net.bible.service.cloudsync.ios.task7SyncNamespace(Platform.isDebugBinary, arguments)

    /** True when `-resetData` was passed. */
    fun resetData(): Boolean = Platform.isDebugBinary && "-resetData" in arguments

    /** The value after `-openRef`, or null. */
    fun openRef(): String? {
        if (!Platform.isDebugBinary) return null
        val args = arguments
        val i = args.indexOf("-openRef")
        return if (i >= 0) args.getOrNull(i + 1) else null
    }

    @kotlin.concurrent.Volatile
    private var resetDone = false

    @kotlin.concurrent.Volatile
    private var openRefDone = false

    /**
     * Deletes the app data (Application Support/AndBible, databases and backup; Documents/sword with the modules).
     * Process-owned IO startup, before [IosAppStartup.ensureStarted] side effects: two recursive deletes, no database or settings access.
     * UserDefaults are left alone.
     */
    fun resetAppData() {
        if (resetDone) return // the Swift host may build the view controller more than once; never wipe a live app
        resetDone = true
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first().toString()
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first().toString()
        val dirs = IosAppDirectories("$support/AndBible".toPath(), documents.toPath())
        runCatching {
            FileSystem.SYSTEM.deleteRecursively(dirs.internalFilesDir)
            FileSystem.SYSTEM.deleteRecursively(dirs.modulesDir)
            // The Room databases and their backups live beside AndBible/ (IosAppEnvironment.databasePath / dbBackupPath),
            // not inside it: leaving them kept the previous run's workspace (extra split windows) across -resetData.
            FileSystem.SYSTEM.deleteRecursively("$support/databases".toPath())
            FileSystem.SYSTEM.deleteRecursively("$support/backup".toPath())
        }.onFailure { nsLog("-resetData failed: ${it.stackTraceToString()}") }
        nsLog("-resetData: app data deleted")
    }

    /**
     * Opens `-openRef` once per process, off the main thread, by setting the key on the active window's page manager (not
     * `WindowControl.showLink`, which targets the links window and opens a new split window). Retries a few times while the window repository is still loading.
     */
    suspend fun openRefOnce() {
        val ref = openRef() ?: return
        if (openRefDone) return
        withContext(Dispatchers.Default) {
            repeat(10) { attempt ->
                try {
                    val key = PassageKeyFactory.instance().getKey(KJVA, ref)
                    // The active window itself (showLink opens the links window, i.e. a NEW split window for a fresh workspace).
                    KoinPlatform.getKoin().get<WindowControl>().activeWindowPageManager.setCurrentDocumentAndKey(null, key)
                    nsLog("-openRef $ref: shown")
                    openRefDone = true // only after success: a cancelled LaunchedEffect may run again
                    return@withContext
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    nsLog("-openRef $ref attempt $attempt failed: ${e.stackTraceToString()}")
                    delay(500)
                }
            }
        }
    }
}
