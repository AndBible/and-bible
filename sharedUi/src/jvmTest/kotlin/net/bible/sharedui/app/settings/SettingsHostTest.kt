package net.bible.sharedui.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
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
import net.bible.sharedui.settings.nav.settingsNavGraph
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
        val bools = mutableMapOf<String, Boolean>()
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = ints[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = bools[key] ?: default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) { if (value == null) ints.remove(key) else ints[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { if (value == null) bools.remove(key) else bools[key] = value }
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
        hostShell: HostShell = shell,
        serviceProvider: () -> net.bible.sharedcore.settings.AppSettingsService = { error("the app settings service must stay lazy") },
    ) = SettingsHost(
        shell = hostShell,
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
        appSettingsServiceProvider = serviceProvider,
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
    fun localeChangeThroughHostRecreatesLanguageAndRetainsScreenPreference() {
        val local = object : net.bible.sharedcore.settings.DeviceLocalPrefs {
            val values = mutableMapOf<String, String>()
            override fun getString(key: String, default: String?) = values[key] ?: default
            override fun getBoolean(key: String, default: Boolean) = values[key]?.toBoolean() ?: default
            override fun setString(key: String, value: String) { values[key] = value }
            override fun setBoolean(key: String, value: Boolean) { values[key] = value.toString() }
            override fun remove(key: String) { values.remove(key) }
        }
        val holder = net.bible.sharedui.assembly.HostStringTableHolder(net.bible.sharedui.assembly.hostStringTable("en"))
        val navigator = object : net.bible.sharedui.host.app.HostNavigator {
            override fun navigate(route: String) = Unit
            override fun popBackStack() = false
            override fun popBackStackTo(route: String, inclusive: Boolean) = false
        }
        val hostScope = CoroutineScope(Job())
        val liveShell = net.bible.sharedui.host.app.HostNavShell(hostScope, navigator, recompose = {
            holder.switchTo(local.getString("locale_pref", "en") ?: "en")
        })
        val options = object : net.bible.android.view.activity.settings.DictionaryOptionsProvider {
            override val greekDictOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
            override val hebrewDictOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
            override val greekMorphOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
            override val wordLookupDictOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
        }
        val flags = java.lang.reflect.Proxy.newProxyInstance(
            net.bible.sharedcore.settings.SettingsPlatformFlags::class.java.classLoader,
            arrayOf(net.bible.sharedcore.settings.SettingsPlatformFlags::class.java),
        ) { _, method, _ -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null } as net.bible.sharedcore.settings.SettingsPlatformFlags
        val service = net.bible.android.view.activity.settings.AppSettingsServiceImpl(
            options, prefs, local, net.bible.sharedui.strings.keyed.KeyedCoreStrings(holder.live), flags,
        )
        try {
            runComposeUiTest {
                setContent {
                    val nav = rememberNavController()
                    val languageStrings = iosStrings(holder.current.tag)
                    val deps = androidx.compose.runtime.remember {
                        host(strings = iosStrings { holder.current.tag }, hostShell = liveShell, serviceProvider = { service }).navDeps(
                            nav, NavResultChannel<ReadingProgressResult> { },
                        )
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        net.bible.sharedui.strings.LocalStrings provides languageStrings,
                        net.bible.sharedui.settings.nav.LocalLiveSettingsLanguage provides true,
                    ) {
                        net.bible.sharedui.theme.AbTheme(darkTheme = false, disableAnimations = true) {
                            net.bible.sharedui.components.AbNavHost(nav, startDestination = net.bible.sharedcore.nav.NavRoutes.SETTINGS) {
                                settingsNavGraph(nav, deps)
                            }
                        }
                    }
                }
                onNodeWithText(iosStrings("en").settings).assertExists()
                onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-screen_keep_on_pref"))
                onNodeWithTag("settings-row-screen_keep_on_pref").performClick()
                onNodeWithTag("settings-row-screen_keep_on_pref").assertIsOn()
                onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-locale_pref"))
                onNodeWithTag("settings-row-locale_pref").performClick()
                onNodeWithText("fi: Suomi").performScrollTo().performClick()
                onNodeWithText(iosStrings("fi").settings).assertExists()
                onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-screen_keep_on_pref"))
                onNodeWithTag("settings-row-screen_keep_on_pref").assertIsOn()
            }
            assertEquals("fi", holder.current.tag)
            assertEquals(net.bible.sharedui.strings.lookup("fi", "studypads"), holder.live.text("studypads"))
            assertTrue(service.snapshot.value.screenKeepOn)
            assertTrue(prefs.getBoolean("screen_keep_on_pref", false))
        } finally { hostScope.coroutineContext[Job]?.cancel() }
    }

    @Test
    fun systemNotificationsDeniedLinkRoutesThroughSettingsGraph() = runComposeUiTest {
        val local = object : net.bible.sharedcore.settings.DeviceLocalPrefs {
            override fun getString(key: String, default: String?) = default
            override fun getBoolean(key: String, default: Boolean) = default
            override fun setString(key: String, value: String) = Unit
            override fun setBoolean(key: String, value: Boolean) = Unit
            override fun remove(key: String) = Unit
        }
        val options = object : net.bible.android.view.activity.settings.DictionaryOptionsProvider {
            override val greekDictOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
            override val hebrewDictOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
            override val greekMorphOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
            override val wordLookupDictOptions = emptyList<net.bible.sharedcore.settings.DictOption>()
        }
        val flags = java.lang.reflect.Proxy.newProxyInstance(
            net.bible.sharedcore.settings.SettingsPlatformFlags::class.java.classLoader,
            arrayOf(net.bible.sharedcore.settings.SettingsPlatformFlags::class.java),
        ) { _, method, _ -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null } as net.bible.sharedcore.settings.SettingsPlatformFlags
        var opened = 0
        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined)
        val pref = net.bible.android.control.activity.SystemNotificationsPreference(prefs, true)
        val service = net.bible.android.view.activity.settings.AppSettingsServiceImpl(
            options, prefs, local, net.bible.sharedui.strings.keyed.KeyedCoreStrings(
                net.bible.sharedui.assembly.hostStringTable("en")
            ), flags, null, pref,
            net.bible.sharedcore.settings.NotificationsDenied { true },
            net.bible.sharedcore.settings.NotificationSettingsLink { opened++ }, scope,
        )
        try {
            setContent {
                val nav = rememberNavController()
                val deps = androidx.compose.runtime.remember {
                    host(serviceProvider = { service }).navDeps(nav, NavResultChannel<ReadingProgressResult> { })
                }
                androidx.compose.runtime.CompositionLocalProvider(net.bible.sharedui.strings.LocalStrings provides strings) {
                    net.bible.sharedui.theme.AbTheme(darkTheme = false, disableAnimations = true) {
                        net.bible.sharedui.components.AbNavHost(nav, startDestination = net.bible.sharedcore.nav.NavRoutes.SETTINGS) {
                            settingsNavGraph(nav, deps)
                        }
                    }
                }
            }
            onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-system_notifications_denied"))
            onNodeWithTag("settings-row-system_notifications_denied").performClick()
            assertEquals(1, opened)
            assertTrue(pref.enabled.value)
        } finally { scope.coroutineContext[Job]?.cancel() }
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
