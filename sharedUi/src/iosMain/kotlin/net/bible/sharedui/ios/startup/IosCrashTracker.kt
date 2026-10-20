package net.bible.sharedui.ios.startup

import net.bible.sharedcore.platform.CrashLoopState
import net.bible.sharedcore.platform.CrashPreferences
import net.bible.sharedcore.platform.CrashPreferencesStore
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.log.nsLog
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.time.Clock

/** Best-effort Kotlin crash capture, independent of Koin and all databases. Native signals remain OS crash reports. */
internal class IosCrashTracker(
    private val settings: IosDeviceSettings,
    clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val state = CrashLoopState(object : CrashPreferencesStore {
        override var running: Boolean
            get() = settings.getBoolean(CrashPreferences.RUNNING, false)
            set(value) = settings.putBoolean(CrashPreferences.RUNNING, value)
        override var count: Int
            get() = settings.getInt(CrashPreferences.COUNT, 0)
            set(value) { settings.putInt(CrashPreferences.COUNT, value) }
        override var time: Long
            get() = settings.getLong(CrashPreferences.TIME, 0)
            set(value) { settings.putLong(CrashPreferences.TIME, value) }
    }, clock)
    private var launched = false
    private var recovery = false

    fun onLaunch() {
        if (launched) return
        launched = true
        state.onLaunch()
    }
    fun onCleanExit() = state.onCleanExit()
    fun onForeground() { if (launched && !recovery) settings.putBoolean(CrashPreferences.RUNNING, true) }
    fun continueStartup() { recovery = false; settings.putBoolean(CrashPreferences.RUNNING, true) }
    fun shouldOfferRecovery(): Boolean = recovery || state.shouldOfferRecovery().also { recovery = it }
    /** Background admission never consumes the recovery latch or changes persistent crash counters. */
    fun wouldOfferRecoveryAfterLaunch(): Boolean = recovery || state.wouldOfferRecoveryAfterLaunch(launched)

    fun recordUncaught(t: Throwable) {
        runCatching {
            val file = crashFile.toPath()
            FileSystem.SYSTEM.createDirectories(file.parent!!)
            FileSystem.SYSTEM.write(file) { writeUtf8(t.stackTraceToString()) }
        }.onFailure { nsLog("Could not persist Kotlin crash: ${it.message}") }
    }

    companion object {
        val crashFile: String get() {
            val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first()
            return "$support/AndBible/log/last_crash.txt"
        }
    }
}
