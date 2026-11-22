package net.bible.sharedui.desktop.webengine

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #4188: [FakePane.post] runs its block inline on the caller's thread (a real KCEF pane hops to the EDT), so a window's
 * background text load records its `adjust_loading_count` script from a worker thread while the test reads
 * [FakePane.evaluated] on the EDT. Reading the record must not throw `ConcurrentModificationException`.
 */
class FakePaneThreadingTest {
    @Test fun aRecordReadByOneThreadSurvivesAnEvaluateFromAnother() {
        val pane = FakePane("w", "fake://", emptyList()) {}
        pane.evaluate("first", null)
        pane.evaluate("second", null)
        val read = mutableListOf<String>()
        for (js in pane.evaluated) {
            read += js
            // A worker records mid-read, as a background loadText does while the test formats `evaluated`.
            if (read.size == 1) thread { pane.post { pane.evaluate("from a worker", null) } }.join()
        }

        assertEquals(listOf("first", "second"), read, "a reader keeps the snapshot it started with")
        assertEquals(listOf("first", "second", "from a worker"), pane.evaluated.toList())
    }
}
