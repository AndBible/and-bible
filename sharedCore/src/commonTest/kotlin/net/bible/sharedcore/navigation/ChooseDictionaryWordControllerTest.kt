package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChooseDictionaryWordControllerTest {
    private val all = listOf(
        DictRow("0", "Faith"), DictRow("1", "Hope"), DictRow("2", "Love"), DictRow("3", "Faithful"),
    )

    @Test fun starts_loading() {
        val c = ChooseDictionaryWordController({})
        assertTrue(c.loading.value)
        assertTrue(c.rows.value.isEmpty())
    }

    @Test fun setAllRows_clears_loading_and_shows_all() {
        val c = ChooseDictionaryWordController({})
        c.setAllRows(all)
        assertFalse(c.loading.value)
        assertEquals(all, c.rows.value)
    }

    @Test fun setQuery_filters_case_insensitively_on_name() {
        val c = ChooseDictionaryWordController({})
        c.setAllRows(all)
        c.setQuery("fai")
        assertEquals(listOf("Faith", "Faithful"), c.rows.value.map { it.name })
    }

    @Test fun empty_query_shows_all() {
        val c = ChooseDictionaryWordController({})
        c.setAllRows(all)
        c.setQuery("love")
        c.setQuery("")
        assertEquals(all, c.rows.value)
    }

    @Test fun no_match_yields_empty() {
        val c = ChooseDictionaryWordController({})
        c.setAllRows(all)
        c.setQuery("zzz")
        assertTrue(c.rows.value.isEmpty())
    }

    @Test fun select_forwards_keyId() {
        var selected: String? = null
        val c = ChooseDictionaryWordController({ selected = it })
        c.setAllRows(all)
        c.select("3")
        assertEquals("3", selected)
    }

    @Test fun showError_then_dismiss() {
        val c = ChooseDictionaryWordController({})
        c.showError()
        assertEquals(ChooserError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }
}
