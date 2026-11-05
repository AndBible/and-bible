package net.bible.sharedcore.bibleview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #3975: the document-generation gate of [BridgeDispatcher] and the classification of every bridge method. */
class BridgeGenerationPolicyTest {
    private class GatedBridge(var admitted: Long) : UnimplementedBibleBridge({}, {}), DocumentGenerationGate {
        val calls = mutableListOf<String>()
        override val admittedDocumentGeneration get() = admitted
        override fun scrolledToOrdinal(keyStr: String, ordinal: Int) { calls += "scrolledToOrdinal $ordinal" }
        override fun saveBookmarkNote(bookmarkId: String, note: String?) { calls += "saveBookmarkNote $note" }
        override fun requestMoreToEnd(callId: Long) { calls += "requestMoreToEnd $callId" }
    }

    private val answered = mutableListOf<Long>()
    private val bridge = GatedBridge(admitted = 7)
    private val dispatcher = BridgeDispatcher(bridge, { answered += it }, {})

    private fun scroll(generation: String?) = dispatcher.dispatch(
        """{"method":"scrolledToOrdinal","args":["Gen.1",5]${generation?.let { ",\"documentGeneration\":$it" } ?: ""}}""")

    @Test fun everyBridgeMethodIsClassifiedExactlyOnce() {
        val methods = BibleBridgeDecoder.entries.keys
        assertEquals(emptySet(), BridgeGenerationPolicy.guarded intersect BridgeGenerationPolicy.unguarded)
        assertEquals(emptySet(), methods - (BridgeGenerationPolicy.guarded + BridgeGenerationPolicy.unguarded), "unclassified")
        assertEquals(emptySet(), (BridgeGenerationPolicy.guarded + BridgeGenerationPolicy.unguarded) - methods, "not a bridge method")
    }

    /**
     * Never guarded: the editor `clear_document` closes saves its text with exactly these calls, and the page's own UI
     * state must follow the live page. Guarding one would lose user input or wedge the window.
     */
    @Test fun userInputAndLivePageStateAreNeverGuarded() {
        val mustPass = setOf(
            "saveBookmarkNote", "saveGenericBookmarkNote", "updateStudyPadTextEntry", "updateStudyPadTextEntryText",
            "saveMyDocumentPageContent", "updateBookmarkToLabel", "updateGenericBookmarkToLabel",
            "setClientReady", "setEditing", "reportModalState", "console",
        )
        assertEquals(emptySet(), mustPass intersect BridgeGenerationPolicy.guarded)
        assertEquals(emptySet(), mustPass - BridgeGenerationPolicy.unguarded)
    }

    @Test fun theEnvelopeCarriesTheGeneration() {
        assertEquals(12L, parseBridgeMessage("""{"method":"x","args":[],"documentGeneration":12}""").getOrThrow().documentGeneration)
        assertNull(parseBridgeMessage("""{"method":"x","args":[]}""").getOrThrow().documentGeneration)
        assertNull(parseBridgeMessage("""{"method":"x","args":[],"documentGeneration":"12"}""").getOrThrow().documentGeneration)
    }

    @Test fun aGuardedCallOfTheAdmittedDocumentIsHandled() {
        assertEquals(BridgeResult.Handled, scroll("7"))
        assertEquals(listOf("scrolledToOrdinal 5"), bridge.calls)
    }

    @Test fun aGuardedCallOfAnotherDocumentIsDropped() {
        assertEquals(BridgeResult.Dropped("scrolledToOrdinal"), scroll("6"))
        assertEquals(BridgeResult.Dropped("scrolledToOrdinal"), scroll("8"))
        assertEquals(BridgeResult.Dropped("scrolledToOrdinal"), scroll(null))
        assertTrue(bridge.calls.isEmpty())
    }

    @Test fun nothingIsGuardedThroughWhileNoDocumentIsAdmitted() {
        bridge.admitted = NO_DOCUMENT_GENERATION
        assertEquals(BridgeResult.Dropped("scrolledToOrdinal"), scroll("$NO_DOCUMENT_GENERATION"))
        assertTrue(bridge.calls.isEmpty())
    }

    @Test fun aStaleDeferredCallIsAnsweredWithNull() {
        assertIs<BridgeResult.Dropped>(dispatcher.dispatch("""{"method":"requestMoreToEnd","args":[9],"documentGeneration":6}"""))
        assertEquals(listOf(9L), answered)
        assertTrue(bridge.calls.isEmpty())
    }

    /** A note the user typed in the old page must still be saved: id-addressed data writes are never dropped. */
    @Test fun anUnguardedCallOfAReplacedPageIsHandled() {
        bridge.admitted = NO_DOCUMENT_GENERATION
        assertEquals(BridgeResult.Handled,
            dispatcher.dispatch("""{"method":"saveBookmarkNote","args":["b","text"],"documentGeneration":3}"""))
        assertEquals(listOf("saveBookmarkNote text"), bridge.calls)
    }

    @Test fun aBridgeWithoutAGateIsNotGuarded() {
        val plain = RecordingBridge()
        assertEquals(BridgeResult.Handled, BridgeDispatcher(plain, {}, {})
            .dispatch("""{"method":"scrolledToOrdinal","args":["k",1]}"""))
        assertEquals(1, plain.calls.size)
    }
}
