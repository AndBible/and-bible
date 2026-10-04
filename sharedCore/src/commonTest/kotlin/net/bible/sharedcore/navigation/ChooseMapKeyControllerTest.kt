package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChooseMapKeyControllerTest {
    private val sample = listOf(KeyRow("0", "Exodus route"), KeyRow("1", "Paul's journeys"))

    @Test fun load_populates_rows_and_current() {
        val c = ChooseMapKeyController({ sample }, { "0" }, {})
        assertEquals(sample, c.rows.value)
        assertEquals("0", c.currentKeyId.value)
        assertNull(c.error.value)
    }

    @Test fun select_forwards_keyId() {
        var selected: String? = null
        val c = ChooseMapKeyController({ sample }, { null }, { selected = it })
        c.select("1")
        assertEquals("1", selected)
    }

    @Test fun throwing_load_sets_error() {
        val c = ChooseMapKeyController({ throw RuntimeException("boom") }, { null }, {})
        assertEquals(ChooserError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }
}
