package net.bible.sharedui.reading

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which panes clear the top safe area (iOS full screen): the first of a stacked split, every pane side by side. */
class TopInsetForPaneTest {
    @Test fun firstStackedPaneGetsTheInset() = assertEquals(59.dp, topInsetForPane(59.dp, isHorizontalSplit = false, index = 0))

    @Test fun lowerStackedPaneGetsNone() {
        assertEquals(0.dp, topInsetForPane(59.dp, isHorizontalSplit = false, index = 1))
        assertEquals(0.dp, topInsetForPane(59.dp, isHorizontalSplit = false, index = 2))
    }

    @Test fun sideBySidePanesAllGetTheInset() {
        assertEquals(59.dp, topInsetForPane(59.dp, isHorizontalSplit = true, index = 0))
        assertEquals(59.dp, topInsetForPane(59.dp, isHorizontalSplit = true, index = 1))
    }

    @Test fun noInsetStaysNone() = assertEquals(0.dp, topInsetForPane(0.dp, isHorizontalSplit = false, index = 1))
}
