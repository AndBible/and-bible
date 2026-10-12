package net.bible.sharedcore.navigation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.assertNull
import net.bible.sharedcore.readingplan.ReadingPlanError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GridChoosePassageControllerTest {
    private fun ui(s: GridStep) = GridUi(s, "t", 6, false, false, true, emptyList())
    private val opts = GridOptions(showScripture = true, alphabetical = false, ltr = false, groupByCategory = false, longNames = false, showProgress = true)

    private var bookPick: BookPick = BookPick.GoChapter
    private var chapterPick: ChapterPick = ChapterPick.GoVerse
    private var finished: String? = null
    private var persisted: GridOptions? = null
    private val builtSteps = mutableListOf<GridStep>()

    private fun CoroutineScope.make() = GridChoosePassageController(
        scope = this,
        initialOptions = opts,
        buildStep = { s, _ -> builtSteps.add(s); ui(s) },
        onPersistOptions = { persisted = it },
        onPickBook = { bookPick },
        onPickChapter = { chapterPick },
        onPickVerse = { id -> "Gen.1.$id" },
        onFinish = { finished = it },
    )

    @Test fun starts_on_book() = runTest {
        val c = make()
        assertEquals(GridStep.BOOK, c.step.value)
        assertEquals(GridStep.BOOK, c.ui.value?.step)
    }

    @Test fun book_goChapter_then_chapter_goVerse_then_verse_finishes() = runTest {
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

    @Test fun book_finish_short_circuits() = runTest {
        val c = make()
        bookPick = BookPick.Finish("Phlm.1.1")
        c.pick(57)
        assertEquals("Phlm.1.1", finished)
        assertEquals(GridStep.BOOK, c.step.value) // no step change
    }

    @Test fun book_goVerse_skips_chapter() = runTest {
        val c = make()
        bookPick = BookPick.GoVerse
        c.pick(19) // e.g. Obadiah (single chapter) with navigateToVerse
        assertEquals(GridStep.VERSE, c.step.value)
    }

    @Test fun back_pops_stack_and_returns_false_at_root() = runTest {
        val c = make()
        bookPick = BookPick.GoChapter
        c.pick(1)
        assertEquals(GridStep.CHAPTER, c.step.value)
        assertTrue(c.back())
        assertEquals(GridStep.BOOK, c.step.value)
        assertFalse(c.back()) // empty stack at root
    }

    @Test fun toggle_flips_option_persists_and_rebuilds() = runTest {
        val c = make()
        c.toggle(GridOption.LONG_NAMES)
        assertTrue(c.options.value.longNames)
        assertEquals(opts.copy(longNames = true), persisted)
        // rebuilt the current (BOOK) step
        assertEquals(GridStep.BOOK, builtSteps.last())
    }

    @Test fun toggle_group_by_category_forces_ltr_and_clears_alphabetical() = runTest {
        val c = make()
        c.toggle(GridOption.ALPHABETICAL)      // set alphabetical
        c.toggle(GridOption.GROUP_BY_CATEGORY) // must clear alphabetical, force ltr
        assertTrue(c.options.value.groupByCategory)
        assertTrue(c.options.value.ltr)
        assertFalse(c.options.value.alphabetical)
    }

    private fun CoroutineScope.withSource(source: suspend (GridStep, GridOptions) -> GridUi) =
        GridChoosePassageController(this, opts, source, {}, { BookPick.GoChapter },
            { ChapterPick.GoVerse }, { "Gen.1.$it" }, {})

    @Test fun suspended_initial_build_has_no_ui_until_completed() = runTest {
        val gate = CompletableDeferred<GridUi>()
        val c = withSource { _, _ -> gate.await() }
        assertNull(c.ui.value)
        gate.complete(ui(GridStep.BOOK))
        runCurrent()
        assertEquals(GridStep.BOOK, c.ui.value?.step)
    }

    @Test fun throwing_source_sets_failed() = runTest {
        val c = withSource { _, _ -> error("boom") }
        assertEquals(ReadingPlanError.FAILED, c.error.value)
    }

    @Test fun cancelled_source_is_not_failed() = runTest {
        val c = withSource { _, _ -> throw CancellationException("screen closed") }
        assertNull(c.error.value)
    }

    @Test fun newer_toggle_wins_even_when_older_build_ignores_cancellation() = runTest {
        val old = CompletableDeferred<Unit>()
        val c = withSource { step, options ->
            if (options.longNames) withContext(NonCancellable) { old.await() }
            ui(step).copy(title = if (options.longNames) "old" else "new")
        }
        c.toggle(GridOption.LONG_NAMES)
        c.toggle(GridOption.LONG_NAMES)
        assertEquals("new", c.ui.value?.title)
        old.complete(Unit)
        runCurrent()
        assertEquals("new", c.ui.value?.title)
        assertNull(c.error.value)
    }

    @Test fun old_step_buttons_are_hidden_until_new_step_finishes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val c = withSource { step, _ ->
            if (step == GridStep.CHAPTER) gate.await()
            ui(step)
        }
        c.pick(1)
        assertEquals(GridStep.CHAPTER, c.step.value)
        assertNull(c.ui.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(GridStep.CHAPTER, c.ui.value?.step)
    }

    @Test fun stale_failure_cannot_replace_newer_success() = runTest {
        val old = CompletableDeferred<Unit>()
        val c = withSource { step, options ->
            if (options.longNames) {
                withContext(NonCancellable) { old.await() }
                error("obsolete failure")
            }
            ui(step)
        }
        c.toggle(GridOption.LONG_NAMES)
        c.toggle(GridOption.LONG_NAMES)
        old.complete(Unit)
        runCurrent()
        assertNull(c.error.value)
        assertEquals(GridStep.BOOK, c.ui.value?.step)
    }

    @Test fun stale_book_callback_cannot_pick_a_chapter_while_build_is_held() = runTest {
        val held = CompletableDeferred<Unit>()
        var chapterPicks = 0
        val c = GridChoosePassageController(this, opts, { step, _ ->
            if (step == GridStep.CHAPTER) held.await()
            ui(step)
        }, {}, { BookPick.GoChapter }, { chapterPicks++; ChapterPick.GoVerse }, { "Gen.1.$it" }, {})
        val rendered = c.ui.value!!
        c.pick(1, rendered)
        c.pick(2, rendered)
        assertEquals(0, chapterPicks)
        assertEquals(GridStep.CHAPTER, c.step.value)
        held.complete(Unit)
        runCurrent()
        c.pick(2, rendered)
        assertEquals(0, chapterPicks)
    }
}
