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
import android.content.ContentProvider
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
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
 */
class StartupCrashStore(val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    val pendingFile: File get() = File(dir, "pending.txt")
    private val stateFile: File get() = File(dir, "state.txt")
    private val breadcrumbFile: File get() = File(dir, "breadcrumb.txt")

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
    @Synchronized
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

    @Synchronized
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
    @Synchronized
    fun recordCrash(threadName: String, e: Throwable, details: String, failedPhase: String? = null) {
        val currentPhase = phase ?: failedPhase
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        append(
            "Crash" + (currentPhase?.let { " during startup phase '$it'" } ?: ""),
            "$details\nThread: $threadName\n$trace",
        )
        count(startup = currentPhase != null)
        // This death is recorded: the next start must not count it again as an unfinished start.
        clearPhase()
    }

    /**
     * How the last process ended, as far as Android tells ([description] names the exit reason):
     * - [Kind.NOT_A_CRASH]: the user or the system closed it (swiped away, low memory, update);
     * - [Kind.CRASH]: a crash Android saw (native crash, ANR, a Java crash the handler missed);
     * - [Kind.UNCLEAR]: anything else, or nothing known (Android before 11): it might have been a crash.
     */
    data class LastExit(val kind: Kind, val description: String) {
        enum class Kind { NOT_A_CRASH, CRASH, UNCLEAR }
    }

    /**
     * A breadcrumb was left over: the last run died in [phase] without a recorded exception. It is a startup crash
     * (the safe mode at once) only when Android says it crashed; otherwise it counts like one ordinary crash, so the
     * safe mode needs a second one within the hour (one low-memory kill during a long migration is not a crash loop).
     */
    @Synchronized
    fun recordUnfinishedStart(phase: String, details: String, exit: LastExit) {
        append(
            "Unfinished start",
            "$details\nThe previous start did not finish startup phase '$phase' and left no stack trace " +
                "(exit reason: ${exit.description}): a native crash, the system stopped the app (for example out of " +
                "memory) or it hung and was closed.",
        )
        count(startup = exit.kind == LastExit.Kind.CRASH)
    }

    /**
     * Run once at the very start of a process, before the first [markPhase]. A leftover breadcrumb becomes a pending
     * record; counted crashes older than an hour that did not happen at startup are forgotten. True when the user should
     * be offered a crash report before anything else starts: a crash during startup, or two crashes within an hour.
     */
    fun onProcessStart(
        details: () -> String,
        lastExit: (since: Long) -> LastExit = { LastExit(LastExit.Kind.UNCLEAR, "unknown") },
    ): Boolean {
        phase?.let { leftover ->
            // The death came after the breadcrumb was written: an exit record from before it is another run's.
            val exit = lastExit(try { breadcrumbFile.lastModified() } catch (_: Exception) { 0L })
            // Closed by the user or the system (swiped away, force-stopped, killed for memory in the background,
            // updated): not a crash.
            if (exit.kind != LastExit.Kind.NOT_A_CRASH) recordUnfinishedStart(leftover, details(), exit)
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

    /** The time of the first crash not yet acted on, or null. */
    fun firstCrashTime(): Long? = readState().firstTime.takeIf { it != 0L }

    /** The user acted on the report (sent it, tried again, opened Backup & Restore): forget the crashes. */
    fun resolve() {
        stateFile.delete()
        pendingFile.delete()
        breadcrumbFile.delete()
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
 * could show. Every start that is not in safe mode switches all of the app's providers back on (also after the user
 * cleared the app's storage, which forgets the records but not the component settings) and installs them itself
 * ([installReenabledProviders]): the framework listed this process's providers before.
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

    /** The providers [start] switched back on, not yet installed in this process ([installReenabledProviders]). */
    @Volatile private var reenabledProviders: List<ProviderInfo> = emptyList()

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
        val needsReport = store.onProcessStart({ reportDetails(context) }, { since -> lastExit(context, since) })
        safeMode = needsReport && safeModeAllowed
        if (!safeMode) {
            store.markPhase(PHASE_APPLICATION)
            reenabledProviders = enableDisabledProviders(context)
        }
        if (installHandler) installHandler()
        Log.i(TAG, "Startup crash guard: needsReport=$needsReport safeMode=$safeMode")
        return safeMode
    }

    /**
     * How the previous process of the app ended, where Android tells (API 30+). Older Android, no record, or a newest
     * record from before [since] (the run that died left none: power loss, a reboot) is
     * [StartupCrashStore.LastExit.Kind.UNCLEAR].
     */
    private fun lastExit(context: Context, since: Long): StartupCrashStore.LastExit {
        val unclear = StartupCrashStore.LastExit(StartupCrashStore.LastExit.Kind.UNCLEAR, "unknown")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return unclear.copy(description = "unknown (Android ${Build.VERSION.RELEASE})")
        return try {
            val am = context.getSystemService(android.app.ActivityManager::class.java) ?: return unclear
            val info = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return unclear
            val described = exitReasonName(info.reason) + (info.description?.let { " ($it)" } ?: "")
            if (info.timestamp < since - EXIT_RECORD_MARGIN_MS) return unclear.copy(description = "unknown (newest record, $described, is older)")
            StartupCrashStore.LastExit(exitKind(info.reason), described)
        } catch (e: Exception) {
            unclear
        }
    }

    /** [android.app.ApplicationExitInfo]'s reason as a [StartupCrashStore.LastExit.Kind]. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    internal fun exitKind(reason: Int): StartupCrashStore.LastExit.Kind = when (reason) {
        android.app.ApplicationExitInfo.REASON_USER_REQUESTED,
        android.app.ApplicationExitInfo.REASON_USER_STOPPED,
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
        android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        android.app.ApplicationExitInfo.REASON_EXIT_SELF -> StartupCrashStore.LastExit.Kind.NOT_A_CRASH
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
        android.app.ApplicationExitInfo.REASON_ANR,
        android.app.ApplicationExitInfo.REASON_CRASH,
        android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> StartupCrashStore.LastExit.Kind.CRASH
        else -> StartupCrashStore.LastExit.Kind.UNCLEAR
    }

    private fun exitReasonName(reason: Int): String = android.app.ApplicationExitInfo::class.java.fields
        .firstOrNull { it.name.startsWith("REASON_") && it.type == Int::class.javaPrimitiveType && it.getInt(null) == reason }
        ?.name?.removePrefix("REASON_") ?: reason.toString()

    /**
     * True in a safe-mode process that runs without a provider the safe mode switched off (androidx.startup's
     * `InitializationProvider`: no WorkManager, so Glance cannot draw a widget). Widget broadcasts are then ignored
     * instead of crashing the safe-mode screen's process.
     */
    fun safeModeWithoutProviders(context: Context): Boolean = safeMode && try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_DISABLED_COMPONENTS)
            .providers.orEmpty()
            .any { pm.getComponentEnabledSetting(ComponentName(context.packageName, it.name)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED }
    } catch (e: Exception) {
        true
    }

    /**
     * Switches every content provider of the app that is switched off ([PackageManager.COMPONENT_ENABLED_STATE_DISABLED])
     * back to its manifest default; returns those. Providers the manifest itself leaves off are not touched.
     */
    private fun enableDisabledProviders(context: Context): List<ProviderInfo> = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION") // GET_DISABLED_COMPONENTS has MATCH_DISABLED_COMPONENTS's value and works on API 23.
        val providers = pm.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_DISABLED_COMPONENTS)
            .providers.orEmpty()
        providers.filter { info ->
            val component = ComponentName(context.packageName, info.name)
            try {
                if (pm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return@filter false
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
                Log.w(TAG, "Switched ${info.name} on again")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Could not enable ${info.name} again", e)
                false
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not check the app's providers", e)
        emptyList()
    }

    /**
     * Installs in this process the providers [start] switched back on (from `Application.onCreate`, where the framework
     * has installed the others): this process was started without them, and androidx.startup's
     * `InitializationProvider` (WorkManager for the widgets, emoji2, ...) must run before the app uses them. A provider
     * that still crashes fails like the framework's own install ("Unable to get provider ..."), so the crash is recorded
     * and the provider is switched off again for the safe mode.
     *
     * The instance is not published: a later lookup of its authority would make the system install a second one.
     * Nothing looks these up, and `InitializationProvider` skips the initialisers it already ran.
     */
    fun installReenabledProviders(context: Context) {
        val providers = reenabledProviders
        reenabledProviders = emptyList()
        providers.forEach { info ->
            try {
                val provider = Class.forName(info.name, true, context.classLoader).getDeclaredConstructor().newInstance() as ContentProvider
                provider.attachInfo(context, info)
            } catch (e: Throwable) {
                throw RuntimeException("Unable to get provider ${info.name}: $e", e)
            }
        }
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
        // Only where the next start shows the safe mode: elsewhere the app would run without the provider.
        if (context != null && safeModeAllowed) failedProviderClass(e)?.let { disableProvider(context, it) }
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

    /** The time of the first crash the records hold, or null. */
    fun firstCrashTime(): Long? = try { store?.firstCrashTime() } catch (_: Exception) { null }

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
        enableDisabledProviders(context)
        store.resolve()
        defaultPreferences(context).edit().putInt("app-crashed-count", 0).putLong("app-crashed-time", 0L).commit()
    }

    /** Switches off the content provider that crashed the start, so that the next start can show the report. */
    private fun disableProvider(context: Context, className: String) {
        // The report's attachment goes through the FileProvider: never switch it off.
        if (className == androidx.core.content.FileProvider::class.java.name) return
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, className),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
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
        reenabledProviders = emptyList()
    }

    private const val TAG = "StartupCrashGuard"
    private const val EXIT_RECORD_MARGIN_MS = 5_000L
}

/**
 * R8 replaces the source file name of every class with `r8-map-id-<id>` (docs/r8-obfuscation.md); the id names the
 * build's `mapping.txt`. Null in a build that is not obfuscated.
 */
internal fun r8MapId(): String? =
    Throwable().stackTrace.firstOrNull()?.fileName?.takeIf { it.startsWith("r8-map-id-") }?.removePrefix("r8-map-id-")

/**
 * The provider whose install crashed the start, or null: the framework's "Unable to get provider <class>: ..." for an
 * exception, or, for an [Error] the framework does not wrap, the class whose method `ContentProvider.attachInfo` called
 * (the provider's `onCreate`) in the stack trace.
 */
internal fun failedProviderClass(e: Throwable): String? {
    val pattern = Regex("Unable to get provider ([\\w.$]+)")
    val chain = generateSequence(e) { it.cause }.take(20).toList()
    chain.firstNotNullOfOrNull { pattern.find(it.message ?: "")?.groupValues?.get(1) }?.let { return it }
    return chain.firstNotNullOfOrNull { t ->
        val trace = t.stackTrace
        val attach = trace.indexOfFirst { it.className == ContentProvider::class.java.name && it.methodName == "attachInfo" }
        trace.getOrNull(attach - 1)?.className?.takeIf { attach > 0 && it != ContentProvider::class.java.name }
            ?: run {
                // Thrown while the framework created it (its static initialiser or constructor): the app class
                // nearest to ActivityThread.installProvider.
                val install = trace.indexOfFirst { it.className == "android.app.ActivityThread" && it.methodName == "installProvider" }
                (install - 1 downTo 0).asSequence().map { trace[it] }
                    .firstOrNull { (it.methodName == "<clinit>" || it.methodName == "<init>") && !it.isPlatformFrame() }
                    ?.className
            }
    }
}

private fun StackTraceElement.isPlatformFrame(): Boolean =
    listOf("java.", "javax.", "android.", "androidx.core.app.", "dalvik.", "kotlin.", "libcore.", "jdk.", "sun.", "com.android.")
        .any { className.startsWith(it) }
