package net.bible.sharedui.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.app.ports.SystemScreens
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsHostTest {
    private var recreates = 0
    private val calls = mutableListOf<String>()

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() { calls += "exit" }
        override fun setWindowTitle(title: String) { calls += "title:$title" }
        override fun recreateHost() { recreates++ }
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) { calls += "navigate:$route" }
    }

    private class MapSettings : AppSettings {
        val ints = mutableMapOf<String, Int>()
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = ints[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) { if (value == null) ints.remove(key) else ints[key] = value }
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private val prefs = MapSettings()
    // The iOS string holder's `fmt` only knows positional `%1$s`, while discrete_mode_link uses a plain `%s` that
    // Android's getString fills; so the link substitution is stated here as the Android one does it.
    private val strings: Strings = object : Strings by iosStrings("en") {
        override fun discreteModeLink(link: String) = "You can download it from $link."
    }

    private fun host(
        discrete: Boolean = false,
        strings: Strings = this.strings,
        appSettingsProvider: () -> AppSettings = { prefs },
    ) = SettingsHost(
        shell = shell,
        strings = strings,
        appSettingsProvider = appSettingsProvider,
        platform = object : SettingsPlatform {
            override fun resetAllSettings() { calls += "reset" }
            override val isDiscreteBuild = discrete
            override val confirmText = "OK-sys"
            override val dismissText = "Cancel-sys"
        },
        links = object : ExternalLinks {
            override val askBeforeOpening = discrete
            override fun open(url: String) { calls += "open:$url" }
        },
        debug = object : DebugActions {
            override fun crashAppDelayed() { calls += "crash" }
            override fun showTestError(message: String) { calls += "error:$message" }
        },
        systemScreens = object : SystemScreens {
            override fun openAppLinkSettings() { calls += "linkSettings" }
        },
        helpDialogs = object : HelpDialogs {
            override fun show(topic: HelpTopic) { calls += "help:$topic" }
        },
        appSettingsServiceProvider = { error("the app settings service must stay lazy") },
        syncSettingsServiceProvider = { error("the sync settings service must stay lazy") },
        readingProgressSettingsServiceProvider = { error("the reading-progress settings service must stay lazy") },
        readingProgressServiceProvider = { error("the reading-progress service must stay lazy") },
    )

    private fun navController(): NavHostController {
        lateinit var nav: NavHostController
        runComposeUiTest { setContent { nav = rememberNavController() } }
        return nav
    }

    @Test
    fun discreteHelpUsesCalculatorTextInDiscreteBuild() {
        val wiki = "https://github.com/AndBible/and-bible/wiki/Discrete-build"
        val discrete = host(discrete = true).buildDiscreteHelpHtml()
        assertTrue(discrete.contains(strings.calculatorPar1))
        assertTrue(discrete.contains(strings.calculatorPar3))
        assertTrue(discrete.contains(wiki))
        assertFalse(discrete.contains(strings.discreteModeInfoPar1))

        val normal = host(discrete = false).buildDiscreteHelpHtml()
        assertTrue(normal.contains(strings.discreteModeInfoPar1))
        assertTrue(normal.contains(wiki))
        assertFalse(normal.contains(strings.calculatorPar1))
        assertFalse(normal.contains(strings.calculatorPar3))
    }

    @Test
    fun recreateOnlyForRecreateKeys() {
        val host = host()
        RECREATE_ON_CHANGE_KEYS.forEachIndexed { index, key ->
            host.maybeRecreateForSettingsKey(key)
            assertEquals(index + 1, recreates, "key $key must recreate once")
        }
        val before = recreates
        host.maybeRecreateForSettingsKey("some_other_key")
        assertEquals(before, recreates)
        assertEquals(setOf("locale_pref", "night_mode_pref3", "display_color_mode", "theme_palette_style", "discrete_mode"), RECREATE_ON_CHANGE_KEYS)
    }

    @Test
    fun readingProgressInitialTab() {
        val host = host()
        assertEquals(ReadingTab.MEMORIZE, host.readingProgressInitialTab(1))
        assertEquals(ReadingTab.READING, host.readingProgressInitialTab(0))
        prefs.ints[PREF_READING_PROGRESS_LAST_TAB] = 1
        assertEquals(ReadingTab.MEMORIZE, host.readingProgressInitialTab(null))
        assertEquals(ReadingTab.READING, host.readingProgressInitialTab(0), "an explicit argument beats the persisted tab")
        prefs.ints[PREF_READING_PROGRESS_LAST_TAB] = 0
        assertEquals(ReadingTab.READING, host.readingProgressInitialTab(null))
    }

    @Test
    fun navDepsIsLazyAndRoutesPlatformRows() {
        val deps = host(discrete = true, appSettingsProvider = { error("the app settings must stay lazy") })
            .navDeps(navController(), NavResultChannel<ReadingProgressResult> { })
        // Building the deps touched no service (the providers above throw) and no platform member.
        assertTrue(calls.isEmpty(), "$calls")

        val app = deps.appSettings
        app.onOpenLinksSettings()
        app.onCrashApp()
        app.onTestErrorDialog()
        app.onTestErrorDialogQueue()
        app.onOpenExternal("https://example.org")
        assertTrue(app.askBeforeOpeningLink())
        deps.exitHost()
        assertEquals(
            listOf(
                "linkSettings", "crash", "error:Test error (debug build)", "error:Test error 1 of 2",
                "error:Test error 2 of 2", "open:https://example.org", "exit",
            ),
            calls,
        )
        assertEquals("OK-sys", deps.readingProgress.confirmText)
        assertEquals("Cancel-sys", deps.readingProgress.dismissText)
        deps.readingProgress.onShowHelp()
        assertEquals("help:READING_PROGRESS", calls.last())
    }
}
