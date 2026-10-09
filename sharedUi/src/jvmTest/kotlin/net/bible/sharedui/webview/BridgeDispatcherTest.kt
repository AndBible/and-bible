package net.bible.sharedui.webview

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BridgeDispatcherTest {
    private val evaluated = mutableListOf<String>()
    private val logged = mutableListOf<String>()
    private fun dispatcher(handlers: Map<String, BridgeHandler> = emptyMap()) =
        BridgeDispatcher(JsSink { evaluated += it }, { logged += it }, handlers)

    @Test fun parsesMethodAndArgs() {
        val m = parseBridgeMessage("""{"method":"scrolledToOrdinal","args":["KJV:Eph.2",36300]}""").getOrThrow()
        assertEquals("scrolledToOrdinal", m.method)
        assertEquals(listOf(JsonPrimitive("KJV:Eph.2"), JsonPrimitive(36300)), m.args)
    }

    @Test fun routesToHandler() {
        var got: Int? = null
        val d = dispatcher(mapOf("scrolledToOrdinal" to BridgeHandler { args, _ -> got = (args[1] as JsonPrimitive).content.toInt() }))
        assertEquals(BridgeResult.Handled, d.dispatch("""{"method":"scrolledToOrdinal","args":["k",36300]}"""))
        assertEquals(36300, got)
    }

    @Test fun unknownSyncMethodIsLoggedNoOp() {
        val r = dispatcher().dispatch("""{"method":"selectionCleared","args":[]}""")
        assertEquals(BridgeResult.Unhandled("selectionCleared"), r)
        assertTrue(evaluated.isEmpty())
        assertTrue(logged.single().contains("selectionCleared"))
    }

    @Test fun asyncMethodWithoutHandlerGetsNullResponse() {
        for (method in ASYNC_BRIDGE_METHODS) {
            evaluated.clear()
            dispatcher().dispatch("""{"method":"$method","args":[7,"x"]}""")
            assertEquals(listOf("bibleView.response(7, null)"), evaluated, method)
        }
    }

    @Test fun asyncSetIsExactlyTheDeferredCallMethods() {
        assertEquals(
            setOf("parseRef", "refChooserDialog", "requestMoreToEnd", "requestMoreToBeginning", "getMyDocumentPageRawContent"),
            ASYNC_BRIDGE_METHODS,
        )
    }

    @Test fun malformedMessagesAreErrorsNotExceptions() {
        val d = dispatcher()
        for (raw in listOf("not json", """{"args":[]}""", """{"method":"x","args":{}}""", """{"method":5,"args":[]}""", "")) {
            assertIs<BridgeResult.Error>(d.dispatch(raw), raw)
        }
        assertEquals(5, logged.size)
    }

    @Test fun throwingHandlerBecomesError() {
        val d = dispatcher(mapOf("setClientReady" to BridgeHandler { _, _ -> error("boom") }))
        val r = d.dispatch("""{"method":"setClientReady","args":[]}""")
        assertIs<BridgeResult.Error>(r)
        assertTrue(r.reason.contains("boom"))
    }

    @Test fun throwingJsSinkOnAsyncMethodBecomesError() {
        val d = BridgeDispatcher(JsSink { error("webview torn down") }, { logged += it }, emptyMap())
        val r = d.dispatch("""{"method":"parseRef","args":[3,"Gen 1"]}""")
        assertIs<BridgeResult.Error>(r)
        assertTrue(r.reason.contains("webview torn down"))
    }

    @Test fun throwingLogNeverEscapesDispatch() {
        val d = BridgeDispatcher(JsSink { }, { error("log broken") }, emptyMap())
        assertEquals(BridgeResult.Unhandled("selectionCleared"), d.dispatch("""{"method":"selectionCleared","args":[]}"""))
        assertIs<BridgeResult.Error>(d.dispatch("not json"))
    }

    @Test fun emitJsFormatsEvent() {
        assertEquals("bibleView.emit('add_documents', {\"a\":1})", emitJs("add_documents", """{"a":1}"""))
        assertEquals("bibleView.emit('clear_document')", emitJs("clear_document"))
    }
}
