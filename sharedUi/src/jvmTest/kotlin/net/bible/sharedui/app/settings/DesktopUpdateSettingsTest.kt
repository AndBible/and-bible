package net.bible.sharedui.app.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.DesktopUpdateCheckSetting
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopUpdateSettingsTest {
    @Test fun updateRowOnlyWithBinding() {
        val emptyKoin = koinApplication {}
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = emptyKoin.koin)
                        }
                    }
                }
                onNodeWithText("Check for updates").assertDoesNotExist()
            }
        } finally {
            emptyKoin.close()
        }
        val setting = TestUpdateSetting()
        val boundKoin = koinApplication { modules(module { single<DesktopUpdateCheckSetting> { setting } }) }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = boundKoin.koin)
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
            boundKoin.close()
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
