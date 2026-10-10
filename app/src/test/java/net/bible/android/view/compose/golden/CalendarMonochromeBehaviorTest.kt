package net.bible.android.view.compose.golden

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.progress.AbCalendarHeatmap
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedcore.progress.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CalendarMonochromeBehaviorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun chapterIdentityAndCountAreExplicit() {
        rule.setContent { ProvideAppLocals {
            AbTheme(colorMode = DisplayColorMode.MONOCHROME) {
                net.bible.sharedui.progress.ChapterHeatGrid(
                    listOf(ChapterHeat(3, 17, 4)),
                    colors = { net.bible.sharedui.progress.countHeatColors(it.count, 17) },
                    onClick = {},
                )
            }
        } }
        rule.onNodeWithContentDescription("3: Count: 17").performClick()
        rule.onNodeWithText("3\nCount: 17").assertExists()
    }

    @Test fun exactCountSurvivesThemeToggleAndClickStillReachesHost() {
        val dark = mutableStateOf(false)
        val clicks = mutableListOf<Long>()
        val heat = CalendarHeatmapLayout.assemble(
            CalendarSkeleton(listOf(DaySlot(0, 0, 0L)), emptyList(), 1, List(7) { "" }),
            mapOf(0L to 17),
        )
        rule.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME) {
                    AbCalendarHeatmap(heat, onDayClick = { clicks.add(it) })
                }
            }
        }
        rule.onNodeWithContentDescription("Activity count: 17").performClick()
        rule.onNodeWithText("Activity count: 17").assertExists()
        assertEquals(listOf(0L), clicks)
        rule.runOnIdle { dark.value = true }
        rule.onNodeWithText("Activity count: 17").assertExists()
        rule.onNodeWithContentDescription("Activity count: 17").performClick()
        assertEquals(listOf(0L, 0L), clicks)
    }
}
