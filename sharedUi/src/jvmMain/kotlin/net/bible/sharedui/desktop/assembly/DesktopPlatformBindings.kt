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

import net.bible.android.control.buildSharedBindings
import net.bible.android.control.link.LinkPlatform
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.control.page.window.SettingLabels
import net.bible.android.control.page.window.WindowSettingsPicker
import net.bible.android.control.search.SearchTextHighlighter
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.view.activity.settings.AppSettingsServiceImpl
import net.bible.service.common.GlobalTextDisplaySettingsStore
import net.bible.service.db.DatabaseContainer
import net.bible.service.format.SaxTextPolicy
import net.bible.service.history.HistoryPlatform
import net.bible.service.llm.LlmStrings
import net.bible.service.startup.ReadingAppInit
import net.bible.service.sword.HtmlToPlainText
import net.bible.sharedcore.http.DesktopHttpClients
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.KtorDownloadHttp
import net.bible.sharedcore.llm.http.KtorLlmHttpTransport
import net.bible.sharedcore.llm.http.LlmHttpTransport
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.MarkdownRenderer
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.desktop.DesktopAppDirectories
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopDateTimeFormats
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.settings.SettingsPlatformFlags
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedlogic.addons.SharedAddonCatalog
import net.bible.sharedlogic.addons.SharedAddonScanner
import net.bible.sharedlogic.addons.SharedPromptPackSource
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.platform.BackgroundImageCatalog
import net.bible.sharedlogic.platform.DistributedPlanCatalog
import net.bible.sharedlogic.platform.EpubStoreEdge
import net.bible.sharedlogic.platform.FirstLoadEdge
import net.bible.sharedlogic.platform.LabelNameStrings
import net.bible.sharedlogic.platform.LocalCalendar
import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedlogic.platform.ManualInstallFolderEnabler
import net.bible.sharedlogic.platform.MyDocumentStoreEdge
import net.bible.sharedlogic.platform.PlatformLocales
import net.bible.sharedlogic.platform.PromptPackSource
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.SearchIndexScheduler
import net.bible.sharedlogic.platform.desktop.DesktopLocalCalendar
import net.bible.sharedlogic.platform.desktop.DesktopLocaleSupport
import net.bible.sharedlogic.platform.desktop.DesktopPlatformLocales
import net.bible.sharedlogic.platform.host.HostEpubStoreEdge
import net.bible.sharedlogic.platform.host.HostFirstLoadEdge
import net.bible.sharedlogic.platform.host.HostHistoryPlatform
import net.bible.sharedlogic.platform.host.HostKeyChooserLauncher
import net.bible.sharedlogic.platform.host.HostLinkPlatform
import net.bible.sharedlogic.platform.host.HostMarkdownRenderer
import net.bible.sharedlogic.platform.host.HostMyDocumentStoreEdge
import net.bible.sharedlogic.platform.host.HostReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.host.HostSearchIndexScheduler
import net.bible.sharedlogic.platform.host.HostUserNotifier
import net.bible.sharedlogic.platform.host.HostWindowSettingsPicker
import net.bible.sharedlogic.search.CommonSearchHighlighter
import net.bible.sharedlogic.settings.DatabaseAppSettings
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedlogic.text.CommonHtmlToPlainText
import net.bible.sharedui.app.settings.LegacyDataImport
import net.bible.sharedui.app.ports.DocumentInstallStarter
import net.bible.sharedui.app.ports.OrientationLock
import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.HostWindowMetrics
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.assembly.hostSyncStrings
import net.bible.sharedui.components.AppToasts
import net.bible.sharedui.host.app.HostRootLinks
import net.bible.sharedui.host.ports.HostDocumentInstallStarter
import net.bible.sharedui.host.ports.HostHelpDialogs
import net.bible.sharedui.host.ports.HostReadingExtrasImpl
import net.bible.sharedui.host.ports.HostSettingsPlatform
import net.bible.sharedui.host.reading.HostReadingExtras
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import net.bible.sharedui.strings.keyed.KeyedDistributedPlanCatalog
import net.bible.sharedui.strings.keyed.KeyedLabelNameStrings
import net.bible.sharedui.strings.keyed.KeyedLlmStrings
import net.bible.sharedui.strings.keyed.KeyedSettingLabels
import net.bible.sharedui.strings.keyed.SharedTextDisplayPrefItems
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.Books
import org.koin.core.module.Module
import org.koin.dsl.module

