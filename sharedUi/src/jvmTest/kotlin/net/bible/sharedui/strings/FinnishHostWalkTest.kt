package net.bible.sharedui.strings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.page.window.WindowControl
import net.bible.service.db.DatabaseContainer
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.assembly.*
import net.bible.sharedui.backup.nav.backupNavGraph
import net.bible.sharedui.bookmark.nav.bookmarkNavGraph
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.desktop.assembly.*
import net.bible.sharedui.host.app.*
import net.bible.sharedui.host.reading.HostReadingWindows
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.settings.nav.LocalLiveSettingsLanguage
import net.bible.sharedui.settings.nav.settingsNavGraph
import net.bible.sharedui.strings.FinnishChromeAssertions.assertNoTranslatedEnglish
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.workspaces.nav.workspaceNavGraph
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.koin.mp.KoinPlatform

/** Real host/service/Room route walk; no network catalogue, UIKit accessibility or process-restart claim. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FinnishHostWalkTest {
    @Test fun runtimeLanguageAndPopulatedFeatureRoutesExposeNoTranslatedEnglish() {
        assertNull(KoinPlatform.getKoinOrNull())
        // v2 pointer injection and composition can run on different test threads; both must count as Main
        // for navigation's LifecycleRegistry, while real IO remains on Dispatchers.IO.
        val main = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        val root = Files.createTempDirectory("finnish-host-walk")
        val previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? = if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val fields = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val values = fields.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }.get(instance) != null
        Dispatchers.setMain(main)
        try {
            val paths = DesktopPaths({ key -> when (key) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            } }, "Linux", root.resolve("home"))
            StartupFixture.files.forEach { (name, bytes) ->
                val target = paths.sword.resolve(name)
                Files.createDirectories(target.parent)
                Files.write(target, bytes)
            }
            val window = object : DesktopWindowHandle {
                override val sizeDp = IntSize(390, 844)
                override val density = 1f
                override fun toFront() = Unit
            }
            assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, window, "en", repositories = { PropertyMap() }))
            val koin = KoinPlatform.getKoin()
            // Persisted user label names are not UI chrome and need not change with the app language.
            koin.get<AppSettings>().setLong("data-version", 1L)
            runBlocking { HostReadingReady.ensure(HostStartup.stringTable) }
            val holder = koin.get<HostStringTableHolder>()
            // A user label proves the manager renders loaded service content, not just an empty scaffold.
            runBlocking { withContext(main) {
                koin.get<WindowControl>().windowRepository.initializeAtEdge()
                koin.get<net.bible.android.control.bookmark.BookmarkControl>().insertOrUpdateLabel(
                    net.bible.android.database.bookmarks.BookmarkEntities.Label(name = "Fixture label").apply { new = true },
                )
            } }
            runSkikoComposeUiTest(size = androidx.compose.ui.geometry.Size(390f, 844f)) {
                lateinit var nav: NavHostController
                lateinit var settingsController: net.bible.sharedcore.settings.AppSettingsController
                var fontScale by mutableStateOf(1f)
                setContent {
                    nav = rememberNavController()
                    val scope = rememberCoroutineScope()
                    val shell = remember { HostNavShell(scope, NavControllerNavigator { nav }, recompose = {
                        holder.switchTo(koin.get<net.bible.sharedcore.settings.DeviceLocalPrefs>().getString("locale_pref", "en") ?: "en")
                    }) }
                    val language = holder.current.tag
                    val hosts = remember { AppHosts(koin, shell, buildDesktopHostPorts(koin, shell), iosStrings { holder.current.tag }) }
                    val deps = remember(language) { hosts.settings.navDeps(nav, NavResultChannel { }) }
                    settingsController = deps.appSettings.controller()
                    val bookmark = remember(language) { hosts.bookmark.navDeps(nav, NavResultChannel { }, NavResultChannel { }, NavResultChannel { }) }
                    val workspace = remember(language) { hosts.workspace.navDeps(nav, NavResultChannel { }, NavResultChannel { }, NavResultChannel { }) }
                    val backup = remember(language) { hosts.backup.navDeps(nav) }
                    CompositionLocalProvider(
                        LocalStrings provides iosStrings(language), LocalLiveSettingsLanguage provides true,
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, fontScale),
                        net.bible.sharedui.components.LocalBarFontScaleCap provides 1.35f,
                    ) {
                        AbTheme(darkTheme = false, disableAnimations = true) {
                            androidx.compose.foundation.layout.Box(Modifier.size(390.dp, 844.dp)) {
                                AbNavHost(nav, startDestination = NavRoutes.SETTINGS) {
                                    settingsNavGraph(nav, deps)
                                    bookmarkNavGraph(nav, bookmark)
                                    workspaceNavGraph(nav, workspace)
                                    backupNavGraph(nav, backup)
                                }
                                hosts.bookmark.Overlays()
                            }
                        }
                    }
                    DisposableEffect(hosts) { onDispose { hosts.onHostDestroy() } }
                }
                onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-locale_pref"))
                onNodeWithTag("settings-row-locale_pref").performClick()
                onNodeWithText("fi: Suomi").performScrollTo().performClick()
                onNodeWithText("Sovelluksen asetukset").assertExists()
                // Scan every lazy settings row, not only the first viewport.
                val itemCount = settingsController.state.value.visibleItems.size
                for (index in 0 until itemCount) {
                    onNode(hasScrollToIndexAction()).performScrollToIndex(index)
                    assertNoTranslatedEnglish("settings row $index")
                }
                onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-global_text_display_settings"))
                onNodeWithTag("settings-row-global_text_display_settings").performClick()
                runOnIdle { fontScale = 53f / 17f }
                onNodeWithText("Yleiset tekstiasetukset").assertIsDisplayed()
                val back = onNodeWithContentDescription(iosStrings("fi").settingsEditorBack)
                back.assertIsDisplayed()
                for (node in listOf(onNodeWithText("Yleiset tekstiasetukset"), back)) {
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    kotlin.test.assertTrue(bounds.left >= 0f && bounds.right <= 390f && bounds.top >= 0f && bounds.bottom <= 844f,
                        "text display chrome must fit at AX5: $bounds")
                }
                assertNoTranslatedEnglish("text display settings")
                back.performClick()
                runOnIdle { fontScale = 1f; nav.navigate(NavRoutes.bookmarks()) }
                onNodeWithText("Kirjanmerkit & Omat Merkinnät").assertExists()
                onNodeWithContentDescription("Kirjajärjestys").assertExists()
                assertNoTranslatedEnglish("bookmarks")
                onNodeWithContentDescription("Hallitse kirjanmerkkiryhmiä").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Fixture label").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Fixture label").assertIsDisplayed()
                onNodeWithText("Ryhmät").assertExists()
                assertNoTranslatedEnglish("manage labels")
                onNodeWithContentDescription(iosStrings("fi").settingsEditorBack).performClick()
                onNodeWithText("Kirjanmerkit & Omat Merkinnät").assertExists()
                runOnIdle { nav.navigate(NavRoutes.BACKUP) }
                onAllNodesWithText("Varmuuskopioi & palauta").assertCountEquals(2)
                assertNoTranslatedEnglish("backup")
                runOnIdle { nav.navigate(NavRoutes.SYNC_SETTINGS) }
                onNodeWithText("Laitteiden synkronointi").assertExists()
                val syncCount = onNode(hasScrollToIndexAction()).fetchSemanticsNode()
                    .config[androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo].rowCount
                for (index in 0 until syncCount) {
                    onNode(hasScrollToIndexAction()).performScrollToIndex(index)
                    assertNoTranslatedEnglish("sync row $index")
                }
            }
        } finally {
            runBlocking { withContext(main) { HostReadingWindows.resetForTest() } }
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
            previousHost?.let { DatabaseContainer.installHost(it) }
            fields.zip(values).forEach { (f, value) -> f.set(owner(f), value) }
            if (established) instanceField.set(owner(instanceField), instance) else {
                val reset = runCatching { CWProject::class.java.getDeclaredMethod("reset") }
                    .getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }.apply { isAccessible = true }
                reset.invoke(if (java.lang.reflect.Modifier.isStatic(reset.modifiers)) null else CWProject.Companion)
            }
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
        }
    }
}
