package net.bible.sharedui.reading.host.slots

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.android.view.activity.navigation.columnMajor
import net.bible.android.view.activity.navigation.layoutGrid
import net.bible.sharedcore.navigation.BookPick
import net.bible.sharedcore.navigation.ChapterPick
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedcore.speak.SpeakSheetPage
import net.bible.sharedui.speak.SpeakSettingsSheet
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Issue #4143: the Speak sheet's verse picker sized its grid to the fixed 400dp body bound for
 * every step, so a one-chapter book still showed a 400dp sheet. It is content-fitted like the
 * Choose Book sheet (#4133): the body follows the grid's real rows, under the same bound.
 */
@OptIn(ExperimentalTestApi::class)
class SpeakVersePickerSizesToContentTest {
    private val strings = iosStrings("en")

    private fun grid(step: GridStep, title: String, labels: List<String>, isBookGrid: Boolean): GridUi {
        val layout = layoutGrid(labels.size, isPortrait = true, isBookGrid = isBookGrid)
        val buttons = labels.mapIndexed { i, label -> GridButton(i + if (isBookGrid) 0 else 1, label) }
        return GridUi(step, title, layout.columns, showLongNames = false, showProgress = false,
            showDeutToggle = false, buttons = columnMajor(buttons, layout.columns), minRows = layout.rows)
    }

    /** Book0 has one chapter. */
    private fun controller() = GridChoosePassageController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        initialOptions = GridOptions(true, false, false, false, false, false),
        buildStep = { step, _ ->
            if (step == GridStep.BOOK) grid(step, "Books", (0 until 66).map { "Book$it" }, isBookGrid = true)
            else grid(step, "Book0", listOf("c1"), isBookGrid = false)
        },
        onPersistOptions = {},
        onPickBook = { BookPick.GoChapter },
        onPickChapter = { ChapterPick.Finish("x") },
        onPickVerse = { "x" },
        onFinish = {},
    )

    @Test
    fun versePickerBodyFollowsTheGrid() = runComposeUiTest {
        val controller = controller()
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    SpeakSettingsSheet(SpeakSheetPage.PickVerse(end = false), depth = 3, onDismiss = {}, onClose = {}) { _, _ ->
                        Box(Modifier.testTag(BODY)) { SpeakVersePickerGrid(controller) }
                    }
                }
            }
        }
        waitForIdle()
        val bookBody = bodyHeight()
        onNode(hasClickAction() and hasText("Book0")).performClick()
        waitForIdle()
        val chapterBody = bodyHeight()
        assertTrue(chapterBody < bookBody / 2, "one-chapter body $chapterBody not much shorter than book body $bookBody")
    }

    private fun ComposeUiTest.bodyHeight(): Float = onNodeWithTag(BODY).fetchSemanticsNode().size.height.toFloat()

    private companion object {
        const val BODY = "speak-picker-body"
    }
}