private const val TAG = "DesktopPlatformBindings"

/** Name of the device-local preferences file inside the config directory. */
const val DESKTOP_DEVICE_SETTINGS_FILE = "device-settings.properties"

/** A hoisted-iOS-class parameter default that does nothing: the desktop has no debug launch arguments. */
object DesktopNoDebugArgs : HostDebugArgs

/** The orientation never changes on a desktop. */
class DesktopOrientationLock : OrientationLock {
    override fun lockCurrentOrientation(): () -> Unit = {}
}

/**
 * Raw text resources (the two LLM system prompts) are classpath files, `<name>.md` at the root or under `raw/`
 * (the packaging copies `app/src/main/res/raw`). A missing one fails when it is first read, naming the file, so screens
 * that never read a prompt are unaffected. This is what `IosPlatformParts.bundleRawResource` does with the bundle.
 */
internal fun classpathRawResource(name: String): String {
    val loader = DesktopOrientationLock::class.java.classLoader
    val stream = listOf("$name.md", "raw/$name.md").firstNotNullOfOrNull { loader.getResourceAsStream(it) }
        ?: error("Raw resource '$name.md' is not on the classpath (package app/src/main/res/raw/$name.md)")
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
}

/** The licence text of the help dialog (`license.html`, classpath); a placeholder, logged, when it is not packaged. */
internal fun classpathBundledHtml(name: String): String {
    val loader = DesktopOrientationLock::class.java.classLoader
    val stream = listOf("$name.html", "html/$name.html").firstNotNullOfOrNull { loader.getResourceAsStream(it) }
    if (stream == null) {
        Log.w(TAG, "Bundled HTML '$name.html' is not on the classpath")
        return "<p>$name.html is not packaged with this build.</p>"
    }
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
}

/**
 * The desktop platform half of the Koin graph: the counterpart of `buildIosPlatformBindings` plus `buildIosHostBindings`,
 * followed by the shared bindings (`buildSharedBindings()`, as `buildIosCoreModule` does; do not add them again).
 * Wave-3 owned ports are NOT bound here but in the four stream modules ([desktopReadingModule], [desktopDocumentModule],
 * [desktopMiscModule], [desktopSyncModule]); this module only reads them with `get()`.
 *
 * Nothing starts when the module is built or resolved: no database opens, no network is used. Builds a NEW `Module`
 * on every call (a `Module` caches its singletons). [uiLanguage] pins the string-table language (tests); null resolves
 * it from `locale_pref`, `LANGUAGE`/`LANG` and the JVM locale ([DesktopUiLanguage]). [launchArgs] are `main()`'s
 * arguments ([DesktopLaunchArgs], read by D3b); [lifecycleEvents] is the host lifecycle's copy of its events for the
 * modules below `:sharedUi` (D3c). The graph also holds the [DesktopExitTasks] that the exit path joins.
 */
