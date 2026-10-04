package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridChoosePassageControllerTest {
    private fun ui(s: GridStep) = GridUi(s, "t", 6, false, false, true, emptyList())
    private val opts = GridOptions(showScripture = true, alphabetical = false, ltr = false, groupByCategory = false, longNames = false, showProgress = true)

    private var bookPick: BookPick = BookPick.GoChapter
    private var chapterPick: ChapterPick = ChapterPick.GoVerse
    private var finished: String? = null
    private var persisted: GridOptions? = null
    private val builtSteps = mutableListOf<GridStep>()

    private fun make() = GridChoosePassageController(
        initialOptions = opts,
        buildStep = { s, _ -> builtSteps.add(s); ui(s) },
        onPersistOptions = { persisted = it },
        onPickBook = { bookPick },
        onPickChapter = { chapterPick },
        onPickVerse = { id -> "Gen.1.$id" },
        onFinish = { finished = it },
    )

    @Test fun starts_on_book() {
        val c = make()
        assertEquals(GridStep.BOOK, c.step.value)
        assertEquals(GridStep.BOOK, c.ui.value.step)
    }

    @Test fun book_goChapter_then_chapter_goVerse_then_verse_finishes() {
        val c = make()
        bookPick = BookPick.GoChapter
        c.pick(1)
        assertEquals(GridStep.CHAPTER, c.step.value)
        chapterPick = ChapterPick.GoVerse
        c.pick(3)
        assertEquals(GridStep.VERSE, c.step.value)
        c.pick(5)
        assertEquals("Gen.1.5", finished)
    }

    @Test fun book_finish_short_circuits() {
        val c = make()
        bookPick = BookPick.Finish("Phlm.1.1")
        c.pick(57)
        assertEquals("Phlm.1.1", finished)
        assertEquals(GridStep.BOOK, c.step.value) // no step change
    }

    @Test fun book_goVerse_skips_chapter() {
        val c = make()
        bookPick = BookPick.GoVerse
        c.pick(19) // e.g. Obadiah (single chapter) with navigateToVerse
        assertEquals(GridStep.VERSE, c.step.value)
    }

    @Test fun back_pops_stack_and_returns_false_at_root() {
        val c = make()
        bookPick = BookPick.GoChapter
        c.pick(1)
        assertEquals(GridStep.CHAPTER, c.step.value)
        assertTrue(c.back())
        assertEquals(GridStep.BOOK, c.step.value)
        assertFalse(c.back()) // empty stack at root
    }

    @Test fun toggle_flips_option_persists_and_rebuilds() {
        val c = make()
        c.toggle(GridOption.LONG_NAMES)
        assertTrue(c.options.value.longNames)
        assertEquals(opts.copy(longNames = true), persisted)
        // rebuilt the current (BOOK) step
        assertEquals(GridStep.BOOK, builtSteps.last())
    }

    @Test fun toggle_group_by_category_forces_ltr_and_clears_alphabetical() {
        val c = make()
        c.toggle(GridOption.ALPHABETICAL)      // set alphabetical
        c.toggle(GridOption.GROUP_BY_CATEGORY) // must clear alphabetical, force ltr
        assertTrue(c.options.value.groupByCategory)
        assertTrue(c.options.value.ltr)
        assertFalse(c.options.value.alphabetical)
    }
}
