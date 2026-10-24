package net.bible.android.view.activity.page

import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.bibleview.UnimplementedBibleBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidBridgeEndpointTest {
    private class Recording : UnimplementedBibleBridge({}, {}) {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        override fun unimplemented(method: String, args: List<Any?>) { calls += method to args }
        override fun unimplementedDeferred(method: String, callId: Long, args: List<Any?>) { calls += method to args }
    }

    @Test fun postMessageReachesTheBridge() {
        val bridge = Recording()
        val endpoint = AndroidBridgeEndpoint(BridgeDispatcher(bridge, {}, {}), { "[]" })
        endpoint.postMessage("""{"method":"addBookmark","args":["KJV",1,2,true]}""")
        assertEquals(listOf("addBookmark" to listOf<Any?>("KJV", 1, 2, true)), bridge.calls)
    }

    @Test fun dispatchRunsSynchronouslyOnTheCallingThread() {
        val thread = Thread.currentThread()
        var calledOn: Thread? = null
        val bridge = object : UnimplementedBibleBridge({}, {}) {
            override fun toast(text: String) { calledOn = Thread.currentThread() }
        }
        AndroidBridgeEndpoint(BridgeDispatcher(bridge, {}, {}), { "[]" })
            .postMessage("""{"method":"toast","args":["hello"]}""")
        assertEquals(thread, calledOn)
    }

    @Test fun throwingHandlerResponseAndLoggerDoNotEscape() {
        var answered: Long? = null
        val bridge = object : UnimplementedBibleBridge({}, {}) {
            override fun parseRef(callId: Long, s: String) { throw AssertionError("handler") }
        }
        val endpoint = AndroidBridgeEndpoint(BridgeDispatcher(bridge, {
            answered = it
            throw AssertionError("callback")
        }, { throw AssertionError("logger") }), { "[]" })
        endpoint.postMessage("""{"method":"parseRef","args":[42,"bad"]}""")
        assertEquals(42L, answered)
    }

    @Test fun getActiveLanguagesPreservesEmptyAndMultipleLanguageResults() {
        for (languages in listOf("[]", "[\"fi\",\"en\"]")) {
            val endpoint = AndroidBridgeEndpoint(BridgeDispatcher(Recording(), {}, {}), { languages })
            assertEquals(languages, endpoint.getActiveLanguages())
        }
    }

    @Test fun providerErrorsDoNotEscapeTheSynchronousBoundary() {
        val endpoint = AndroidBridgeEndpoint(BridgeDispatcher(Recording(), {}, {}), { throw AssertionError("provider") })
        assertEquals("[\"en\"]", endpoint.getActiveLanguages())
    }

    @Test fun malformedMessageDoesNotThrow() {
        val endpoint = AndroidBridgeEndpoint(BridgeDispatcher(Recording(), {}, {}), { "[]" })
        endpoint.postMessage("{nope")
    }

    @Test fun getActiveLanguagesFallsBackToEnglishWhenTheProviderThrows() {
        val endpoint = AndroidBridgeEndpoint(BridgeDispatcher(Recording(), {}, {}), { error("no books") })
        assertEquals("[\"en\"]", endpoint.getActiveLanguages())
    }
}
