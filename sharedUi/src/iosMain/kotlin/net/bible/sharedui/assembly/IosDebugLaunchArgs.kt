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
     * Deletes the app data (Application Support/AndBible with the databases, Documents/sword with the modules).
     * Main thread, before [IosAppStartup.start]: two recursive deletes, no database or settings access.
     * UserDefaults are left alone.
     */
    fun resetAppData() {
        if (resetDone) return // the Swift host may build the view controller more than once; never wipe a live app
        resetDone = true
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first().toString() + "/AndBible"
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first().toString()
        val dirs = IosAppDirectories(support.toPath(), documents.toPath())
        runCatching {
            FileSystem.SYSTEM.deleteRecursively(dirs.internalFilesDir)
            FileSystem.SYSTEM.deleteRecursively(dirs.modulesDir)
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
        openRefDone = true
        withContext(Dispatchers.Default) {
            repeat(10) { attempt ->
                try {
                    val key = PassageKeyFactory.instance().getKey(KJVA, ref)
                    // The active window itself (showLink opens the links window, i.e. a NEW split window for a fresh workspace).
                    KoinPlatform.getKoin().get<WindowControl>().activeWindowPageManager.setCurrentDocumentAndKey(null, key)
                    nsLog("-openRef $ref: shown")
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
