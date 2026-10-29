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

import androidx.compose.ui.graphics.ImageBitmap
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.android.control.speak.SpeakSetupProblem
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.settings.TextDisplaySettingsRouteEntry
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.service.llm.agent.AgentPlatform
import net.bible.service.llm.agent.ToolPermissionAnswer
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener
import net.bible.sharedcore.platform.speak.SpeakLanguageResult
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.platform.SentenceBreaker
import net.bible.sharedlogic.platform.resolveNightMode
import net.bible.sharedui.app.ai.BugReportPlatform
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.app.ports.SystemScreens
import net.bible.sharedui.app.ports.TextDisplayEntry
import net.bible.sharedui.app.ports.TextDisplayPlatform
import okio.Sink
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import net.bible.sharedcore.log.recentLogSnapshot
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.desktop.misc.DesktopBugReport as ConcreteBugReport
import net.bible.sharedui.desktop.misc.DesktopBugReportPlatform
import net.bible.sharedui.desktop.misc.DesktopCrashTracker
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

// OWNER: plan D3d (desktop misc: night mode, notification permission, system screens, debug actions, bug report, text
// display platform, reading plans, agent platform, bookmark CSV files; the speak/SentenceBreaker no-ops stay no-ops
// because TTS is dropped on desktop). D3d replaces the bindings in this file only. Everything below is a D2b stub: it
// logs its first use once and answers neutrally, except where noted.

private const val TAG = "DesktopMiscBindings"
private val reported = ConcurrentHashMap.newKeySet<String>()

private fun pending(member: String) {
    if (reported.add(member)) Log.w(TAG, "desktop misc stub (D3d pending): $member")
}

/**
 * A minimal REAL night-mode answer (D2b's live checks need it): the stored mode of `night_mode_pref3` (default
 * `"system"`) and the manual flag `night_mode_pref`, resolved by the common [resolveNightMode]. The desktop cannot
 * tell the system appearance yet, so `"system"` and `"automatic"` mean light. [observeChanges] fires when either key
 * changes value (any writer in the process, on the writing thread). D3d replaces this with the real store.
 */
class DesktopNightModeStub(private val deviceSettings: DesktopDeviceSettings) : DesktopNightModeSource {
    val mode: String get() = deviceSettings.getString(NIGHT_MODE_MODE_KEY, DEFAULT_MODE) ?: DEFAULT_MODE
    override val nightMode: Boolean
        get() = resolveNightMode(mode, deviceSettings.getBoolean(NIGHT_MODE_KEY, false), systemDark = false, autoReading = null)
    override val manual: Boolean get() = mode == "manual"

    override fun observeChanges(onChange: () -> Unit): () -> Unit {
        val lock = Any()
        var previous = snapshot()
        return deviceSettings.observeChanges {
            val changed = synchronized(lock) { snapshot().let { now -> (now != previous).also { previous = now } } }
            if (changed) onChange()
        }
    }

    private fun snapshot(): List<String?> = listOf(deviceSettings.getString(NIGHT_MODE_MODE_KEY), deviceSettings.getString(NIGHT_MODE_KEY))

    companion object {
        const val NIGHT_MODE_KEY = "night_mode_pref"
        const val NIGHT_MODE_MODE_KEY = "night_mode_pref3"
        const val DEFAULT_MODE = "system"
    }
}

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
        reportBug = { KoinPlatform.getKoinOrNull()?.get<DesktopBugReport>()?.report(null) },
    )
}

class DesktopNotificationPermissionStub : NotificationPermission {
    override suspend fun request() = Unit
}

class DesktopSystemScreensStub : SystemScreens {
    override fun openAppLinkSettings() = pending("SystemScreens.openAppLinkSettings")
}

class DesktopDebugActionsStub : DebugActions {
    override fun crashAppDelayed() = pending("DebugActions.crashAppDelayed")
    override fun showTestError(message: String) = pending("DebugActions.showTestError")
}

class DesktopReadingPlanSourceStub : ReadingPlanSource {
    override fun builtInPlanCodes(): List<String> = emptyList()
    override fun openBuiltInPlan(code: String): String? = null
}

/** No agent UI: tool permissions are denied, questions answered "no". */
class DesktopAgentPlatformStub : AgentPlatform {
    private val source = EventSource<Boolean>()
    override val hasForegroundUi: Boolean get() = true
    override val foregroundChanges: Events<Boolean> get() = source
    override suspend fun awaitForegroundUi(): Boolean = true
    override suspend fun askToolPermission(toolDisplayName: String, toolDescription: String, actionDescription: String?): ToolPermissionAnswer {
        pending("AgentPlatform.askToolPermission"); return ToolPermissionAnswer.DENY
    }
    override suspend fun askQuestion(title: String?, message: String): Boolean { pending("AgentPlatform.askQuestion"); return false }
    override fun showShortMessage(message: String) = pending("AgentPlatform.showShortMessage")
}

/** The text display screen's entry and hide-labels result (as on every platform); no module thumbnails yet. */
class DesktopTextDisplayPlatformStub(private val sharedService: () -> TextDisplaySettingsServiceImpl) : TextDisplayPlatform {
    override fun entryFor(args: TextDisplaySettingsArgs): TextDisplayEntry {
        val entry = TextDisplaySettingsRouteEntry(args, sharedService())
        return TextDisplayEntry(entry.service, entry.scope, { scope: SettingsScope ->
            ManageLabelsMapper.hideLabelsData(entry.service.currentHideLabelsIds(scope), scope is SettingsScope.Window).toJSON()
        }, entry::resultOnLeave)
    }

    override fun applyHideLabelsResult(resultJson: String, controller: TextDisplaySettingsController) {
        val data = ManageLabelsContract.ManageLabelsData.fromJSON(resultJson)
        if (data.reset) controller.onRevert(TextSettingType.BOOKMARKS_HIDELABELS.name)
        else controller.onHideLabelsChange(data.selectedLabels.map { it.toString() })
    }

    override fun thumbnail(token: String): ImageBitmap? = null
}

class DesktopBookmarkCsvFilesStub : DesktopBookmarkCsvFiles {
    override fun openSink(ref: DocumentRef): Sink? { pending("BookmarkCsvFiles.openSink"); return null }
    override fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
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
    single<DesktopNightModeSource> { DesktopNightModeStub(get<DesktopDeviceSettings>()) }
    single<NightModeState> { get<DesktopNightModeSource>() }
    single<NotificationPermission> { DesktopNotificationPermissionStub() }
    single<SystemScreens> { DesktopSystemScreensStub() }
    single<DebugActions> { DesktopDebugActionsStub() }
    single {
        val table = get<IosStringTableHolder>()
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
            crashFile = Path.of(dirs.internalFilesDir.toString()).resolve("logs/last_crash.txt"),
        )
    }
    single<BugReportPlatform> { DesktopBugReportPlatform(get()) }
    single<DesktopBugReport> { get<ConcreteBugReport>() }
    single<ReadingPlanSource> { DesktopReadingPlanSourceStub() }
    single<AgentPlatform> { DesktopAgentPlatformStub() }
    single<TextDisplayPlatform> { DesktopTextDisplayPlatformStub { get<TextDisplaySettingsServiceImpl>() } }
    single<DesktopBookmarkCsvFiles> { DesktopBookmarkCsvFilesStub() }
    single<SpeakPlatform> { DesktopNoSpeakPlatform() }
    single<SpeakSettingsStore> { DesktopNoSpeakSettingsStore() }
    single<SentenceBreaker> { DesktopNoSentenceBreaker }
}
