package net.bible.sharedui.host.reading

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.sharedui.HostBackDispatcher
import net.bible.sharedui.PassThroughBackHandler
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.reading.ReadingShortcutInput
import net.bible.sharedui.theme.LocalDisableAnimations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The headless scene has no OS key fallback; model Compose's post-editor Esc input at the root. */
@OptIn(ExperimentalTestApi::class)
class HardwareEscapeBackTest {
    private fun Modifier.hostEscapeInput() = onKeyEvent {
        it.key == Key.Escape && it.type == KeyEventType.KeyDown && HostBackDispatcher.back()
    }

    @Test
    fun sheetThenFindThenPushedScreenThenPlainReading() = runComposeUiTest {
        var findOpen by mutableStateOf(true)
        var sheetOpen by mutableStateOf(true)
        val log = mutableListOf<String>()
        val focus = FocusRequester()
        val chain = ReadingBackChain(
            isDrawerOpen = { false }, closeDrawer = {},
            isSearchSheetVisible = { false }, closeSearchSheet = {},
            isSearchBarOpen = { findOpen }, closeSearchBar = { findOpen = false; log += "find" },
        )
        lateinit var navigate: () -> Unit
        lateinit var route: () -> String?
        setContent {
            HostBackDispatcher.Attach()
            val nav = rememberNavController()
            navigate = { nav.navigate("pushed") }
            route = { nav.currentDestination?.route }
            CompositionLocalProvider(LocalDisableAnimations provides true) {
                ReadingShortcutInput(onKeyEvent = { false }) { modifier ->
                    Box(modifier.hostEscapeInput().size(100.dp).testTag("keys").focusRequester(focus).focusable()) {
                        AbNavHost(nav, "reading") {
                            composable("reading") {
                                PassThroughBackHandler(true) { chain.onBack(it) }
                                if (sheetOpen) PlatformBackHandler(true) { sheetOpen = false; log += "sheet" }
                            }
                            composable("pushed") { Text("Pushed") }
                        }
                    }
                }
            }
        }
        runOnIdle { focus.requestFocus() }
        onNodeWithTag("keys").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        assertEquals(listOf("sheet"), log)
        assertTrue(findOpen)
        onNodeWithTag("keys").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        assertEquals(listOf("sheet", "find"), log)
        assertFalse(findOpen)
        runOnIdle { navigate() }
        waitForIdle()
        assertEquals("pushed", route())
        onNodeWithTag("keys").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        assertEquals("reading", route())
        onNodeWithTag("keys").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        assertEquals("reading", route(), "plain reading must not navigate away")
        assertEquals(listOf("sheet", "find"), log)
    }

    @Test
    fun dialogAndPopupCloseBeforeFind() = runComposeUiTest {
        var findOpen by mutableStateOf(true)
        var dialogOpen by mutableStateOf(true)
        var popupOpen by mutableStateOf(false)
        val focus = FocusRequester()
        val chain = ReadingBackChain(
            isDrawerOpen = { false }, closeDrawer = {},
            isSearchSheetVisible = { false }, closeSearchSheet = {},
            isSearchBarOpen = { findOpen }, closeSearchBar = { findOpen = false },
        )
        setContent {
            MaterialTheme {
                Box(Modifier.hostEscapeInput().size(100.dp).testTag("keys").focusRequester(focus).focusable()) {
                    PassThroughBackHandler(true) { chain.onBack(it) }
                    if (dialogOpen) AlertDialog(
                        onDismissRequest = { dialogOpen = false },
                        confirmButton = {
                            HostBackDispatcher.Attach()
                            TextButton({}, Modifier.testTag("dialog")) { Text("OK") }
                        },
                    )
                    if (popupOpen) Popup(
                        onDismissRequest = { popupOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        HostBackDispatcher.Attach()
                        Box(Modifier.size(40.dp).testTag("popup").focusable())
                    }
                }
            }
        }
        // Dialogs are separate scenes: the platform back input targets their topmost dispatcher.
        assertTrue(HostBackDispatcher.back())
        waitForIdle()
        assertFalse(dialogOpen)
        assertTrue(findOpen)
        runOnIdle { popupOpen = true }
        waitForIdle()
        assertTrue(HostBackDispatcher.back())
        waitForIdle()
        assertFalse(popupOpen)
        assertTrue(findOpen)
        runOnIdle { focus.requestFocus() }
        onNodeWithTag("keys").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        assertFalse(findOpen)
    }

    @Test
    fun unconsumedEscapeFromFindFieldReachesBack() = runComposeUiTest {
        var findOpen by mutableStateOf(true)
        val chain = ReadingBackChain(
            isDrawerOpen = { false }, closeDrawer = {},
            isSearchSheetVisible = { false }, closeSearchSheet = {},
            isSearchBarOpen = { findOpen }, closeSearchBar = { findOpen = false },
        )
        setContent {
            ReadingShortcutInput(onKeyEvent = { false }) { modifier ->
                Box(modifier.hostEscapeInput()) {
                    PassThroughBackHandler(true) { chain.onBack(it) }
                    if (findOpen) BasicTextField("query", {}, Modifier.testTag("find"))
                }
            }
        }
        onNodeWithTag("find").performClick()
        onNodeWithTag("find").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        assertFalse(findOpen)
    }

    @Test
    fun focusedEditorCanConsumeEscapeBeforeBack() = runComposeUiTest {
        var findOpen by mutableStateOf(true)
        var editorEscapes = 0
        setContent {
            ReadingShortcutInput(onKeyEvent = { false }) { modifier ->
                Box(modifier.hostEscapeInput()) {
                    PassThroughBackHandler(true) { findOpen = false }
                    BasicTextField("query", {}, Modifier.testTag("editor").onPreviewKeyEvent {
                        if (it.key == Key.Escape) { editorEscapes++; true } else false
                    })
                }
            }
        }
        onNodeWithTag("editor").performClick()
        onNodeWithTag("editor").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        waitForIdle()
        assertTrue(editorEscapes > 0)
        assertTrue(findOpen)
    }
}
