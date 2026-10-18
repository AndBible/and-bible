/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.reading.host

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import net.bible.service.history.HistoryPlatform
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.DrawerGroup
import net.bible.sharedcore.reading.DrawerItem
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/** Composition tests of the common [ReadingHost]: what it lays out from its deps and its [ReadingHostState]. */
@OptIn(ExperimentalTestApi::class)
class ReadingHostCompositionTest {
    private class MapSettings : AppSettings {
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private class NoPlatform : HistoryPlatform {
        override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? = null
        override fun isOnReadingScreen() = true
        override fun leaveCurrentScreen() = Unit
    }

    /** [DocumentControl] has nine collaborators and is only read by the document tabs; skip its constructor. */
    private fun allocateDocumentControl(): DocumentControl {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, DocumentControl::class.java) as DocumentControl
    }

    private fun newState(): ReadingHostState {
        val windowControl = WindowControl()
        return ReadingHostState(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            settings = MapSettings(),
            windowControl = windowControl,
            historyManager = HistoryManager(windowControl, NoPlatform()),
            documentControl = allocateDocumentControl(),
        )
    }

    private fun snapshot(id: String) = WindowSnapshot(
        id = id, state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true, isPinMode = false,
        isSynchronised = false, syncGroup = 0, isLinksWindow = false,
    )

    private fun noopCommands(): WindowCommands = Proxy.newProxyInstance(
        WindowCommands::class.java.classLoader, arrayOf(WindowCommands::class.java),
    ) { _, _, _ -> null } as WindowCommands

    private fun controller(vararg windowIds: String): ReadingViewController {
        val layout = WindowLayoutState(
            windows = windowIds.map(::snapshot),
            activeWindowId = windowIds.first(),
            maximizedWindowId = null,
            reverseSplitMode = false,
            restoreButtonsVisible = true,
        )
        val service = object : WindowStateService { override val layout = MutableStateFlow(layout) }
        return ReadingViewController(service, noopCommands())
    }

    private fun deps(
        state: ReadingHostState,
        controller: ReadingViewController,
        drawerMenu: DrawerMenuState = DrawerMenuState.EMPTY,
        quickSheetContent: @Composable (net.bible.sharedcore.reading.ReadingQuickSheet) -> Unit = {},
    ): ReadingHostDeps {
        val icon = ColorPainter(Color.Gray)
        return ReadingHostDeps(
            state = state,
            controller = controller,
            toolbarCallbacks = ReadingToolbarCallbacks(
                onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
                onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
                onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
                onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
            ),
            toolbarIcons = {
                ReadingToolbarIcons(icon, icon, icon, icon, icon, icon, icon, icon, icon)
            },
            drawerMenu = mutableStateOf(drawerMenu),
            quickSheetContent = quickSheetContent,
        )
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false) { content() }
        }
    }

    @Test
    fun twoWindowsRenderTwoPanes() = runComposeUiTest {
        val rendered = mutableListOf<String>()
        val state = newState()
        val deps = deps(state, controller("w1", "w2"))
        setContent {
            Themed {
                CompositionLocalProvider(
                    LocalReadingPane provides { windowId, modifier ->
                        rendered += windowId
                        Box(modifier.testTag("pane-$windowId"))
                    },
                ) { ReadingHost(deps) }
            }
        }
        onNodeWithTag("pane-w1").assertExists()
        onNodeWithTag("pane-w2").assertExists()
        assertEquals(setOf("w1", "w2"), rendered.toSet())
    }

    @Test
    fun quickSheetShowsWhenStateOpensIt() = runComposeUiTest {
        val state = newState()
        val deps = deps(state, controller("w1"), quickSheetContent = { Text("quick-sheet-body") })
        setContent { Themed { ReadingHost(deps) } }
        onNodeWithTag(ReadingQuickSheetTag).assertDoesNotExist()

        state.showHistorySheet()
        waitForIdle()

        onNodeWithTag(ReadingQuickSheetTag).assertExists()
        onNodeWithText("quick-sheet-body").assertExists()
        assertEquals("reading-quick-sheet", ReadingQuickSheetTag)

        state.closeQuickSheet()
        waitForIdle()
        onNodeWithTag(ReadingQuickSheetTag).assertDoesNotExist()
    }

    @Test
    fun drawerOpensFromState() = runComposeUiTest {
        val state = newState()
        val menu = DrawerMenuState(
            appName = "AndBible",
            groups = listOf(DrawerGroup(null, listOf(DrawerItem("settings", "Drawer Settings Row", "settings")))),
            versionText = "1.0",
        )
        val deps = deps(state, controller("w1"), drawerMenu = menu)
        setContent { Themed { ReadingHost(deps) } }
        // M3 composes the drawer sheet off-screen while closed, so "closed" means not displayed.
        onNodeWithText("Drawer Settings Row").assertIsNotDisplayed()

        state.openDrawer()
        waitForIdle()

        onNodeWithText("Drawer Settings Row").assertIsDisplayed()

        state.closeDrawer()
        waitForIdle()

        onNodeWithText("Drawer Settings Row").assertIsNotDisplayed()
    }
}
