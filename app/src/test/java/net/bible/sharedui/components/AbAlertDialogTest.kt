package net.bible.sharedui.components

import android.view.Window
import android.view.WindowManager
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbAlertDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `dialog dim clears in mono and restores on runtime exit with null text`() {
        val mode = mutableStateOf(DisplayColorMode.NORMAL)
        var window: Window? = null
        rule.setContent {
            AbTheme(darkTheme = false, colorMode = mode.value) {
                AbAlertDialog(onDismissRequest = {}, confirmButton = {
                    val view = LocalView.current
                    SideEffect {
                        window = generateSequence(view.parent) { it.parent }
                            .filterIsInstance<DialogWindowProvider>().first().window
                    }
                    Text("Confirm")
                })
            }
        }
        rule.waitForIdle()
        val original = window!!
        val originalDim = original.attributes.dimAmount
        assertTrue(original.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        rule.runOnIdle { mode.value = DisplayColorMode.MONOCHROME }
        rule.waitForIdle()
        val monoWindow = window!!
        assertEquals(0, monoWindow.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        rule.runOnIdle { mode.value = DisplayColorMode.NORMAL }
        rule.waitForIdle()
        assertTrue(window!!.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        assertEquals(originalDim, window!!.attributes.dimAmount)
        assertTrue(monoWindow.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        for (oldMode in listOf(DisplayColorMode.BW, DisplayColorMode.COLOR_EINK)) {
            rule.runOnIdle { mode.value = oldMode }
            rule.waitForIdle()
            assertTrue(window!!.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        }
    }

    @Test fun `old modes with null text retain Material dialog layout`() {
        val mode = mutableStateOf(DisplayColorMode.NORMAL)
        val wrapped = mutableStateOf(false)
        var size = IntSize.Zero
        rule.setContent {
            AbTheme(darkTheme = false, colorMode = mode.value) {
                val modifier = Modifier.onGloballyPositioned { size = it.size }
                if (wrapped.value) {
                    AbAlertDialog(onDismissRequest = {}, confirmButton = { Text("Confirm") },
                        title = { Text("Title") }, modifier = modifier)
                } else {
                    androidx.compose.material3.AlertDialog(onDismissRequest = {},
                        confirmButton = { Text("Confirm") }, title = { Text("Title") }, modifier = modifier)
                }
            }
        }
        for (oldMode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.BW, DisplayColorMode.COLOR_EINK)) {
            rule.runOnIdle { wrapped.value = false; mode.value = oldMode }
            rule.waitForIdle()
            val defaultSize = size
            assertTrue(defaultSize.height > 0)
            rule.runOnIdle { wrapped.value = true }
            rule.waitForIdle()
            assertEquals("null text must not introduce M3 text padding in $oldMode", defaultSize, size)
        }
    }

    @Test fun `dim override restores original flags on disposal without changing dim amount`() {
        val override = mutableStateOf(false)
        var window: Window? = null
        rule.setContent {
            AbAlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                val view = LocalView.current
                SideEffect {
                    window = generateSequence(view.parent) { it.parent }
                        .filterIsInstance<DialogWindowProvider>().first().window
                }
                if (override.value) NoDialogDim()
                Text("Content")
            })
        }
        rule.waitForIdle()
        val original = window!!
        rule.runOnIdle { original.setDimAmount(0.37f); override.value = true }
        rule.waitForIdle()
        assertSame(original, window)
        assertEquals(0, original.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertEquals(0.37f, original.attributes.dimAmount)
        rule.runOnIdle { override.value = false }
        rule.waitForIdle()
        assertSame(original, window)
        assertTrue(original.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        assertEquals(0.37f, original.attributes.dimAmount)
        rule.runOnIdle { original.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); override.value = true }
        rule.waitForIdle()
        rule.runOnIdle { override.value = false }
        rule.waitForIdle()
        assertEquals(0, original.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    @Test fun `mono dialog with text clears the dialog window rather than activity window`() {
        var window: Window? = null
        rule.setContent {
            AbTheme(darkTheme = true, colorMode = DisplayColorMode.MONOCHROME) {
                AbAlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                    val view = LocalView.current
                    SideEffect {
                        window = generateSequence(view.parent) { it.parent }
                            .filterIsInstance<DialogWindowProvider>().first().window
                    }
                    Text("Content")
                })
            }
        }
        rule.waitForIdle()
        assertNotNull(window)
        assertEquals(0, window!!.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
}
