package net.bible.sharedui.app.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.DesktopBetaUpdateSetting
import net.bible.sharedui.settings.DesktopUpdateCheckSetting
import net.bible.sharedui.settings.DesktopUpdateInterval
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
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
                onNodeWithText("Daily").assertExists().performClick()
                runOnIdle { assertEquals(DesktopUpdateInterval.DAILY, setting.interval) }
                onNodeWithText("Check for updates").performClick()
                onNodeWithText("Never").performClick()
                runOnIdle { assertEquals(DesktopUpdateInterval.NEVER, setting.interval) }
            }
        } finally {
            boundKoin.close()
        }
    }

    @Test fun betaRowOnlyWithBindingAndTogglesIt() {
        val offer = TestBetaSetting()
        val koin = koinApplication { modules(module {
            single<DesktopUpdateCheckSetting> { TestUpdateSetting() }
            single<DesktopBetaUpdateSetting> { offer }
        }) }
        val withoutBeta = koinApplication { modules(module { single<DesktopUpdateCheckSetting> { TestUpdateSetting() } }) }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = withoutBeta.koin)
                        }
                    }
                }
                onNodeWithText("Offer public beta updates").assertDoesNotExist()
            }
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false) {
                            AppSettingsScreen(SettingsScreenState("Settings", emptyList()), null, { _, _ -> }, { _, _ -> },
                                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, "Reset", settingsKoin = koin.koin)
                        }
                    }
                }
                onNodeWithText("Offer public beta updates").assertExists().assertIsOff()
                onNodeWithText("Offer public beta updates").performClick()
                runOnIdle { assertTrue(offer.enabled) }
            }
        } finally {
            koin.close(); withoutBeta.close()
        }
    }

    private class TestBetaSetting : DesktopBetaUpdateSetting {
        override var enabled = false
            private set
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun observeChanges(onChange: () -> Unit): () -> Unit = {}
    }

    private class TestUpdateSetting : DesktopUpdateCheckSetting {
        override var interval = DesktopUpdateInterval.WEEKLY
            private set
        private var listener: (() -> Unit)? = null
        override fun setInterval(interval: DesktopUpdateInterval) { this.interval = interval; listener?.invoke() }
        override fun observeChanges(onChange: () -> Unit): () -> Unit {
            listener = onChange
            return { listener = null }
        }
    }
}
