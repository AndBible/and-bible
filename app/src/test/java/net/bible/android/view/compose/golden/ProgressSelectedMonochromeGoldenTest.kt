package net.bible.android.view.compose.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.progress.*
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.progress.*
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual interaction captures: no preselected stand-in and no fabricated host navigation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ProgressSelectedMonochromeGoldenTest {
    @get:Rule val rule = createComposeRule()

    private fun calendar(dark: Boolean) {
        val heat = CalendarHeatmapLayout.assemble(
            CalendarSkeleton(listOf(DaySlot(0, 0, 0L)), emptyList(), 1, List(7) { "" }),
            mapOf(0L to 17),
        )
        rule.setContent { ProvideAppLocals { AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { AbCalendarHeatmap(heat) }
            }
        } } }
        rule.onNodeWithContentDescription("Activity count: 17").performClick()
        rule.onRoot().captureRoboImage("src/test/roborazzi/ProgressDetail_calendarSelected_${if (dark) "mono_dark" else "mono"}.png")
    }

    private fun book(dark: Boolean) {
        rule.setContent { ProvideAppLocals { AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            BookHeatGrid(listOf(BookHeat("Gen", "Gen", false, 0.125f, false)),
                colors = { bookProgressColors(it.readPercent, 1f) }, onClick = {})
            } }
        } } }
        rule.onNodeWithContentDescription("Gen: 12.5%").performClick()
        rule.onRoot().captureRoboImage("src/test/roborazzi/ProgressDetail_bookSelected_${if (dark) "mono_dark" else "mono"}.png")
    }

    private fun chapter(dark: Boolean) {
        rule.setContent { ProvideAppLocals { AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            ChapterHeatGrid(listOf(ChapterHeat(3, 17, 4)), colors = { countHeatColors(it.count, 17) }, onClick = {})
            } }
        } } }
        // Count is readable before a production click navigates away.
        rule.onRoot().captureRoboImage("src/test/roborazzi/ProgressDetail_chapterBeforeNavigation_${if (dark) "mono_dark" else "mono"}.png")
    }

    @Test fun calendarLight() = calendar(false)
    @Test fun calendarDark() = calendar(true)
    @Test fun bookLight() = book(false)
    @Test fun bookDark() = book(true)
    @Test fun chapterLight() = chapter(false)
    @Test fun chapterDark() = chapter(true)
}
