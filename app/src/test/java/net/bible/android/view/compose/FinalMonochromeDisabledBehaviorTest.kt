package net.bible.android.view.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.*
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class FinalMonochromeDisabledBehaviorTest {
    @get:Rule val rule = createComposeRule()
    @Test(timeout = 60000) fun disabledRowsAndDropdownBlockPhysicalClicksBothThemes() {
        var calls = 0
        val dark = mutableStateOf(false)
        rule.setContent { ProvideAppLocals { AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME) {
            Column {
                AbSettingsRow("Credentials", "Signed in", false, { calls++ })
                AbSwitchRow("Dependent", true, { calls++ }, enabled = false)
                AbDropdownField("Category", "First", listOf("First", "Second"), { it }, { calls++ }, enabled = false)
            }
        } } }
        for (theme in listOf(false, true)) {
            rule.runOnIdle { dark.value = theme }
            for (label in listOf("Credentials", "Dependent", "First")) {
                rule.onNodeWithText(label).assertIsNotEnabled().performTouchInput { click() }
            }
            rule.onNode(isPopup()).assertDoesNotExist()
            assertEquals(0, calls)
        }
    }
    @Test(timeout = 60000) fun loadingOverlayBlocksUnderlyingButtonWithoutRemovingItBothThemes() {
        var calls = 0
        val dark = mutableStateOf(false)
        val loading = mutableStateOf(true)
        rule.setContent { ProvideAppLocals { AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME) {
            Box(Modifier.fillMaxSize()) {
                Button(onClick = { calls++ }) { Text("Underlying action") }
                if (loading.value) AbLoadingOverlay()
            }
        } } }
        for (theme in listOf(false, true)) {
            rule.runOnIdle { dark.value = theme; loading.value = true }
            rule.onNodeWithText("Underlying action").assertExists().performTouchInput { click() }
            assertEquals(if (theme) 1 else 0, calls)
            rule.runOnIdle { loading.value = false }
            rule.onNodeWithText("Underlying action").performTouchInput { click() }
            assertEquals(if (theme) 2 else 1, calls)
        }
    }
}
