package net.bible.sharedui.reading

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.reading.DrawerGroup
import net.bible.sharedcore.reading.DrawerItem
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R3: the drawer row keeps Android's fixed single-line 48dp height by default and only grows with large text where
 * the iOS host provides [LocalDrawerRowsGrowWithText] (Dynamic Type), so no Android golden can move.
 */
@OptIn(ExperimentalTestApi::class)
class ReadingDrawerRowScaleTest {
    private val label = "Backup & Restore of all my documents and settings"

    private fun rowHeight(grow: Boolean, fontScale: Float): Dp {
        var height = 0.dp
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = fontScale),
                    LocalDrawerRowsGrowWithText provides grow,
                ) {
                    AbTheme(darkTheme = false) {
                        ReadingDrawerContent(
                            state = DrawerMenuState(
                                appName = "AndBible",
                                groups = listOf(DrawerGroup(null, listOf(DrawerItem("backup", label, "k")))),
                                versionText = "v",
                            ),
                            icon = { null },
                            onItemClick = {},
                        )
                    }
                }
            }
            height = onNode(hasClickAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true)
                .getBoundsInRoot().let { it.bottom - it.top }
        }
        return height
    }

    @Test
    fun defaultRowIsFixed48dpEvenWithHugeTextAndALongLabel() {
        assertEquals(48.dp, rowHeight(grow = false, fontScale = 3f))
    }

    @Test
    fun rowAtNormalScaleIs48dpByDefaultAndNeverShorterWhenGrowing() {
        assertEquals(48.dp, rowHeight(grow = false, fontScale = 1f))
        // The long label is allowed to wrap at 1x in grow mode, so use a short check on the default mode only here.
        assertTrue(rowHeight(grow = true, fontScale = 1f) >= 48.dp)
    }

    @Test
    fun growingRowWrapsAndGrowsWithLargeTextAndALongLabel() {
        val height = rowHeight(grow = true, fontScale = 3f)
        assertTrue(height > 48.dp, "row should grow past 48dp at font scale 3 with a long label, was $height")
    }
}
