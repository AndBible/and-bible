package net.bible.sharedui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import org.jetbrains.skia.Image
import java.io.File
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.progress.BookHeat
import net.bible.sharedcore.progress.CalendarHeatmap
import net.bible.sharedcore.progress.ChapterDetail
import net.bible.sharedcore.progress.ChapterHeat
import net.bible.sharedcore.progress.MemorizeModel
import net.bible.sharedcore.progress.ReadingProgressModel
import net.bible.sharedcore.progress.ReadingSummary
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exercises the real book buttons, including taps after the user has already scrolled. */
@OptIn(ExperimentalTestApi::class)
class ProgressDetailScrollTest {
    private val books = (1..39).map { BookHeat("book$it", "B$it", false, 0f, false) }
    private val calendar = CalendarHeatmap(emptyList(), emptyList(), 0, emptyList())
    private fun detail(id: String, chapters: Int) = ChapterDetail(
        id, "Detail $id", (1..chapters).map { ChapterHeat(it, 0, 0) }, 0, listOf(0),
    )

    private fun ComposeUiTest.show(
        tab: ReadingTab, animations: Boolean, restored: Boolean = false, chapters: Int = 150,
        loadDelayMillis: Long = 0,
    ) {
        var selected by mutableStateOf(if (restored) detail("book1", chapters) else null)
        setContent {
            val scope = rememberCoroutineScope()
            val selectBook: (String) -> Unit = { book ->
                scope.launch {
                    delay(loadDelayMillis)
                    selected = detail(book, chapters)
                }
            }
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = !animations) {
                    Box(Modifier.size(360.dp, 520.dp)) {
                        ReadingProgressScreen(
                            model = ReadingProgressModel(
                                tab, ReadingSummary(0, 0, 0, 0f), books, emptyList(), 0f, listOf(0),
                                selected, calendar, 1, 1,
                            ),
                            loading = false, onUp = {}, onSelectTab = {}, onPrevCycle = {},
                            onNextCycle = {}, onNewCycle = {}, onBookClick = selectBook,
                            onBookLongClick = {}, onChapterClick = {}, onChapterLongClick = {},
                            onCalendarDayClick = {}, onOpenSettings = {}, onShowHelp = {},
                            memorizeTabContent = {
                                MemorizeTabBody(
                                    memorize = MemorizeModel(
                                        true, 0, 0, 0, 0, 0f, books, emptyList(), selected, calendar,
                                        emptyList(), 0, 0, emptyList(), 0, 0,
                                    ),
                                    onSetOverview = {}, onBookClick = selectBook,
                                    onChapterClick = {}, onCalendarDayClick = {}, onPassageTap = { _, _ -> },
                                    onPassageUnmark = {}, onTargetTap = { _, _ -> }, onTargetRemove = {},
                                    onShowMorePassages = {}, onShowMoreTargets = {},
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.assertHeadingAtTop(id: String) {
        val viewport = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNode().boundsInRoot
        val heading = onNodeWithText("Detail $id").fetchSemanticsNode().boundsInRoot
        onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(heading.top >= viewport.top - 1f && heading.top <= viewport.top + 20f,
            "Heading should be near viewport top: heading=$heading viewport=$viewport")
    }

    private fun checkBookTaps(tab: ReadingTab, animations: Boolean) = runComposeUiTest {
        show(tab, animations)
        onNodeWithText("B1").performScrollTo().performClick()
        System.getenv("PROGRESS_SCROLL_EVIDENCE_DIR")?.let { directory ->
            val file = File(directory, "${tab.name.lowercase()}-${if (animations) "animated" else "instant"}.png")
            file.parentFile.mkdirs()
            val image = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
            file.writeBytes(checkNotNull(image.encodeToData()).bytes)
        }
        assertHeadingAtTop("book1")

        // Scroll back to a book so the existing detail is already partly visible before another tap.
        onNodeWithText("B39").performScrollTo().performClick()
        assertHeadingAtTop("book39")

        // A tap on the same book is still a request to reveal its chapter buttons.
        onNodeWithText("B39").performScrollTo().performClick()
        assertHeadingAtTop("book39")
    }

    @Test fun readingBookTapsAlignDetailWithoutAnimations() = checkBookTaps(ReadingTab.READING, false)
    @Test fun readingBookTapsAlignDetailWithAnimations() = checkBookTaps(ReadingTab.READING, true)
    @Test fun memorizeBookTapsAlignDetailWithoutAnimations() = checkBookTaps(ReadingTab.MEMORIZE, false)
    @Test fun memorizeBookTapsAlignDetailWithAnimations() = checkBookTaps(ReadingTab.MEMORIZE, true)

    @Test fun delayedBookDetailsScrollOnlyAfterTheSelectedBookHasLoaded() {
        for (tab in ReadingTab.entries) runComposeUiTest {
            show(tab, animations = false, loadDelayMillis = 200)
            onNodeWithText("B1").performScrollTo().performClick()
            mainClock.advanceTimeBy(300)
            assertHeadingAtTop("book1")
            onNodeWithText("B39").performScrollTo().performClick()
            mainClock.advanceTimeBy(300)
            assertHeadingAtTop("book39")
        }
    }

    @Test fun shortBookChaptersAreVisibleWhenHeadingCannotReachTop() {
        for (tab in ReadingTab.entries) runComposeUiTest {
            show(tab, animations = false, chapters = 3)
            onNodeWithText("B1").performScrollTo().performClick()
            onNodeWithText("Detail book1").assertIsDisplayed()
            onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test fun restoredDetailsDoNotScrollOnEitherTab() {
        for (tab in ReadingTab.entries) runComposeUiTest {
            show(tab, animations = false, restored = true)
            val range = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(range.value() == 0f, "Restored $tab screen should stay at the overview")
        }
    }
}
