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
package net.bible.sharedui.assembly

import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.android.control.buildSharedBindings
import net.bible.android.control.link.LinkPlatform
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.control.page.window.SettingLabels
import net.bible.android.control.page.window.WindowSettingsPicker
import net.bible.android.control.search.SearchTextHighlighter
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.android.control.speak.SpeakSetupProblem
import net.bible.service.common.GlobalTextDisplaySettingsStore
import net.bible.service.db.DatabaseContainer
import net.bible.service.format.SaxTextPolicy
import net.bible.service.history.HistoryPlatform
import net.bible.service.llm.LlmStrings
import net.bible.service.llm.agent.AgentPlatform
import net.bible.service.sword.ContentSource
import net.bible.service.sword.HtmlToPlainText
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.IosHttpClients
import net.bible.sharedcore.http.KtorDownloadHttp
import net.bible.sharedcore.llm.http.KtorLlmHttpTransport
import net.bible.sharedcore.llm.http.LlmHttpTransport
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.IosAppEnvironment
import net.bible.sharedcore.platform.IosDateTimeFormats
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.platform.IosReadingPlanSource
import net.bible.sharedcore.platform.MarkdownRenderer
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.search.IosSearchIndexingRunner
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.speak.IosAudioFocus
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedlogic.addons.SharedAddonCatalog
import net.bible.sharedlogic.addons.SharedAddonScanner
import net.bible.sharedlogic.addons.SharedPromptPackSource
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.platform.DistributedPlanCatalog
import net.bible.sharedlogic.platform.EpubStoreEdge
import net.bible.sharedlogic.platform.FirstLoadEdge
import net.bible.sharedlogic.platform.LabelNameStrings
import net.bible.sharedlogic.platform.LocalCalendar
import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedlogic.platform.ManualInstallFolderEnabler
import net.bible.sharedlogic.platform.MyDocumentStoreEdge
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.platform.PlatformLocales
import net.bible.sharedlogic.platform.PromptPackSource
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.SearchIndexScheduler
import net.bible.sharedlogic.platform.SentenceBreaker
import net.bible.sharedlogic.platform.ios.IosAgentPlatform
import net.bible.sharedlogic.platform.ios.IosContentSource
import net.bible.sharedlogic.platform.ios.IosEpubStoreEdge
import net.bible.sharedlogic.platform.ios.IosFirstLoadEdge
import net.bible.sharedlogic.platform.ios.IosHistoryPlatform
import net.bible.sharedlogic.platform.ios.IosLocalCalendar
import net.bible.sharedlogic.platform.ios.IosLocaleSupport
import net.bible.sharedlogic.platform.ios.IosMarkdownRenderer
import net.bible.sharedlogic.platform.ios.IosMyDocumentStoreEdge
import net.bible.sharedlogic.platform.ios.IosNightModeState
import net.bible.sharedlogic.platform.ios.IosPlatformLocales
import net.bible.sharedlogic.platform.ios.IosReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.ios.IosSearchIndexScheduler
import net.bible.sharedlogic.platform.ios.IosSentenceBreaker
import net.bible.sharedlogic.platform.ios.IosSpeakPlatform
import net.bible.sharedlogic.platform.ios.IosSpeakSettingsStore
import net.bible.sharedlogic.platform.ios.IosUserNotifier
import net.bible.sharedlogic.platform.ios.PendingKeyChooserLauncher
import net.bible.sharedlogic.platform.ios.PendingLinkPlatform
import net.bible.sharedlogic.platform.ios.PendingWindowSettingsPicker
import net.bible.sharedlogic.search.CommonSearchHighlighter
import net.bible.sharedlogic.settings.DatabaseAppSettings
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedlogic.text.CommonHtmlToPlainText
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import net.bible.sharedui.strings.keyed.KeyedDistributedPlanCatalog
import net.bible.sharedui.strings.keyed.KeyedLabelNameStrings
import net.bible.sharedui.strings.keyed.KeyedLlmStrings
import net.bible.sharedui.strings.keyed.KeyedSettingLabels
import net.bible.sharedui.strings.keyed.PendingTextDisplayPrefItems
import net.bible.sharedui.strings.keyed.keyedSpeakPhrases
import okio.Path
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.Books
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Where the app keeps its files and what it was told by its host.
 *
 * Production passes nothing: databases, Application Support and Documents are the real sandbox directories and the
 * UI language is `locale_pref`, else the first preferred iOS language the string tables have, else English (C4).
 *
 * Tests pass a temporary [root]; below it live `Documents/` (with `Documents/sword`, the module folder),
 * `AppSupport/` (the app's private files) and `databases/` ([DOCUMENTS_DIR], [SUPPORT_DIR], [DATABASES_DIR]).
 * [uiLanguage] pins the language of the string tables. [repositories] is the download-site map; null reads
 * `repositories.txt` from the app bundle (the same file as Android's `R.raw.repositories`), tests pass an empty
 * `PropertyMap`.
 */
class IosAppConfig(
    val root: Path? = null,
    val uiLanguage: String? = null,
    val repositories: org.crosswire.common.util.PropertyMap? = null,
) {
    companion object {
        const val DOCUMENTS_DIR = "Documents"
        const val SUPPORT_DIR = "AppSupport"
        const val DATABASES_DIR = "databases"
    }
}

/**
 * The iOS platform half of the Koin graph: one definition for each port the shared definitions need, except
 * Stream D's (`CloudAdapterFactory`, `SyncSettingsPlatform`, `DocumentSyncStarter`, `SyncScheduler`), which phase 1
 * leaves unbound. Builds a NEW `Module` on every call (a `Module` caches its singletons). The set of bound port types
 * is declared in [IOS_BOUND_PORT_TYPES]; `IosAssemblyTest.boundPortListMatchesTheModule` keeps both in step.
 */
fun buildIosPlatformBindings(config: IosAppConfig): Module = buildIosPlatformBindings(config, IosPlatformParts(config))

/** `IosPlatformBindings` + the shared bindings (+ [extra], e.g. test doubles): what `startKoin` loads on iOS. */
fun buildIosCoreModule(config: IosAppConfig, extra: List<Module> = emptyList()): Module =
    buildIosCoreModule(config, extra, IosPlatformParts(config))

internal fun buildIosCoreModule(config: IosAppConfig, extra: List<Module>, parts: IosPlatformParts): Module = module {
    includes(buildIosPlatformBindings(config, parts), buildSharedBindings(), *extra.toTypedArray())
}

@OptIn(ExperimentalForeignApi::class)
internal fun buildIosPlatformBindings(config: IosAppConfig, parts: IosPlatformParts): Module {
    // One scanner for both add-on ports, so a clearCaches() on the catalog also reloads the prompt packs.
    val scanner = SharedAddonScanner(books = { Books.installed().getBooks() })
    val promptPacks = SharedPromptPackSource(scanner)

    return module {
        // --- device, sandbox and settings
        single { parts.deviceSettings }
        single<SecretStore> { parts.secrets }
        single { parts.environment }
        single<AppEnvironment> { get<IosAppEnvironment>() }
        single<AppDirectories> { parts.directories }
        single<AppSettings> { DatabaseAppSettings({ DatabaseContainer.instance.settingsStore }, { IosAppStartup.databasesOpen }) }

        // --- strings (one table for the chosen UI language)
        single<CoreStrings> { KeyedCoreStrings(parts.strings) }
        single<LlmStrings> { KeyedLlmStrings(parts.strings) }
        single<LabelNameStrings> { KeyedLabelNameStrings(parts.strings) }
        single<SettingLabels> { KeyedSettingLabels(parts.strings) }
        single<DistributedPlanCatalog> { KeyedDistributedPlanCatalog(parts.strings) }
        single<TextDisplayPrefItems> { PendingTextDisplayPrefItems(strings = { parts.strings }, contentSource = { get() }) }

        // --- locale, calendar, text
        single<DateTimeFormats> { IosDateTimeFormats() }
        single<LocalCalendar> { IosLocalCalendar() }
        single<PlatformLocales> { IosPlatformLocales() }
        single<LocaleSupport> { IosLocaleSupport }
        single<SentenceBreaker> { IosSentenceBreaker }
        single<HtmlToPlainText> { CommonHtmlToPlainText }
        single<SearchTextHighlighter> { CommonSearchHighlighter }
        single<SaxTextPolicy> { SaxTextPolicy.MODERN }
        single<MarkdownRenderer> { IosMarkdownRenderer() }

        // --- content and storage
        single<ReadingPlanSource> { IosReadingPlanSource() }
        single<ContentSource> { IosContentSource() }
        single<ReadOnlyModuleDbOpener> { IosReadOnlyModuleDbOpener() }
        single<FirstLoadEdge> { IosFirstLoadEdge }
        single<EpubStoreEdge> { IosEpubStoreEdge }
        single<MyDocumentStoreEdge> { IosMyDocumentStoreEdge }
        single<PromptPackSource> { promptPacks }
        single<AddonCatalog> { SharedAddonCatalog(scanner, promptPacks) }
        single<SearchIndexScheduler> { IosSearchIndexScheduler(IosSearchIndexingRunner(), get<AppCoroutineScope>()) }
        single<ManualInstallFolderEnabler> { ManualInstallFolderEnabler { } } // no manual install folders on iOS

        // --- network
        single { IosHttpClients() }
        single<DownloadHttp> { KtorDownloadHttp(get<IosHttpClients>().downloadClient) { WebResource.getTimeout() } }
        single<LlmHttpTransport> { KtorLlmHttpTransport(get<IosHttpClients>()::clientFor) }

        // --- speak, agent, notifications
        single<SpeakSettingsStore> { IosSpeakSettingsStore(parts.deviceSettings) }
        single<SpeakPlatform> {
            IosSpeakPlatform(
                audioFocus = IosAudioFocus(),
                phrases = { tag -> keyedSpeakPhrases(iosStringTable(tag.replace('_', '-'))) },
                problemText = { problem ->
                    parts.strings.text(
                        when (problem) {
                            SpeakSetupProblem.ENGINE_NOT_AVAILABLE -> "tts_engine_not_available"
                            SpeakSetupProblem.LANGUAGE_NOT_AVAILABLE -> "tts_lang_not_available"
                            SpeakSetupProblem.AUDIO_FOCUS_DENIED -> "tts_audio_focus_denied"
                        },
                    )
                },
                notifier = get(),
                settings = get(),
                earconFiles = IosSpeakPlatform.earconFiles(parts.bundleResourcePath),
            )
        }
        single<UserNotifier> { IosUserNotifier(get<AppDialogController>(), okText = { parts.strings.text("okay") }) }
        single<AgentPlatform> { IosAgentPlatform(get()) }
        single<NightModeState> { IosNightModeState(parts.deviceSettings) }

        // --- reading UI ports (C7: pending implementations log and answer cancelled)
        single<GlobalTextDisplaySettingsSource> {
            val scope = this
            object : GlobalTextDisplaySettingsSource {
                override val current get() = scope.get<GlobalTextDisplaySettingsStore>().current
            }
        }
        single<WindowSettingsPicker> { PendingWindowSettingsPicker() }
        single<KeyChooserLauncher> { PendingKeyChooserLauncher() }
        single<LinkPlatform> { PendingLinkPlatform() }
        single<HistoryPlatform> { IosHistoryPlatform() }
    }
}
