package net.bible.android.view.compose

import net.bible.sharedui.components.BarWrite
import net.bible.sharedui.components.HostSystemBars
import net.bible.sharedui.components.barWrites
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fix batch 5 F106 / Review Focus 4: what a sheet's dialog window is told to do with its bars. */
class HostSystemBarsMirrorTest {
    @Test fun visibleHostBarsAreShownNotHidden() =
        assertEquals(listOf(BarWrite.ShowStatus, BarWrite.ShowNav), barWrites(HostSystemBars(true, true)))

    @Test fun hiddenStatusHidesItAndAllowsSwipe() =
        assertEquals(listOf(BarWrite.HideStatus, BarWrite.ShowNav, BarWrite.TransientBySwipe), barWrites(HostSystemBars(false, true)))

    @Test fun fullscreenHidesBoth() =
        assertEquals(listOf(BarWrite.HideStatus, BarWrite.HideNav, BarWrite.TransientBySwipe), barWrites(HostSystemBars(false, false)))

    @Test fun hiddenNavAloneAllowsSwipe() =
        assertEquals(listOf(BarWrite.ShowStatus, BarWrite.HideNav, BarWrite.TransientBySwipe), barWrites(HostSystemBars(true, false)))

    @Test fun noHostLocalMeansNoWrite() = assertEquals(emptyList<BarWrite>(), barWrites(null))
}
