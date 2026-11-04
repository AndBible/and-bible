package net.bible.sharedui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import net.bible.android.view.activity.settings.AppSettingsServiceImpl
import net.bible.android.view.activity.settings.DictionaryOptionsProvider
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.*
import net.bible.sharedui.app.settings.appSettingsLabels
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.components.LocalBarFontScaleCap
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SettingsAx5MigrationTest {
    private inline fun <reified T> defaults(): T = java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, args -> when {
        method.returnType == List::class.java -> emptyList<Any>()
        args != null && args.size >= 2 -> args[1]
        method.returnType == Boolean::class.javaPrimitiveType -> false
        else -> null
    } } as T

    @Test fun allPreviouslyWalkedApplicationSettingsAreReachableAtAx5() = runComposeUiTest {
        val strings = iosStrings("en")
        val service = AppSettingsServiceImpl(
            defaults<DictionaryOptionsProvider>(), defaults<AppSettings>(), defaults<DeviceLocalPrefs>(),
            KeyedCoreStrings(hostStringTable("en")), defaults<SettingsPlatformFlags>(),
        )
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val controller = AppSettingsController(service, scope, strings.appSettingsLabels(), {})
        try {
            setContent {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(390.dp).height(844.dp)) {
                CompositionLocalProvider(
                    LocalStrings provides strings,
                    LocalDensity provides Density(1f, 53f / 17f),
                    LocalBarFontScaleCap provides 1.35f,
                ) {
                    AbTheme(darkTheme = false, disableAnimations = true) {
                        AppSettingsScreen(
                            controller.state.value, {}, controller::onSwitch, controller::onListChoice,
                            controller::onTextInput, controller::onSliderChange, controller::onMultiSelectChange,
                            {}, {}, strings.resetSettings,
                        )
                    }
                }
                }
            }
            val keys = listOf(
                "navigate_to_verse_pref", "open_links_in_special_window_pref", "screen_keep_on_pref",
                "double_tap_to_fullscreen", "auto_fullscreen_pref", "toolbar_button_actions", "bible_view_swipe_mode",
                "disable_two_step_bookmarking", "volume_keys_scroll", "night_mode_pref3", "global_text_display_settings",
                "locale_pref", "font_size_multiplier", "display_color_mode", "disable_animations",
                "sync_settings_shortcut", "ai_settings_shortcut", "reading_progress_settings_shortcut",
            )
            for (key in keys) {
                onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-$key"))
                onNodeWithTag("settings-row-$key").assertIsDisplayed()
                val bounds = onNodeWithTag("settings-row-$key").fetchSemanticsNode().boundsInRoot
                kotlin.test.assertTrue(bounds.left >= 0f && bounds.right <= 391f, "$key must remain inside phone width: $bounds")
            }
        } finally { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }
}