fun desktopPlatformModule(
    paths: DesktopPaths,
    window: DesktopWindowHandle,
    uiLanguage: String? = null,
    launchArgs: List<String> = emptyList(),
    lifecycleEvents: DesktopLifecycleEvents = DesktopLifecycleEvents(),
): Module {
    // One scanner for both add-on ports, so a clearCaches() on the catalog also reloads the prompt packs.
    val scanner = SharedAddonScanner(books = { Books.installed().getBooks() })
    val promptPacks = SharedPromptPackSource(scanner)

    val platform = module {
        // --- launch, lifecycle and exit (wave-3 seams: D3b reads the arguments, D3c observes and registers)
        single { DesktopLaunchArgs(launchArgs) }
        single { lifecycleEvents }
        single { DesktopExitTasks() }

        // --- device, sandbox and settings
        single { DesktopDeviceSettings(paths.config.resolve(DESKTOP_DEVICE_SETTINGS_FILE)) }
        single<DeviceLocalPrefs> { get<DesktopDeviceSettings>() }
        single {
            DesktopAppEnvironment(paths, get<SecretStore>(), get<DesktopDeviceSettings>(), initialized = { ReadingAppInit.initialized })
        }
        single<AppEnvironment> { get<DesktopAppEnvironment>() }
        single<AppDirectories> { DesktopAppDirectories(paths) }
        single<AppSettings> { DatabaseAppSettings({ DatabaseContainer.instance.settingsStore }, { HostStartup.databasesOpen }) }

        // --- strings (retained wrappers read the current language per access)
        single {
            val language = uiLanguage ?: DesktopUiLanguage.resolve(get<DesktopDeviceSettings>())
            HostStringTableHolder(hostStringTable(language, ::classpathRawResource))
        }
        single<CoreStrings> { KeyedCoreStrings { get<HostStringTableHolder>().current } }
        single<net.bible.service.cloudsync.host.HostSyncStrings> { hostSyncStrings(get<HostStringTableHolder>().current) }
        single<LlmStrings> { KeyedLlmStrings { get<HostStringTableHolder>().current } }
        single<LabelNameStrings> { KeyedLabelNameStrings { get<HostStringTableHolder>().current } }
        single<SettingLabels> { KeyedSettingLabels { get<HostStringTableHolder>().current } }
        single<DistributedPlanCatalog> { KeyedDistributedPlanCatalog { get<HostStringTableHolder>().current } }
        single<TextDisplayPrefItems> {
            SharedTextDisplayPrefItems(
                strings = { get<HostStringTableHolder>().live },
                contentSource = { get() },
                windowControl = { get() },
                documentControl = { get() },
                settings = { get() },
                backgroundImages = { scanner.backgroundImages().mapValues { it.value.name } },
            )
        }
        single<BackgroundImageCatalog> { get<AddonCatalog>() as SharedAddonCatalog }
        // Page asset routes of the BibleView origin (`/background/`, `/module/`, ...), asked by the web engine.
        single<net.bible.sharedui.desktop.webengine.DesktopPageAssets> {
            net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes.fromKoin { println("[desktop] $it") }
        }

        // --- locale, calendar, text
        single<DateTimeFormats> { DesktopDateTimeFormats() }
        single<LocalCalendar> { DesktopLocalCalendar() }
        single<PlatformLocales> { DesktopPlatformLocales() }
        single<LocaleSupport> { DesktopLocaleSupport }
        single<HtmlToPlainText> { CommonHtmlToPlainText }
        single<SearchTextHighlighter> { CommonSearchHighlighter }
        single<SaxTextPolicy> { SaxTextPolicy.MODERN }
        single<MarkdownRenderer> { HostMarkdownRenderer() }
        single<PlatformText> { DesktopPlatformText(table = { get<HostStringTableHolder>().current }) }

        // --- content and storage
        single<ReadOnlyModuleDbOpener> { HostReadOnlyModuleDbOpener() }
        single<FirstLoadEdge> { HostFirstLoadEdge }
        single<EpubStoreEdge> { HostEpubStoreEdge }
        single<MyDocumentStoreEdge> { HostMyDocumentStoreEdge }
        single<PromptPackSource> { promptPacks }
        single<AddonCatalog> { SharedAddonCatalog(scanner, promptPacks) }
        // No background-task budget on a desktop: the index build simply runs.
        single<SearchIndexScheduler> { HostSearchIndexScheduler({ _, build -> build { false } }, get<AppCoroutineScope>()) }
        single<ManualInstallFolderEnabler> { ManualInstallFolderEnabler { } }

        // --- network
        single { DesktopHttpClients() }
        single<DownloadHttp> { KtorDownloadHttp(get<DesktopHttpClients>().downloadClient) { WebResource.getTimeout() } }
        single<LlmHttpTransport> { KtorLlmHttpTransport(get<DesktopHttpClients>()::clientFor) }

        // --- notifications
        single<UserNotifier> {
            HostUserNotifier(
                get<AppDialogController>(),
                okText = { get<HostStringTableHolder>().current.text("okay") },
                onInfo = { message, long -> AppToasts.show(message, long) },
            )
        }

        // --- reading UI ports, routed through the shared root
        single<GlobalTextDisplaySettingsSource> {
            val scope = this
            object : GlobalTextDisplaySettingsSource {
                override val current get() = scope.get<GlobalTextDisplaySettingsStore>().current
            }
        }
        single<WindowSettingsPicker> {
            val scope = this
            HostWindowSettingsPicker(
                dialogs = get<AppDialogController>(),
                title = { win, type ->
                    val repository = win.windowRepository
                    val bundle = SettingsBundle(
                        level = SettingsLevel.WORKSPACE,
                        workspaceId = repository.id,
                        workspaceName = repository.name,
                        workspaceSettings = win.pageManager.textDisplaySettings,
                        globalSettings = scope.get<GlobalTextDisplaySettingsStore>().current,
                    )
                    scope.get<TextDisplayPrefItems>().item(bundle, type).title ?: type.name
                },
                text = { get<HostStringTableHolder>().current.text(it) },
            )
        }
        single<HostReadingExtras> {
            val holder = get<HostStringTableHolder>()
            val currentTable = { holder.current }
            val bugReport = get<DesktopBugReport>()
            HostReadingExtrasImpl(
                helpDialogs = HostHelpDialogs(get<AppDialogController>(), currentTable, ::classpathBundledHtml, { get<DesktopAppEnvironment>().versionName }),
                table = currentTable,
                scope = get<AppCoroutineScope>(),
                textShare = get<TextShare>(),
                report = { cause -> bugReport.report(cause) },
            )
        }
        single<KeyChooserLauncher> { HostKeyChooserLauncher(HostRootLinks::startKeyChooser) }
        single<LinkPlatform> {
            val koinScope = this
            HostLinkPlatform(
                openRoute = HostRootLinks::openRoute,
                searchStrongs = HostRootLinks::searchStrongsInReadingView,
                reportError = {
                    koinScope.getOrNull<HostReadingExtras>()?.reportJsError()
                        ?: Log.w(TAG, "LinkPlatform.reportJsError: no error report flow")
                },
            )
        }
        single<HistoryPlatform> { HostHistoryPlatform(isOnReading = HostRootLinks::isOnReadingScreen, leave = HostRootLinks::leaveCurrentScreen) }

        // --- host ports of the hoisted classes (`buildIosHostBindings` counterpart)
        single {
            val scope = this
            // LegacyDataImport is never bound on desktop: the legacy-import row stays hidden.
            HostSettingsPlatform(get<CoreStrings>(), get(), get(), legacyImport = { scope.getOrNull<LegacyDataImport>() })
        }
        single<SettingsPlatform> { get<HostSettingsPlatform>() }
        single<SettingsPlatformFlags> { get<HostSettingsPlatform>() }
        single<DocumentInstallStarter> { HostDocumentInstallStarter() }
        factory<AppSettingsService> {
            AppSettingsServiceImpl(settings = get(), devicePrefs = get(), strings = get(), platform = get())
        }
        // LegacyDataImport is iOS-only: nothing is bound, every reader uses getOrNull.

        // --- window and debug
        single<OrientationLock> { DesktopOrientationLock() }
        single<HostWindowMetrics> { DesktopWindowMetrics(window) }
        single<HostDebugArgs> { DesktopNoDebugArgs }
        single<DesktopWindowHandle> { window }
    }
    return module { includes(platform, buildSharedBindings()) }
}

