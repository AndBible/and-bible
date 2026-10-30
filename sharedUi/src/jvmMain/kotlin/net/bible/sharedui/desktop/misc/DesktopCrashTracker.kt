package net.bible.sharedui.desktop.misc

import java.nio.file.Files
import java.nio.file.Path
import net.bible.sharedcore.platform.CrashLoopState
import net.bible.sharedcore.platform.CrashPreferences
import net.bible.sharedcore.platform.CrashPreferencesStore
import net.bible.sharedcore.settings.DeviceLocalPrefs

/** Database-independent startup accounting and best-effort uncaught exception capture. */
class DesktopCrashTracker(
    private val settings: DeviceLocalPrefs,
    private val directory: Path,
    clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val state = CrashLoopState(object : CrashPreferencesStore {
        override var running: Boolean
            get() = settings.getBoolean(CrashPreferences.RUNNING, false)
            set(value) = settings.setBoolean(CrashPreferences.RUNNING, value)
        override var count: Int
            get() = settings.getString(CrashPreferences.COUNT, null)?.toIntOrNull() ?: 0
            set(value) = settings.setString(CrashPreferences.COUNT, value.toString())
        override var time: Long
            get() = settings.getString(CrashPreferences.TIME, null)?.toLongOrNull() ?: 0L
            set(value) = settings.setString(CrashPreferences.TIME, value.toString())
    }, clock)
    private var launched = false
    private var recovery = false
    private var previous: Thread.UncaughtExceptionHandler? = null
    private var installed: Thread.UncaughtExceptionHandler? = null

    fun onLaunch() { if (!launched) { launched = true; state.onLaunch() } }
    fun onCleanExit() = state.onCleanExit()
    fun continueStartup() { recovery = false; settings.setBoolean(CrashPreferences.RUNNING, true) }
    fun shouldOfferRecovery(): Boolean = recovery || state.shouldOfferRecovery().also { recovery = it }
    fun wouldOfferRecoveryAfterLaunch(): Boolean = recovery || state.wouldOfferRecoveryAfterLaunch(launched)

    fun recordUncaught(error: Throwable) {
        try {
            Files.createDirectories(directory)
            Files.writeString(directory.resolve("last_crash.txt"), error.stackTraceToString())
        } catch (e: Exception) {
            System.err.println("Could not persist crash: $e")
        }
    }

    fun install() {
        if (installed != null) return
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        previous = prior
        val handler = Thread.UncaughtExceptionHandler { thread, error ->
            recordUncaught(error)
            if (prior != null) prior.uncaughtException(thread, error) else error.printStackTrace()
        }
        installed = handler
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    override fun close() {
        if (Thread.getDefaultUncaughtExceptionHandler() === installed && installed != null) {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        installed = null
        previous = null
    }
}
