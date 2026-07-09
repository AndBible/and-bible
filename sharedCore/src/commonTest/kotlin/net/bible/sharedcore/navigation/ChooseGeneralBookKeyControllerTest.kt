package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChooseGeneralBookKeyControllerTest {
    private val sample = listOf(KeyRow("0", "Genesis"), KeyRow("1", "Exodus"), KeyRow("2", "Leviticus"))

    @Test fun load_populates_rows_and_current() {
        val c = ChooseGeneralBookKeyController({ sample }, { "1" }, {})
        assertEquals(sample, c.rows.value)
        assertEquals("1", c.currentKeyId.value)
        assertNull(c.error.value)
    }

    @Test fun select_forwards_keyId() {
        var selected: String? = null
        val c = ChooseGeneralBookKeyController({ sample }, { null }, { selected = it })
        c.select("2")
        assertEquals("2", selected)
    }

    @Test fun throwing_load_sets_error() {
        val c = ChooseGeneralBookKeyController({ throw RuntimeException("boom") }, { null }, {})
        assertEquals(ChooserError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }
}
