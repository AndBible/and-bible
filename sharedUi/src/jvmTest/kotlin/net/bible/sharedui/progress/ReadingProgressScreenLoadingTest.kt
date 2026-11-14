package net.bible.sharedui.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.progress.CalendarHeatmap
import net.bible.sharedcore.progress.ReadingProgressModel
import net.bible.sharedcore.progress.ReadingSummary
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** #4096: before the first load the screen must not show the placeholder zeros. */
@OptIn(ExperimentalTestApi::class)
class ReadingProgressScreenLoadingTest {
    private fun model(loaded: Boolean, chapters: Int) = ReadingProgressModel(
        tab = ReadingTab.READING,
        summary = ReadingSummary(chapters, 1, 14, 1.4f),
        otBooks = emptyList(), ntBooks = emptyList(),
        bookPercentScaleMax = 0f, bookPercentScaleSteps = emptyList(),
        chapterDetail = null,
        calendar = CalendarHeatmap(emptyList(), emptyList(), 0, emptyList()),
        cycle = 1, latestCycle = 1, loaded = loaded,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(m: ReadingProgressModel, loading: Boolean) =
        setContent {
            val strings = iosStrings("en")
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    ReadingProgressScreen(
                        model = m, loading = loading, onUp = {}, onSelectTab = {}, onPrevCycle = {},
                        onNextCycle = {}, onNewCycle = {}, onBookClick = {}, onBookLongClick = {},
                        onChapterClick = {}, onChapterLongClick = {}, onCalendarDayClick = {},
                        onOpenSettings = {}, onShowHelp = {},
                    )
                }
            }
        }

    private val chaptersLabel = iosStrings("en").readingProgressChaptersRead

    @Test fun unloadedModelShowsNoSummaryNumbers() = runComposeUiTest {
        mainClock.autoAdvance = false // spinner never idles
        show(model(loaded = false, chapters = 0), loading = true)
        onNodeWithText(chaptersLabel).assertDoesNotExist()
    }

    @Test fun loadedModelShowsSummary() = runComposeUiTest {
        mainClock.autoAdvance = false
        show(model(loaded = true, chapters = 17), loading = false)
        onNodeWithText(chaptersLabel).assertExists()
        onNodeWithText("17").assertExists()
    }
}
