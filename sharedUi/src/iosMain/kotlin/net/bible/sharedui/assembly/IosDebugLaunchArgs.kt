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
import org.crosswire.jsword.book.Books
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
 * - `-resetDatabases`: [resetAppDatabases] before startup, a fresh workspace with the installed modules kept.
 * - `-openRef <osisRef>`: [openRefOnce] navigates the active window to that reference once reading is shown;
 *   with `-openDocument <initials>` it also switches that window to the installed book.
 * - `-featureScenario <chooser|plan-settings|labels|download>`: finite real graph entry after reading is ready.
 * - `-legacyMigrationStepDelayMs <ms>`: [legacyStepDelayMs], delay after the legacy migration's MAP step.
 * - `-seedLegacyFixture <dir>`: [legacySeedDir], see `IosLegacyDebugSeed` (simulator end to end of the legacy migration).
 * - `-standardTestFixture`: explicitly normalize locale and identity device defaults, independently of resetData.
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
    fun featureScenario() = net.bible.sharedui.app.DebugFeatureScenario.parse(Platform.isDebugBinary, arguments)

    /** Explicit device-local fixture, separate from the database-only reset contract. */
    fun normalizeStandardFixture() {
        val settings = net.bible.sharedcore.platform.IosDeviceSettings()
        net.bible.sharedui.app.applyDebugStandardFixture(Platform.isDebugBinary, arguments, settings::setString, settings::setBoolean)
    }

    val referenceApplied: Boolean get() = openRef() == null || openRefDone

    /** True when `-resetData` was passed. */
    fun resetData(): Boolean = Platform.isDebugBinary && "-resetData" in arguments

    /** The value after `-openRef`, or null. */
    fun openRef(): String? = argumentValue("-openRef")

    /**
     * Debug only: `-legacyMigrationStepDelayMs <ms>` slows the legacy migration after its MAP step so a UI test can
     * observe the progress screen. Null when absent, malformed or on a release binary.
     */
    fun legacyStepDelayMs(): Long? = argumentValue("-legacyMigrationStepDelayMs")?.toLongOrNull()?.takeIf { it > 0 }

    /** Debug only: the unpacked fixture scenario directory after `-seedLegacyFixture`, or null. */
    fun legacySeedDir(): String? = argumentValue("-seedLegacyFixture")

    /** The value after `-openDocument` (book initials the `-openRef` window switches to), or null. */
    fun openDocument(): String? = argumentValue("-openDocument")

    private fun argumentValue(name: String): String? {
        if (!Platform.isDebugBinary) return null
        val args = arguments
        val i = args.indexOf(name)
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
            deleteDatabases(support)
        }.onFailure { nsLog("-resetData failed: ${it.stackTraceToString()}") }
        nsLog("-resetData: app data deleted")
    }

    /** True when `-resetDatabases` was passed. */
    fun resetDatabases(): Boolean = Platform.isDebugBinary && "-resetDatabases" in arguments

    /**
     * Deletes only the databases and their backups, keeping the installed modules: the app starts on reading with a
     * fresh one-window workspace on the first installed Bible. Same timing and once-per-process guard as [resetAppData].
     */
    fun resetAppDatabases() {
        if (resetDone) return
        resetDone = true
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first().toString()
        runCatching { deleteDatabases(support) }.onFailure { nsLog("-resetDatabases failed: ${it.stackTraceToString()}") }
        nsLog("-resetDatabases: databases deleted")
    }

    // The Room databases and their backups live beside AndBible/ (IosAppEnvironment.databasePath / dbBackupPath),
    // not inside it: leaving them kept the previous run's workspace (extra split windows) across -resetData.
    private fun deleteDatabases(support: String) {
        FileSystem.SYSTEM.deleteRecursively("$support/databases".toPath())
        FileSystem.SYSTEM.deleteRecursively("$support/backup".toPath())
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
                    // A null document keeps the window's own (a fresh workspace's is the first installed Bible).
                    val document = openDocument()?.let { initials ->
                        checkNotNull(Books.installed().getBook(initials)) { "-openDocument $initials is not installed" }
                    }
                    // The active window itself (showLink opens the links window, i.e. a NEW split window for a fresh workspace).
                    KoinPlatform.getKoin().get<WindowControl>().activeWindowPageManager.setCurrentDocumentAndKey(document, key)
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
