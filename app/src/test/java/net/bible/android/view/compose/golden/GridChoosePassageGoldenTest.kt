package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.navigation.GridChoosePassageScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class GridChoosePassageGoldenTest {
    // A compact 12-book grid across categories, with the current book + progress.
    private val books = listOf(
        GridButton(0, "Gen", "Genesis", 0, isCurrent = false, readProgress = 1f),
        GridButton(1, "Exod", "Exodus", 0, readProgress = 0.5f),
        GridButton(2, "Josh", "Joshua", 1),
        GridButton(3, "Ps", "Psalms", 2, isCurrent = true, memProgress = 0.3f),
        GridButton(4, "Isa", "Isaiah", 3),
        GridButton(5, "Hos", "Hosea", 4),
        GridButton(6, "Matt", "Matthew", 5),
        GridButton(7, "Acts", "Acts", 6),
        GridButton(8, "Rom", "Romans", 7),
        GridButton(9, "Jas", "James", 8),
        GridButton(10, "Rev", "Revelation", 9),
        GridButton(11, "Ps151", "Psalm 151", -1),
    )
    private val opts = GridOptions(showScripture = true, alphabetical = false, ltr = false, groupByCategory = false, longNames = false, showProgress = true)
    private fun bookUi(long: Boolean = false, sections: List<List<GridButton>>? = null) =
        GridUi(GridStep.BOOK, "Choose passage (Workspace 1)", 6, showLongNames = long, showProgress = true, showDeutToggle = true, buttons = books, sections = sections)

    @Test fun grid_book_flat() {
        captureMatrix("GridChoosePassage", "book_flat") { GridChoosePassageScreen(bookUi(), opts, {}, {}, {}) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun grid_book_flat_rtl() {
        captureRtl("GridChoosePassage", "book_flat") { GridChoosePassageScreen(bookUi(), opts, {}, {}, {}) }
    }

    @Test fun grid_book_grouped() {
        val sections = listOf(books.subList(0, 2), books.subList(2, 3), books.subList(3, 6), books.subList(6, 12))
        captureGolden("GridChoosePassage", "book_grouped", EDGE_MODE) {
            GridChoosePassageScreen(bookUi(sections = sections), opts.copy(groupByCategory = true, ltr = true), {}, {}, {})
        }
    }

    @Test fun grid_book_longnames() {
        captureGolden("GridChoosePassage", "book_long", EDGE_MODE) {
            GridChoosePassageScreen(bookUi(long = true), opts.copy(longNames = true), {}, {}, {})
        }
    }

    @Test fun grid_chapter() {
        val chapters = (1..24).map { GridButton(it, it.toString(), colorGroup = 2, isCurrent = it == 3, readProgress = if (it < 3) 1f else 0f) }
        captureGolden("GridChoosePassage", "chapter", EDGE_MODE) {
            GridChoosePassageScreen(
                GridUi(GridStep.CHAPTER, "Psalms", 5, showLongNames = false, showProgress = true, showDeutToggle = false, buttons = chapters),
                opts, {}, {}, {},
            )
        }
    }

    @Test fun grid_verse() {
        val verses = (1..31).map { GridButton(it, it.toString(), colorGroup = 2, isCurrent = it == 6, memProgress = if (it == 6) 1f else 0f) }
        captureGolden("GridChoosePassage", "verse", EDGE_MODE) {
            GridChoosePassageScreen(
                GridUi(GridStep.VERSE, "Psalms 3", 5, showLongNames = false, showProgress = true, showDeutToggle = false, buttons = verses),
                opts, {}, {}, {},
            )
        }
    }
}
