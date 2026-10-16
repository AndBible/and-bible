package net.bible.android.view.activity.page

import org.junit.Assert.assertEquals
import org.junit.Test

class LastTouchedOfTest {
    @Test
    fun noAttachedBibleViewMeansNeverTouched() {
        assertEquals(0L, lastTouchedOf(emptyList()))
    }

    @Test
    fun returnsTheMostRecentTouch() {
        assertEquals(30L, lastTouchedOf(listOf(10L, 30L, 20L)))
    }
}
