package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubSearchFormControllerTest {
    private fun controller(saved: MutableList<EpubSearchMode> = mutableListOf(),
                           submits: MutableList<Pair<String, EpubSearchMode>> = mutableListOf()) =
        EpubSearchFormController(
            loadMode = { EpubSearchMode.PHRASE },
            saveMode = { saved.add(it) },
            onSubmit = { q, m -> submits.add(q to m) },
        ) to (saved to submits)

    @Test fun initial_mode_from_loadMode() {
        val (c, _) = controller()
        assertEquals(EpubSearchMode.PHRASE, c.mode.value)
    }
    @Test fun setMode_persists() {
        val (c, state) = controller()
        c.setMode(EpubSearchMode.FTS)
        assertEquals(EpubSearchMode.FTS, c.mode.value)
        assertEquals(listOf(EpubSearchMode.FTS), state.first)
    }
    @Test fun seedMode_does_not_persist() {
        val (c, state) = controller()
        c.seedMode(EpubSearchMode.ANY_WORD)
        assertEquals(EpubSearchMode.ANY_WORD, c.mode.value)
        assertTrue(state.first.isEmpty())
    }
    @Test fun submit_blank_is_ignored() {
        val (c, state) = controller()
        c.setQuery("   ")
        c.submit()
        assertTrue(state.second.isEmpty())
    }
    @Test fun submit_forwards_query_and_mode() {
        val (c, state) = controller()
        c.setQuery("grace")
        c.setMode(EpubSearchMode.ANY_WORD)
        c.submit()
        assertEquals("grace" to EpubSearchMode.ANY_WORD, state.second.single())
    }

    // --- Task 15: FTS5 query-syntax help moved off the host into this controller's own state -----

    @Test fun helpOpen_startsFalse() {
        val (c, _) = controller()
        assertEquals(false, c.helpOpen.value)
    }

    @Test fun showHelp_opensIt() {
        val (c, _) = controller()
        c.showHelp()
        assertEquals(true, c.helpOpen.value)
    }

    @Test fun dismissHelp_closesIt() {
        val (c, _) = controller()
        c.showHelp()
        c.dismissHelp()
        assertEquals(false, c.helpOpen.value)
    }
}
