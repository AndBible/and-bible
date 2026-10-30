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
package net.bible.sharedui.desktop.assembly

import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.android.control.speak.SpeakSetupProblem
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.service.llm.agent.AgentPlatform
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener
import net.bible.sharedcore.platform.speak.SpeakLanguageResult
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.platform.SentenceBreaker
import net.bible.sharedui.app.ai.BugReportPlatform
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.app.ports.SystemScreens
import net.bible.sharedui.app.ports.TextDisplayPlatform
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import net.bible.sharedcore.log.recentLogSnapshot
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.desktop.misc.DesktopBugReport as ConcreteBugReport
import net.bible.sharedui.desktop.misc.DesktopBugReportPlatform
import net.bible.sharedui.desktop.misc.DesktopCrashTracker
import java.nio.file.Path

// OWNER: plan D3d (desktop misc: night mode, notification permission, system screens, debug actions, bug report, text
// display platform, reading plans, agent platform, bookmark CSV files; the speak/SentenceBreaker no-ops stay no-ops
// because TTS is dropped on desktop).

private const val TAG = "DesktopMiscBindings"

/** Installs crash capture before the database or dependency graph exists. */
fun desktopCrashGate(paths: DesktopPaths): DesktopCrashGate {
    val tracker = DesktopCrashTracker(DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)), paths.data.resolve("logs"))
    tracker.install()
    tracker.onLaunch()
    if (System.getProperty("andbible.debug.startupCrash") == "true" && !tracker.shouldOfferRecovery()) {
        throw IllegalStateException("Deliberate desktop startup crash")
    }
    return DesktopCrashGate(
        recoveryRequired = tracker::shouldOfferRecovery,
        reportBug = {
            net.bible.sharedui.desktop.misc.recoveryBugReport(
                paths.data, System.getProperty("andbible.version")?.takeIf { it.isNotBlank() } ?: "dev",
                ::recentLogSnapshot, net.bible.sharedui.assembly.hostStringTable(DesktopUiLanguage.resolve(DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)))).text("report_bug_line_1"),
            ).report(null)
        },
        onStartupReady = tracker::continueStartup,
        onCleanExit = tracker::onCleanExit,
    )
}

class DesktopNightModeAdapter(private val state: net.bible.sharedlogic.platform.desktop.misc.DesktopNightModeState) : DesktopNightModeSource {
    override val nightMode: Boolean get() = state.nightMode
    override val manual: Boolean get() = state.manual
    override fun observeChanges(onChange: () -> Unit): () -> Unit = state.observeChanges(onChange)
}

// --- speak: text to speech is dropped on desktop; these bindings exist because common code resolves them.

/** No speech engine: every call is ignored, language support is reported as missing. */
private object NoSpeakEngine : SpeakEngine {
    override fun speakText(text: String, utteranceId: String) = Unit
    override fun playEarcon(earcon: String, volume: Float, utteranceId: String) = Unit
    override fun playSilence(durationMs: Long, utteranceId: String) = Unit
    override fun setLanguage(languageTag: String) = SpeakLanguageResult.NOT_SUPPORTED
    override fun setSpeechRate(rate: Float) = Unit
    override fun flush() = Unit
    override fun stop() = Unit
    override fun shutdown() = Unit
    override val maxSpeechInputLength: Int get() = 1000
}

class DesktopNoSpeakPlatform : SpeakPlatform {
    override val maxSpeechInputLength: Int get() = 1000
    override val preferredEnginePackage: String? get() = null
    override fun createEngine(listener: SpeakEngineListener): SpeakEngine = NoSpeakEngine.also { listener.onInit(false) }
    override fun onPrepareForSpeaking() = Unit
    override fun attachMediaControls(control: SpeakControl) = Unit
    override fun detachMediaControls() = Unit
    override fun bringReadingToFront() = Unit
    override fun phrasesFor(languageCode: String) = SpeakPhrases("", "", "", "", "", "", "")
    override fun reportProblem(problem: SpeakSetupProblem) = Unit
    override fun requestAudioFocus(onFocusChange: (gained: Boolean) -> Unit): Boolean = false
    override fun abandonAudioFocus() = Unit
    override fun startPhoneCallMonitoring(onCallStateChanged: (active: Boolean) -> Unit) = Unit
}

