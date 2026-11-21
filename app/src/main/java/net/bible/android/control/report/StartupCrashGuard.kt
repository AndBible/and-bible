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
package net.bible.android.control.report

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import net.bible.android.activity.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #4176: the crash records of the early startup, kept in plain files (`filesDir/crash/`) so that they can be written
 * and read before anything else of the app exists (no Koin, no databases, no settings store).
 *
 * - [pendingFile]: the stack traces (and unfinished starts) not yet reported, newest last.
 * - the state file: the crash count, the time of the first counted crash and whether one of them happened while the
 *   app was starting.
 * - the breadcrumb: the startup phase in progress. One left over at the next start means the last run died in that
 *   phase without the uncaught-exception handler seeing it (a native crash, the system killing it, a hang).
 * - the disabled providers: content providers [StartupCrashGuard] switched off because they crashed the start.
 */
class StartupCrashStore(val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    val pendingFile: File get() = File(dir, "pending.txt")
    private val stateFile: File get() = File(dir, "state.txt")
    private val breadcrumbFile: File get() = File(dir, "breadcrumb.txt")
    private val disabledProvidersFile: File get() = File(dir, "disabled-providers.txt")

    data class State(val count: Int = 0, val firstTime: Long = 0L, val startupCrash: Boolean = false)

    fun readState(): State = try {
        val values = stateFile.takeIf { it.exists() }?.readLines()
            ?.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            ?.toMap().orEmpty()
        State(
            count = values["count"]?.toIntOrNull() ?: 0,
            firstTime = values["first"]?.toLongOrNull() ?: 0L,
            startupCrash = values["startup"] == "1",
        )
    } catch (e: Exception) {
        State()
    }

    private fun writeState(state: State) {
        dir.mkdirs()
        stateFile.writeText("count=${state.count}\nfirst=${state.firstTime}\nstartup=${if (state.startupCrash) 1 else 0}\n")
    }

    /** The startup phase in progress, or null. */
    val phase: String? get() = try {
        breadcrumbFile.takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    fun markPhase(name: String) {
        dir.mkdirs()
        breadcrumbFile.writeText(name)
    }

    fun clearPhase() {
        breadcrumbFile.delete()
    }

    /** Counts a crash; [startup] marks it as one of the start (a startup phase was in progress). */
    private fun count(startup: Boolean) {
        val state = readState()
        writeState(
            State(
                count = state.count + 1,
                firstTime = if (state.count == 0 || state.firstTime == 0L) clock() else state.firstTime,
                startupCrash = state.startupCrash || startup,
            )
        )
    }

    private fun append(header: String, body: String) {
        dir.mkdirs()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH).format(Date(clock()))
        val old = pendingFile.takeIf { it.exists() }?.readText().orEmpty()
        val entry = "=== $header at $time ===\n$body\n"
        // Keep the newest entries: a crash loop must not fill the disk.
        pendingFile.writeText((old + entry).takeLast(MAX_PENDING_CHARS))
    }

    /**
     * The uncaught-exception handler's record of [e]. [failedPhase] is the phase [e] was thrown from when that phase
     * has already ended (its breadcrumb is removed while the exception propagates).
     */
    fun recordCrash(threadName: String, e: Throwable, details: String, failedPhase: String? = null) {
        val currentPhase = phase ?: failedPhase
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        append(
            "Crash" + (currentPhase?.let { " during startup phase '$it'" } ?: ""),
            "$details\nThread: $threadName\n$trace",
        )
        count(startup = currentPhase != null)
    }

    /** A breadcrumb was left over: the last run died in [phase] without a recorded exception. */
    fun recordUnfinishedStart(phase: String, details: String) {
        append(
            "Unfinished start",
            "$details\nThe previous start did not finish startup phase '$phase' and left no stack trace: a native " +
                "crash, the system stopped the app (for example out of memory) or it hung and was closed.",
        )
        count(startup = true)
    }

    /**
     * Run once at the very start of a process, before the first [markPhase]. A leftover breadcrumb becomes a pending
     * record; counted crashes older than an hour that did not happen at startup are forgotten. True when the user should
     * be offered a crash report before anything else starts: a crash during startup, or two crashes within an hour.
     */
    fun onProcessStart(details: () -> String): Boolean {
        phase?.let { leftover ->
            recordUnfinishedStart(leftover, details())
            clearPhase()
        }
        val state = readState()
        if (!state.startupCrash && state.firstTime != 0L && clock() - state.firstTime > ONE_HOUR) {
            stateFile.delete()
            pendingFile.delete()
            return false
        }
        return needsReport()
    }

    fun needsReport(): Boolean = readState().let { it.startupCrash || it.count >= 2 }

    fun disabledProviders(): List<String> = try {
        disabledProvidersFile.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    fun addDisabledProvider(className: String) {
        dir.mkdirs()
        if (className !in disabledProviders()) disabledProvidersFile.appendText("$className\n")
    }

    /** The user acted on the report (sent it, tried again, opened Backup & Restore): forget the crashes. */
    fun resolve() {
        stateFile.delete()
        pendingFile.delete()
        breadcrumbFile.delete()
        disabledProvidersFile.delete()
    }

    companion object {
        const val DIR_NAME = "crash"
        private const val MAX_PENDING_CHARS = 200_000
    }
}

/**
 * #4176: the crash handler of the early startup and the crash safe mode.
 *
 * [install] runs from `BibleApplication.attachBaseContext`, which the framework calls before it installs the app's
 * content providers (`androidx.startup`'s `InitializationProvider`, WorkManager, ...) and before
 * `Application.onCreate`. The handler only writes files ([StartupCrashStore]) and then hands the crash to the
 * previous handler, so a crash anywhere in the start is recorded.
 *
 * When the last runs crashed during startup ([StartupCrashStore.onProcessStart]), the process starts in safe mode:
 * `BibleApplication.onCreate` skips its initialisation (Koin, Sword, databases, sync) and every activity hands over to
 * [net.bible.android.view.activity.base.CrashSafeModeActivity], a native dialog that offers the report. A content
 * provider that crashed the start is switched off for the next start, or the process would die before any activity
 * could show; [resolve] switches it back on.
 *
 * The discrete flavour (and the calculator disguise) never starts in safe mode: nothing of the app may show before the
 * PIN. There the report is offered by [ErrorReportControl.checkCrash] after the calculator.
 */
object StartupCrashGuard {
    const val PHASE_APPLICATION = "application"
    const val PHASE_INITIALIZE_APP = "initialize-app"
    const val PHASE_DATABASES = "startup-databases"

    /** True for the whole life of a process that started in safe mode. */
    @Volatile var safeMode: Boolean = false
        private set

    @Volatile var store: StartupCrashStore? = null
        private set

    @Volatile private var appContext: Context? = null

    /** The last [phase] that ended with an exception, and that exception. */
    @Volatile private var failedPhase: Pair<String, Throwable>? = null

    /** Whether safe mode may be used at all (false in the discrete flavour and with the calculator disguise). */
    @Volatile private var safeModeAllowed: Boolean = true

    /** The device-default preferences, read without the app's settings store. */
    private fun defaultPreferences(context: Context) =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    private fun calculatorDisguise(context: Context): Boolean = try {
        net.bible.service.common.BuildVariant.Appearance.isDiscrete ||
            defaultPreferences(context).let { it.getBoolean("show_calculator", false) || it.getBoolean("discrete_mode", false) }
    } catch (e: Exception) {
        false
    }

    /**
     * The process start: checks the records ([StartupCrashStore.onProcessStart]), decides safe mode, marks the
     * [PHASE_APPLICATION] breadcrumb and (unless [installHandler] is false, as in unit tests) installs the handler.
     * Never throws: a broken crash guard must not break the start.
     */
    fun install(context: Context, installHandler: Boolean = true) {
        try {
            start(context, StartupCrashStore(File(context.filesDir, StartupCrashStore.DIR_NAME)), installHandler)
        } catch (e: Throwable) {
            Log.e(TAG, "Startup crash guard could not start", e)
        }
    }

    internal fun start(context: Context, store: StartupCrashStore, installHandler: Boolean): Boolean {
        this.store = store
        appContext = context
        safeModeAllowed = !calculatorDisguise(context)
        val needsReport = store.onProcessStart { reportDetails(context) }
        safeMode = needsReport && safeModeAllowed
        if (!safeMode) store.markPhase(PHASE_APPLICATION)
        if (installHandler) installHandler()
        Log.i(TAG, "Startup crash guard: needsReport=$needsReport safeMode=$safeMode")
        return safeMode
    }

    private fun installHandler() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(current))
    }

    /** The handler; also used directly by tests. Records [e], then always hands it on to [previous]. */
    internal class Handler(private val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                record(t, e)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    internal fun record(t: Thread, e: Throwable) {
        val store = store ?: return
        val context = appContext
        store.recordCrash(t.name, e, context?.let { reportDetails(it) } ?: "", failedPhaseOf(e))
        if (context != null) failedProviderClass(e)?.let { disableProvider(context, store, it) }
    }

    /** Marks the start of a risky startup phase (the breadcrumb); [clearPhase] ends it. */
    fun markPhase(name: String) {
        try { store?.markPhase(name) } catch (_: Exception) {}
    }

    fun clearPhase() {
        try { store?.clearPhase() } catch (_: Exception) {}
    }

    /**
     * Runs [block] as startup phase [name]. An exception ends the phase too (a caller may handle it), but is
     * remembered: if it reaches the uncaught-exception handler, it is still a crash of this phase.
     */
    inline fun <T> phase(name: String, block: () -> T): T {
        markPhase(name)
        try {
            return block()
        } catch (t: Throwable) {
            noteFailedPhase(name, t)
            throw t
        } finally {
            clearPhase()
        }
    }

    @PublishedApi internal fun noteFailedPhase(name: String, t: Throwable) {
        failedPhase = name to t
    }

    /** The phase [e] (or an exception it wraps, or a copy of one) was thrown from, if it ended with it. */
    private fun failedPhaseOf(e: Throwable): String? {
        val (name, thrown) = failedPhase ?: return null
        val matches = generateSequence(e) { it.cause }.take(20).any {
            it === thrown || (it.javaClass == thrown.javaClass && it.message == thrown.message)
        }
        return if (matches) name else null
    }

    /** Whether there are crash records the user has not acted on yet. */
    fun needsReport(): Boolean = try { store?.needsReport() == true } catch (_: Exception) { false }

    /** The report text: the build and device, then every pending record. */
    fun reportText(context: Context): String {
        val pending = try { store?.pendingFile?.takeIf { it.exists() }?.readText() } catch (_: Exception) { null }
        return reportDetails(context) + "\n\n" + (pending ?: "No crash records.")
    }

    /**
     * The user acted on the report: forget the crash records (also the classic counters of
     * [ErrorReportControl.checkCrash]) and switch the providers that crashed the start back on.
     */
    fun resolve(context: Context) {
        val store = store ?: StartupCrashStore(File(context.filesDir, StartupCrashStore.DIR_NAME))
        store.disabledProviders().forEach { className ->
            try {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Could not enable $className again", e)
            }
        }
        store.resolve()
        defaultPreferences(context).edit().putInt("app-crashed-count", 0).putLong("app-crashed-time", 0L).commit()
    }

    /** Switches off the content provider that crashed the start, so that the next start can show the report. */
    private fun disableProvider(context: Context, store: StartupCrashStore, className: String) {
        // The report's attachment goes through the FileProvider: never switch it off.
        if (className == androidx.core.content.FileProvider::class.java.name) return
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, className),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            store.addDisabledProvider(className)
        } catch (e: Exception) {
            Log.e(TAG, "Could not disable provider $className", e)
        }
    }

    /** The build and the device: version name and code and R8's map id, so the trace can be retraced directly. */
    internal fun reportDetails(context: Context): String = buildString {
        append("App id: ").append(context.packageName).append('\n')
        append("Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        append("R8 map id: ").append(r8MapId() ?: "none (not obfuscated)").append('\n')
        append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append(")")
    }

    internal fun resetForTest() {
        safeMode = false
        store = null
        appContext = null
        safeModeAllowed = true
        failedPhase = null
    }

    private const val TAG = "StartupCrashGuard"
}

/**
 * R8 replaces the source file name of every class with `r8-map-id-<id>` (docs/r8-obfuscation.md); the id names the
 * build's `mapping.txt`. Null in a build that is not obfuscated.
 */
internal fun r8MapId(): String? =
    Throwable().stackTrace.firstOrNull()?.fileName?.takeIf { it.startsWith("r8-map-id-") }?.removePrefix("r8-map-id-")

/**
 * The provider whose install crashed the start (the framework's "Unable to get provider <class>: ..."), or null.
 */
internal fun failedProviderClass(e: Throwable): String? {
    val pattern = Regex("Unable to get provider ([\\w.$]+)")
    return generateSequence(e) { it.cause }.take(20)
        .firstNotNullOfOrNull { pattern.find(it.message ?: "")?.groupValues?.get(1) }
}
