package net.bible.sharedcore.bibleview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BridgeDispatcherTest {
    private val bridge = RecordingBridge()
    private val answered = mutableListOf<Long>()
    private val logged = mutableListOf<String>()
    private val dispatcher = BridgeDispatcher(bridge, { answered += it }, { logged += it })

    @Test fun handledCallReachesTheBridge() {
        assertEquals(BridgeResult.Handled, dispatcher.dispatch("""{"method":"addBookmark","args":["KJV",1,2,false]}"""))
        assertEquals(listOf("addBookmark" to listOf<Any?>("KJV", 1, 2, false)), bridge.calls)
    }

    @Test fun malformedJsonIsAnErrorNotAThrow() {
        assertIs<BridgeResult.Error>(dispatcher.dispatch("{not json"))
        assertIs<BridgeResult.Error>(dispatcher.dispatch("""{"method":1,"args":[]}"""))
        assertIs<BridgeResult.Error>(dispatcher.dispatch("""{"method":"x","args":{}}"""))
        assertIs<BridgeResult.Error>(dispatcher.dispatch("[]"))
        assertTrue(logged.isNotEmpty())
    }

    @Test fun unknownMethodIsUnhandled() {
        assertEquals(BridgeResult.Unhandled("nope"), dispatcher.dispatch("""{"method":"nope","args":[]}"""))
        assertTrue(answered.isEmpty())
    }

    @Test fun badArgumentsOnDeferredAnswerNull() {
        assertIs<BridgeResult.Error>(dispatcher.dispatch("""{"method":"parseRef","args":[7]}"""))
        assertEquals(listOf(7L), answered)
    }

    @Test fun fractionalCallIdOnBadArityAnswersTruncatedId() {
        assertIs<BridgeResult.Error>(dispatcher.dispatch("""{"method":"parseRef","args":[7.9]}"""))
        assertEquals(listOf(7L), answered)
    }

    @Test fun badArgumentsOnPlainCallDoNotAnswer() {
        assertIs<BridgeResult.Error>(dispatcher.dispatch("""{"method":"removeBookmark","args":[null]}"""))
        assertTrue(answered.isEmpty())
    }

    @Test fun throwingDeferredHandlerAnswersNull() {
        val throwing = object : UnimplementedBibleBridge({}, {}) {
            override fun parseRef(callId: Long, s: String) = throw IllegalStateException("boom")
        }
        val d = BridgeDispatcher(throwing, { answered += it }, { logged += it })
        assertIs<BridgeResult.Error>(d.dispatch("""{"method":"parseRef","args":[3,"Gen 1"]}"""))
        assertEquals(listOf(3L), answered)
        assertTrue(logged.any { "boom" in it })
    }

    @Test fun unimplementedDeferredAnswersNullThroughTheBase() {
        val d = BridgeDispatcher(UnimplementedBibleBridge({ logged += it }, { answered += it }), { }, { })
        assertEquals(BridgeResult.Handled, d.dispatch("""{"method":"refChooserDialog","args":[4]}"""))
        assertEquals(listOf(4L), answered)
    }

    @Test fun failingLoggerAndResponderNeverEscape() {
        val d = BridgeDispatcher(bridge, { error("responder") }, { error("logger") })
        assertIs<BridgeResult.Error>(d.dispatch("{bad"))
        assertIs<BridgeResult.Error>(d.dispatch("""{"method":"parseRef","args":[1]}"""))
    }
}
