package net.bible.android.control.page.window

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowStateServiceImplResetTest {
    @Test fun resetDropsLeakedSubscribers() {
        val service = WindowStateServiceImpl()
        val seen = mutableListOf<WindowChange>()
        service.windowChanges.subscribe { seen += it }
        service.notify(WindowChange.LayoutConfigurationChanged)
        assertEquals(1, seen.size)
        service.resetSubscribersForTest()
        service.notify(WindowChange.LayoutConfigurationChanged)
        assertEquals(1, seen.size)
    }
}
