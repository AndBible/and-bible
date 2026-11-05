package net.bible.sharedui.app.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.startup.nav.welcomeNavGraph
import net.bible.sharedui.download.nav.downloadNavGraph
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedlogic.platform.desktop.DesktopPlatformLocales
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.sharedui.app.download.DownloadHostPlatform
import net.bible.android.control.download.repoIdentity
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import kotlin.test.assertSame

class DownloadHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private class IntSettings(private val values: Map<String, Int> = emptyMap()) : AppSettings {
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        override fun getString(key: String, default: String?): String? = strings[key] ?: default
        override fun getLong(key: String, default: Long): Long = default
        override fun getInt(key: String, default: Int): Int = values[key] ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
        override fun getDouble(key: String, default: Double): Double = default
        override fun getFloat(key: String, default: Float): Float = default
        override fun setString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) {
            if (value == null) booleans.remove(key) else booleans[key] = value
        }
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected ${m.name}")
        } as T

    private fun host(
        settings: AppSettings,
        language: String = "en",
        platform: DownloadHostPlatform = unused(),
        hostShell: HostShell = shell,
        uiLanguage: String = "en",
    ) = DownloadHost(
        shell = hostShell,
        strings = iosStrings(uiLanguage),
        platform = platform,
        notificationPermission = unused(),
        clipboard = unused(),
        openInstallZip = { _, _, _ -> },
        observeProgressJobs = { { } },
        appSettingsProvider = { settings },
        userNotifierProvider = { unused() },
        directoriesProvider = { error("unused") },
        environmentProvider = { unused() },
        coreStringsProvider = { unused() },
        downloadHttpProvider = { unused() },
        bookInstallWatcherProvider = {
            BookInstallWatcher(
                OrderedLauncher(shell.hostScope), unused(), unused(),
                DocumentSyncSettings(
                    { error("unused") }, { error("unused") }, { error("unused") },
                    OrderedLauncher(shell.hostScope), unused(),
                ),
                unused(),
            )
        },
        downloadControlProvider = { error("unused") },
        documentControlProvider = { error("unused") },
        fakeBookFactoryProvider = { error("unused") },
        customRepositoryServiceProvider = { error("unused") },
        documentSyncSettingsProvider = { error("unused") },
        platformLocalesProvider = { DesktopPlatformLocales { language } },
    )

    private val english = LangOption("en", "English", "en")
    private val finnish = LangOption("fi", "Finnish", "fi")
    private fun row(id: String, language: LangOption) = DocRow(
        id, id, id, id, language, "test", DocCategory.BIBLE, DocInstallStatus.NOT_INSTALLED,
        0, false, false, false, false, false, null,
    )
    private val rows get() = listOf(row("KJV", english), row("Fin", finnish))

    /** Replaces only network/lifecycle ports; the Welcome and Download navigation and consent policy are production. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun advancedWelcomeReachesProductionDownloadConsentBeforeCatalogueWork() {
        // The Compose test body (composition, NavHost, lifecycle) runs on the test thread, and androidx.lifecycle's
        // main-thread check asks Dispatchers.Main.immediate whether a dispatch is needed. A separate Main thread
        // therefore always fails that check; an unconfined Main treats the calling (test) thread as main.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            for (language in listOf("en", "fi")) {
                runComposeUiTest {
                    val settings = IntSettings()
                    val strings = iosStrings(language)
                    val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
                    lateinit var nav: androidx.navigation.NavHostController
                    setContent {
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        nav = androidx.navigation.compose.rememberNavController()
                        val shell = androidx.compose.runtime.remember {
                            object : HostShell by this@DownloadHostTest.shell {
                                override val hostScope = scope
                                override fun navigate(route: String) { nav.navigate(route) }
                            }
                        }
                        val core = androidx.compose.runtime.remember {
                            net.bible.sharedui.app.welcome.WelcomeFlowCore(
                                versionText = { "v" }, supportedFormatsText = { "formats" }, language = { "en" },
                                isDiscrete = { false }, knownInstalled = { emptyList() }, megabytesFree = { 1_000L },
                                refuseDownload = { error("ample space") }, initialise = { calls += "initialize" },
                                ensureSyncLoaded = { calls += "sync" }, navigate = shell::navigate, bookLabel = { it.name },
                            )
                        }
                        val platform = androidx.compose.runtime.remember {
                            object : net.bible.sharedui.app.welcome.WelcomeHostPlatform {
                                override val windowTitle = "App"
                                override val appName = "App"
                                override val logo: @androidx.compose.runtime.Composable () -> androidx.compose.ui.graphics.painter.Painter? = { null }
                                override val controller get() = core.controller
                                override fun onDownload() { scope.launch { core.onDownload() } }
                                override fun onImport() = Unit
                                override fun onRestore() = Unit
                                override fun onRedownload() = Unit
                                override fun onEasyStart() = Unit
                                override fun onOpenHomepage() = Unit
                                override fun onOpenGithub() = Unit
                                @androidx.compose.runtime.Composable override fun Dialogs() = Unit
                            }
                        }
                        val download = androidx.compose.runtime.remember {
                            host(settings, hostShell = shell, uiLanguage = language).navDeps(
                                nav, net.bible.sharedui.nav.NavResultChannel { error("no repository result") },
                                net.bible.sharedui.download.nav.CloudDocumentsDeps(
                                    controllerFor = { error("cloud route not entered") }, title = "Cloud",
                                    topBarActions = {}, openOrGate = { false }, seedItems = { emptyList() },
                                    refreshFromNetwork = {}, subscribeProgress = { {} },
                                    statusFilterLabels = { emptyList() }, categoryFilterLabels = { emptyList() },
                                    countLabel = { _, _ -> "" },
                                ),
                            )
                        }
                        val live = download.download
                        val isolatedDownload = androidx.compose.runtime.remember {
                            net.bible.sharedui.download.nav.DownloadNavDeps(
                                exitHost = { error("unexpected exit") }, setWindowTitle = {},
                                repositoryEditorResults = download.repositoryEditorResults,
                                customRepositories = download.customRepositories,
                                customRepositoryEditor = download.customRepositoryEditor,
                                progressStatus = download.progressStatus, cloudDocuments = download.cloudDocuments,
                                download = net.bible.sharedui.download.nav.DownloadDeps(
                                    controllerFor = live.controllerFor, sessionToken = live.sessionToken, title = live.title,
                                    topBarActions = {}, askIfWantToProceed = live.askIfWantToProceed,
                                    requestNotificationPermission = { calls += "permission" },
                                    refreshCatalogue = { calls += "catalogue" }, onAutoDownload = { _, _ -> },
                                    reloadCatalogueIfRequested = {}, onCancelDownload = {}, hasBible = live.hasBible,
                                    subscribeDownloadProgress = { {} }, subscribeMonitoring = { {} },
                                    persistTypeFilter = live.persistTypeFilter, initialTypeFilter = live.initialTypeFilter,
                                ),
                            )
                        }
                        androidx.compose.runtime.CompositionLocalProvider(net.bible.sharedui.strings.LocalStrings provides strings) {
                            net.bible.sharedui.theme.AbTheme(darkTheme = false, disableAnimations = true) {
                                net.bible.sharedui.components.AbNavHost(nav, startDestination = net.bible.sharedcore.nav.NavRoutes.WELCOME) {
                                    welcomeNavGraph(
                                        net.bible.sharedui.app.welcome.WelcomeHost(shell, platform).navDeps(nav),
                                    )
                                    downloadNavGraph(nav, isolatedDownload)
                                }
                            }
                        }
                    }
                    onNodeWithTag("welcome-easy-start").assertExists()
                    onNodeWithText(strings.welcomeTabAdvanced).performClick()
                    onNodeWithTag("welcome-easy-start").assertDoesNotExist()
                    onNodeWithText(strings.welcomeImportButton).assertExists()
                    onNodeWithText(strings.welcomeRestoreButton).assertExists()
                    onNodeWithText(strings.welcomeHomepageLabel).assertExists()
                    onNodeWithText(strings.welcomeGithubLabel).assertExists()
                    onNodeWithText(strings.welcomeDownloadButton).performClick()
                    waitUntil(timeoutMillis = 10_000) {
                        onAllNodesWithText(strings.downloadQuestionTitle).fetchSemanticsNodes().isNotEmpty()
                    }
                    onNodeWithText(strings.downloadQuestionTitle).assertExists()
                    assertEquals(listOf("initialize", "sync"), calls, "network and permissions wait for consent")
                    onNodeWithText(strings.yes).performClick()
                    waitUntil(timeoutMillis = 10_000) { "catalogue" in calls }
                    waitForIdle()
                    onNodeWithText(strings.downloadQuestionTitle).assertDoesNotExist()
                    onNodeWithTag("welcome-screen").assertDoesNotExist()
                    onNodeWithContentDescription(strings.languageLabel).assertExists()
                    onNodeWithText(strings.downloadTitle).assertExists()
                    if (language == "fi") {
                        onAllNodesWithText("Download Documents").fetchSemanticsNodes().let { assertEquals(0, it.size) }
                        onNodeWithContentDescription("Language").assertDoesNotExist()
                    }
                    assertEquals(listOf("initialize", "sync", "permission", "catalogue"), calls)
                    assertEquals(false, settings.getBoolean("download_do_not_ask", false), "ordinary Yes is not permanent consent")
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun firstCatalogueUsesSystemLanguage() {
        val host = host(IntSettings(), "en")
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        assertEquals(english, controller.selectedLanguage.value)
        assertEquals(listOf("KJV"), controller.displayed.value.map { it.docId })
    }

    @Test
    fun automaticDefaultDoesNotBecomeARememberedUserPick() {
        val settings = IntSettings()
        val first = host(settings, "en")
        first.downloadControllerFor(DocTypeFilter.ALL)
        first.setDownloadDocuments(rows)
        val afterLocaleChange = host(settings, "fi")
        val controller = afterLocaleChange.downloadControllerFor(DocTypeFilter.ALL)
        afterLocaleChange.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
    }

    @Test
    fun pickedLanguageSurvivesANewHost() {
        val settings = IntSettings()
        val first = host(settings)
        val controller = first.downloadControllerFor(DocTypeFilter.ALL)
        first.setDownloadDocuments(rows)
        controller.setLanguage(finnish)
        val reopened = host(settings)
        val restored = reopened.downloadControllerFor(DocTypeFilter.ALL)
        reopened.setDownloadDocuments(rows)
        assertEquals(finnish, restored.selectedLanguage.value)
        assertEquals(listOf("Fin"), restored.displayed.value.map { it.docId })
    }

    @Test
    fun unavailableRememberedLanguageFallsBackToSystemThenAll() {
        val settings = IntSettings().apply { strings["selected_language_code"] = "de" }
        val host = host(settings)
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        assertEquals(english, controller.selectedLanguage.value)
        assertEquals("de", settings.strings["selected_language_code"])
        val unsupported = host(IntSettings(), "de")
        val all = unsupported.downloadControllerFor(DocTypeFilter.ALL)
        unsupported.setDownloadDocuments(rows)
        assertNull(all.selectedLanguage.value)
        assertEquals(2, all.resultCount.value)
    }

    @Test
    fun catalogueRefreshAndReentryDoNotResetAllOrUserLanguage() {
        val host = host(IntSettings())
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        controller.setLanguage(null)
        host.setDownloadDocuments(rows)
        assertNull(controller.selectedLanguage.value)
        controller.setLanguage(finnish)
        host.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
        assertSame(controller, host.downloadControllerFor(DocTypeFilter.BIBLE))
    }

    @Test
    fun languageMatchingUsesCatalogueGroupingForIso3AndRegionVariants() {
        val settings = IntSettings().apply { strings["selected_language_code"] = "fin" }
        val host = host(settings)
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
        val regional = host(IntSettings(), "fi-FI")
        val regionalController = regional.downloadControllerFor(DocTypeFilter.ALL)
        regional.setDownloadDocuments(rows)
        assertEquals(finnish, regionalController.selectedLanguage.value)
    }

    @Test
    fun emptyCatalogueDoesNotConsumeInitialLanguageSelection() {
        val host = host(IntSettings(), "fi")
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(emptyList())
        host.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
    }

    @Test
    fun initialTypeFilter() {
        val settings = IntSettings(mapOf("selected_document_filter_no" to 1))
        // The addons route argument wins over the stored filter.
        assertEquals(DocTypeFilter.ADDON, host(settings).initialTypeFilter(addons = true))
        // Otherwise the stored index picks the entry.
        assertEquals(DocTypeFilter.entries[1], host(settings).initialTypeFilter(addons = false))
        // An out-of-range stored index falls back to ALL.
        val outOfRange = IntSettings(mapOf("selected_document_filter_no" to 999))
        assertEquals(DocTypeFilter.ALL, host(outOfRange).initialTypeFilter(addons = false))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aboutActionShowsAboutForTheSelectedCatalogueBook() {
        // Pins what iOS FeatureUITests.testDownloadAboutDialog needed from the host: About on a selected row reaches showAbout(book).
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val shown = mutableListOf<Book>()
            val platform = Proxy.newProxyInstance(
                DownloadHostPlatform::class.java.classLoader, arrayOf(DownloadHostPlatform::class.java),
            ) { _, m, args ->
                if (m.name == "showAbout") { shown += args!![0] as Book; Unit } else error("unexpected ${m.name}")
            } as DownloadHostPlatform
            val book = SwordBook(
                SwordBookMetaData("""
                    [engKJV2006eb]
                    Description=King James Version
                    ModDrv=RawText
                    Versification=KJV
                    Lang=en
                """.trimIndent().encodeToByteArray(), "engKJV2006eb"),
                NullBackend(),
            )
            val host = host(IntSettings(), platform = platform)
            val controller = host.downloadControllerFor(DocTypeFilter.ALL)
            host.setDownloadDocuments(listOf(row(book.repoIdentity, english)))
            // The catalogue load needs the network and the install manager; seed the session's docId -> Book map directly.
            val sessionField = DownloadHost::class.java.getDeclaredField("session").apply { isAccessible = true }
            val session = sessionField.get(host)
            session.javaClass.getDeclaredField("booksById").apply { isAccessible = true }
                .set(session, mapOf(book.repoIdentity to book))
            controller.toggle(book.repoIdentity)
            controller.about()
            assertEquals(1, shown.size)
            assertSame(book, shown.single())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
