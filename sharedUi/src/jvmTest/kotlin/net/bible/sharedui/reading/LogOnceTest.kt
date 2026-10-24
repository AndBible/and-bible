package net.bible.sharedui.reading

import net.bible.sharedui.reading.bridge.LogOnce
import kotlin.test.Test
import kotlin.test.assertEquals

class LogOnceTest {
    @Test fun logsEachNameOnlyOnce() {
        val lines = mutableListOf<String>()
        val once = LogOnce { lines += it }
        once.pending("openSearch")
        once.pending("openSearch")
        once.pending("openDownloads")
        once.pending("openSearch")
        assertEquals(listOf("pending: openSearch", "pending: openDownloads"), lines)
    }
}
