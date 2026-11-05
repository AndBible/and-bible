package net.bible.sharedui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.LocalDisableAnimations

/** Characterizes the real rail/menu path at phone and iPad widths, independently of the installed Bible. */
@OptIn(ExperimentalTestApi::class)
class WindowTabBarSemanticsTest {
    @Test fun compactRailExposesBothTabsAndTheirNewWindowMenu() = checkRail(393, "KJV")

    @Test fun iPadRailExposesBothTabsAndTheirNewWindowMenu() = checkRail(820, "NASB")

    private fun checkRail(width: Int, document: String) = runSkikoComposeUiTest(
        size = Size(width.toFloat(), 1180f), density = Density(1f),
    ) {
        val windows = listOf("first", "second").map {
            WindowSnapshot(it, WindowStateValue.VISIBLE, 1f, true, true, false, 0, false)
        }
        var newWindowSource: String? = null
        setContent {
            AbTheme {
                CompositionLocalProvider(LocalStrings provides iosStrings("en"), LocalDisableAnimations provides true) {
                    var menuWindowId by remember { mutableStateOf<String?>(null) }
                    SplitContent(
                        layout = WindowLayoutState(windows, "first", null, false, true),
                        onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                        pane = { Box(Modifier.fillMaxSize()) },
                        railOverlay = {
                            WindowTabBar(
                                model = buildWindowTabBar(WindowLayoutState(windows, "first", null, false, true)),
                                onRestore = {}, onWindowLongPress = { menuWindowId = it },
                                onAddWindow = {}, onUnMaximise = {}, onToggleCollapse = {},
                                windowLabel = { document }, windowTopLabel = { it.id },
                                menuWindowId = menuWindowId,
                                menuItems = listOf(WindowPaneMenuItem("new", "New window")),
                                onMenuItemClick = { id, _ -> newWindowSource = id; menuWindowId = null },
                                onMenuDismiss = { menuWindowId = null },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        },
                    )
                }
            }
        }
        // With two windows the '+' is intentionally replaced by a collapse switch and two labelled tabs.
        onNodeWithContentDescription("New window").assertDoesNotExist()
        for (id in listOf("first", "second")) {
            onNodeWithText(id).assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performSemanticsAction(SemanticsActions.OnLongClick)
            onNodeWithText("New window").assertIsDisplayed().performClick()
            assertEquals(id, newWindowSource, "the accessible menu adds from the tab that opened it")
        }
    }
}
