package net.bible.sharedui.app.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.ProceedAnswer
import net.bible.sharedcore.navigation.defaultArrangement
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedcore.startup.StartupWelcomeInfo
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.startup.StartupWelcomeScreen
import net.bible.sharedui.navigation.DocumentSelectionScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WelcomeHostTest {
    private val calls = mutableListOf<String>()

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) { calls += "title:$title" }
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private val welcomeController = StartupWelcomeController {
        StartupWelcomeInfo("v", "formats", previousInstallDetected = false, easyStartAvailable = true)
    }

    private val platform = object : WelcomeHostPlatform {
        override val windowTitle = "Label"
        override val appName = "App"
        override val logo: @Composable () -> Painter? = { null }
        override val controller: StartupWelcomeController get() { calls += "controller"; return welcomeController }
        override fun onDownload() { calls += "download" }
        override fun onImport() { calls += "import" }
        override fun onRestore() { calls += "restore" }
        override fun onRedownload() { calls += "redownload" }
        override fun onEasyStart() { calls += "easyStart" }
        override fun onOpenHomepage() { calls += "homepage" }
        override fun onOpenGithub() { calls += "github" }
        @Composable override fun Dialogs() { calls += "dialogs" }
    }

    private fun navController(): NavHostController {
        lateinit var nav: NavHostController
        runComposeUiTest { setContent { nav = rememberNavController() } }
        return nav
    }

    @Test
    fun advancedWelcomeDownloadInitializesThenConsentsAndShowsCatalogue() = runComposeUiTest {
        val strings = iosStrings("en")
        var route by androidx.compose.runtime.mutableStateOf<String?>(null)
        val core = WelcomeFlowCore(
            versionText = { "v" }, supportedFormatsText = { "formats" }, language = { "en" },
            isDiscrete = { false }, knownInstalled = { emptyList() }, megabytesFree = { 1_000L },
            refuseDownload = { error("ample disk space") }, initialise = { calls += "initialize" },
            ensureSyncLoaded = { calls += "sync" }, navigate = { route = it }, bookLabel = { it.name },
        )
        val controller = core.controller
        setContent {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val state by controller.state.collectAsState()
            CompositionLocalProvider(LocalStrings provides strings) {
                if (route == null) StartupWelcomeScreen(
                    state = state, appName = "App", logo = null,
                    onSelectTab = controller::selectTab,
                    onDownload = { scope.launch { core.onDownload() } }, onImport = platform::onImport,
                    onRestore = platform::onRestore, onRedownload = platform::onRedownload,
                    onEasyStart = platform::onEasyStart, onOpenHomepage = platform::onOpenHomepage,
                    onOpenGithub = platform::onOpenGithub,
                ) else DownloadConsentCatalogue(strings)
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
        waitForIdle()
        assertEquals(net.bible.sharedcore.nav.NavRoutes.download(firstDownload = true), route)
        assertEquals(listOf("initialize", "sync"), calls)
        onNodeWithTag("welcome-screen").assertDoesNotExist()
        onNodeWithText(strings.downloadQuestionTitle).assertExists()
        onNodeWithText(strings.yes).performClick()
        onNodeWithText(strings.downloadQuestionTitle).assertDoesNotExist()
        onNodeWithContentDescription(strings.languageLabel).assertExists()
    }

    @Composable
    private fun DownloadConsentCatalogue(strings: net.bible.sharedui.strings.Strings) {
        val controller = androidx.compose.runtime.remember { DocumentSelectionController(
            langComparator = compareBy { it.displayName },
            onSelect = {}, onDelete = {}, onAbout = {}, onUnlock = {}, onStickyLanguage = {},
            onConfirmDownload = {}, onConfirmDelete = {}, onConfirmDeleteIndex = {},
        ) }
            val dialog by controller.dialog.collectAsState()
            LaunchedEffect(controller) { assertEquals(ProceedAnswer.YES, controller.askProceed()) }
            CompositionLocalProvider(LocalStrings provides strings) {
                DocumentSelectionScreen(
                    title = "Documents", downloadMode = true, loading = false, isRefreshing = false,
                    onRefresh = {}, grouped = emptyList(), languages = emptyList(), selectedLanguage = null,
                    typeFilters = emptyList(), selectedTypeFilter = DocTypeFilter.ALL, query = "",
                    resultCount = "0 documents", selectionMode = false, selectedIds = emptySet(),
                    error = null, dialog = dialog, topBarActions = {}, onQueryChange = {},
                    searchModeActive = false, onOpenSearch = {}, onCloseSearch = {}, onLanguageChange = {},
                    onTypeFilterChange = {}, arrangement = defaultArrangement(emptySet()),
                    groupKeys = listOf(DocGroupBy.NONE), repositories = emptyList(),
                    rememberArrangement = false, arrangementIsDefault = true,
                    onMoveSort = { _, _ -> }, onToggleSortDirection = {}, onGroupByChange = {},
                    onRepositoryChange = {}, onRememberChange = {}, onResetArrangement = {},
                    onRowClick = {}, onRowLongClick = {}, onDownload = {}, onCancel = {},
                    onSelectionAbout = {}, onSelectionDelete = {}, onSelectionDeleteIndex = {},
                    onSelectionUnlock = {}, unlockVisible = false, deleteVisible = false,
                    onDismissError = {}, onConfirmDialog = {}, onDismissDialog = {},
                    onConfirmProceed = controller::confirmProceed, onDismissProceed = controller::dismissProceed,
                    onNavigateUp = {}, onExitSelection = {},
                )
            }
    }

    @Test
    fun navDepsDelegatesToPlatform() {
        val deps = WelcomeHost(shell, platform).navDeps(navController())
        assertEquals("Label", deps.windowTitle)
        assertEquals("App", deps.appName)
        assertSame(platform.logo, deps.logo)
        assertTrue(calls.isEmpty(), "construction must not touch the platform's lazy members: $calls")

        deps.setWindowTitle("T")
        assertSame(welcomeController, deps.controller())
        deps.onDownload()
        deps.onImport()
        deps.onRestore()
        deps.onRedownload()
        deps.onEasyStart()
        deps.onOpenHomepage()
        deps.onOpenGithub()
        assertEquals(
            listOf("title:T", "controller", "download", "import", "restore", "redownload", "easyStart", "homepage", "github"),
            calls,
        )
    }
}