class DesktopNoSpeakSettingsStore : SpeakSettingsStore {
    override fun load(): String? = ""
    override fun save(json: String) = Unit
}

/** Sentence boundaries are only used by speech; the whole text is one sentence. */
object DesktopNoSentenceBreaker : SentenceBreaker {
    override fun preceding(text: String, offset: Int): Int = 0
    override fun following(text: String, offset: Int): Int = text.length
}

fun desktopMiscModule(): Module = module {
    single<DesktopNightModeSource> { DesktopNightModeAdapter(net.bible.sharedlogic.platform.desktop.misc.DesktopNightModeState(get())) }
    single<NightModeState> { get<DesktopNightModeSource>() }
    single<NotificationPermission> { net.bible.sharedui.desktop.misc.DesktopNotificationPermission() }
    single<SystemScreens> { net.bible.sharedui.desktop.misc.DesktopSystemScreens() }
    single<DebugActions> { net.bible.sharedui.desktop.misc.DesktopDebugActions(get()) }
    single {
        val table = get<HostStringTableHolder>()
        val dialogs = get<AppDialogController>()
        val delivery = get<FileDelivery>()
        val links = get<ExternalLinks>()
        val dirs = get<AppDirectories>()
        ConcreteBugReport(
            directory = Path.of(get<DesktopAppEnvironment>().cacheDir).resolve("reports"),
            version = get<DesktopAppEnvironment>().versionName,
            log = ::recentLogSnapshot,
            deliver = { path, name, subject -> delivery.deliver(path.toString(), name, SaveOrShare.SAVE,
                "application/gzip", "application/gzip", subject, null, table.current.text("bug_report_email_title")) },
            open = links::open,
            confirm = { ai ->
                val strings = table.current
                dialogs.await(AppDialogRequest.Confirm(
                    strings.text(if (ai) "send_ai_bug_report_title" else "bug_report_email_title"),
                    strings.text("bug_report_email_text"), strings.text("okay"), strings.text("cancel"),
                )) == AppDialogResult.Ok
            },
            failure = { error ->
                Log.w(TAG, "Report preparation failed", error)
                val strings = table.current
                dialogs.await(AppDialogRequest.Message(strings.text("error"), strings.text("bug_report_prepare_failed"), strings.text("okay")))
            },
            introduction = { table.current.text("report_bug_line_1") },
            crashFile = Path.of(dirs.internalFilesDir.toString()).resolve("logs").resolve("last_crash.txt"),
        )
    }
    single<BugReportPlatform> { DesktopBugReportPlatform(get()) }
    single<DesktopBugReport> { get<ConcreteBugReport>() }
    single<ReadingPlanSource> { net.bible.sharedcore.platform.desktop.misc.DesktopReadingPlanSource() }
    single {
        val table = get<HostStringTableHolder>()
        val window = get<DesktopWindowHandle>()
        net.bible.sharedlogic.platform.desktop.misc.DesktopAgentPlatform(
            get(), get(), get(), { table.current.text(it) }, get(), { window.isFocusedAndVisible },
        )
    } onClose { it?.close() }
    single<AgentPlatform> { get<net.bible.sharedlogic.platform.desktop.misc.DesktopAgentPlatform>() }
    single<TextDisplayPlatform> {
        val window = get<DesktopWindowHandle>()
        net.bible.sharedui.desktop.misc.DesktopTextDisplayPlatform(
            sharedService = { get<TextDisplaySettingsServiceImpl>() },
            windowPixels = { (window.sizeDp.width * window.density).toInt() to (window.sizeDp.height * window.density).toInt() },
        )
    }
    single<DesktopBookmarkCsvFiles> { net.bible.sharedui.desktop.misc.DesktopBookmarkCsvFiles() }
    single<SpeakPlatform> { DesktopNoSpeakPlatform() }
    single<SpeakSettingsStore> { DesktopNoSpeakSettingsStore() }
    single<SentenceBreaker> { DesktopNoSentenceBreaker }
}
