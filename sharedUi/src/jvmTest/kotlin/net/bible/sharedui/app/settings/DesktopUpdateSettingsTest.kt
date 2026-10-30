package net.bible.sharedui.app.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.DesktopUpdateCheckSetting
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopUpdateSettingsTest {
    @Test fun updateRowOnlyWithBinding() {
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                    AbTheme(darkTheme = false) {
                        AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                            { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset")
                    }
                }
            }
            onNodeWithText("Check for updates").assertDoesNotExist()
        }
        val setting = TestUpdateSetting()
        startKoin { modules(module { single<DesktopUpdateCheckSetting> { setting } }) }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset")
                        }
                    }
                }
                onNodeWithText("Check for updates").assertExists().performClick()
                runOnIdle { assertTrue(setting.enabled) }
                onNodeWithText("Check for updates").performClick()
                runOnIdle { assertFalse(setting.enabled) }
                runOnIdle { setting.setEnabled(true) }
                onNodeWithText("Check for updates").assertIsOn()
            }
        } finally {
            stopKoin()
        }
    }

    private class TestUpdateSetting : DesktopUpdateCheckSetting {
        override var enabled = false
            private set
        private var listener: (() -> Unit)? = null
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled; listener?.invoke() }
        override fun observeChanges(onChange: () -> Unit): () -> Unit {
            listener = onChange
            return { listener = null }
        }
    }
}
