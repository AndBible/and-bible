package net.bible.sharedcore.bibleview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnimplementedBibleBridgeTest {
    @Test fun throwingLoggerDoesNotPreventDeferredNullResponse() {
        var respondedWith: Long? = null
        val bridge = UnimplementedBibleBridge(
            log = { error("logger failed") },
            respondNull = { respondedWith = it },
        )

        bridge.parseRef(42L, "Gen 1")

        assertEquals(42L, respondedWith)
    }

    @Test fun throwingResponseCallbackIsSwallowed() {
        var attempted = false
        val bridge = UnimplementedBibleBridge(
            log = {},
            respondNull = { attempted = true; error("response failed") },
        )

        bridge.requestMoreToEnd(12L)

        assertTrue(attempted)
    }

    @Test fun throwingLoggerAndResponseCallbackAreBothSwallowed() {
        var attempted = false
        val bridge = UnimplementedBibleBridge(
            log = { error("logger failed") },
            respondNull = { attempted = true; error("response failed") },
        )

        bridge.getMyDocumentPageRawContent(8L, "KJV", "Gen.1")

        assertTrue(attempted)
    }

    @Test fun nonDeferredMethodSwallowsLoggerFailure() {
        val bridge = UnimplementedBibleBridge(log = { error("logger failed") }, respondNull = {})

        bridge.removeBookmark("b1")
    }
}
