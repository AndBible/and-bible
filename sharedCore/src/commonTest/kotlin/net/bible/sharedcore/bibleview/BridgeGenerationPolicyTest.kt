package net.bible.sharedcore.bibleview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #3975: the document-generation gate of [BridgeDispatcher] and the classification of every bridge method. */
class BridgeGenerationPolicyTest {
    /**
     * A gate like ReadingBridge's: [runAdmitted] queues the call for "the main thread" ([runMain]) and checks the
     * generation again when it runs there.
     */
    private class GatedBridge(var admitted: Long) : UnimplementedBibleBridge({}, {}), DocumentGenerationGate {
        val calls = mutableListOf<String>()
        val main = ArrayDeque<() -> Unit>()
        override fun isAdmitted(generation: Long?) =
            generation != null && admitted != NO_DOCUMENT_GENERATION && generation == admitted
        override fun runAdmitted(generation: Long?, call: () -> Unit, drop: () -> Unit) {
            main.addLast { if (isAdmitted(generation)) call() else drop() }
        }
        fun runMain() { while (main.isNotEmpty()) main.removeFirst()() }
        /** The page's own document: [pageDocument], which stays while a newer one is requested ([admitted] 0). */
        var pageDocument = admitted
        override fun runForPageDocument(generation: Long?, call: () -> Unit, drop: () -> Unit) =
            if (generation != null && generation != NO_DOCUMENT_GENERATION && generation == pageDocument) call() else drop()
        override fun scrolledToOrdinal(keyStr: String, ordinal: Int) { calls += "scrolledToOrdinal $ordinal" }
        override fun saveBookmarkNote(bookmarkId: String, note: String?) { calls += "saveBookmarkNote $note" }
        override fun requestMoreToEnd(callId: Long) { calls += "requestMoreToEnd $callId" }
    }

    private val answered = mutableListOf<Long>()
    private val bridge = GatedBridge(admitted = 7)
    private val dispatcher = BridgeDispatcher(bridge, { answered += it }, {})

    private fun scroll(generation: String?) = dispatcher.dispatch(
        """{"method":"scrolledToOrdinal","args":["Gen.1",5]${generation?.let { ",\"documentGeneration\":$it" } ?: ""}}""")
            .also { bridge.runMain() }

    @Test fun everyBridgeMethodIsClassifiedExactlyOnce() {
        val methods = BibleBridgeDecoder.entries.keys
        val sets = listOf(BridgeGenerationPolicy.guarded, BridgeGenerationPolicy.paging, BridgeGenerationPolicy.unguarded)
        for (i in sets.indices) for (j in sets.indices) if (i < j) assertEquals(emptySet(), sets[i] intersect sets[j])
        val all = sets.flatten().toSet()
        assertEquals(emptySet(), methods - all, "unclassified")
        assertEquals(emptySet(), all - methods, "not a bridge method")
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

    /**
     * Review lens (#3975): every call that navigates, or acts on the active or current window (its document, position,
     * speech that follows the text, the prompt selector of its document category, keyboard shortcuts), is guarded.
     */
    @Test fun navigationAndCurrentWindowActionsAreGuarded() {
        val mustGuard = setOf(
            // Navigation of the window or the active window (LinkControl resolves the active window).
            "goToNextChapter", "goToPreviousChapter", "openEpubLink", "openStudyPad", "openMyNotes", "openAiDocPage",
            "openExternalLink", "compare", "memorize",
            // Speech moves the window along the text it speaks.
            "speak", "speakMemorizationLoop", "speakGeneric",
            // The prompt selector reads the active window's document category.
            "llmAction", "llmActionGeneric", "noteEditorLlmAction",
            // Keyboard shortcuts act on this view's window or the active one.
            "onKeyDown",
            // The window's document itself.
            "shareHtml", "deleteMyDocumentPage", "regenerateMyDocumentPage",
        )
        assertEquals(emptySet(), mustGuard - BridgeGenerationPolicy.guarded)
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

    @Test fun aStalePagingCallIsAnsweredWithNull() {
        dispatcher.dispatch("""{"method":"requestMoreToEnd","args":[9],"documentGeneration":6}""")
        assertEquals(listOf(9L), answered)
        assertTrue(bridge.calls.isEmpty())
    }

    /**
     * Review A3 (#3975): a chapter request of the page's own document runs while the window has requested a newer
     * document (no guarded call is admitted then); dropping it would end infinite scroll for good.
     */
    @Test fun aPagingCallOfThePagesDocumentRunsWhileANewerOneIsRequested() {
        bridge.admitted = NO_DOCUMENT_GENERATION
        dispatcher.dispatch("""{"method":"requestMoreToEnd","args":[9],"documentGeneration":7}""")
        assertEquals(listOf("requestMoreToEnd 9"), bridge.calls)
        assertTrue(answered.isEmpty())
    }

    /** A note the user typed in the old page must still be saved: id-addressed data writes are never dropped. */
    @Test fun anUnguardedCallOfAReplacedPageIsHandled() {
        bridge.admitted = NO_DOCUMENT_GENERATION
        assertEquals(BridgeResult.Handled,
            dispatcher.dispatch("""{"method":"saveBookmarkNote","args":["b","text"],"documentGeneration":3}"""))
        assertEquals(listOf("saveBookmarkNote text"), bridge.calls)
    }

    /** Review A (#3975): a bridge that does not implement the gate must not let guarded calls through (fail closed). */
    @Test fun aBridgeWithoutAGateDropsGuardedCalls() {
        val plain = RecordingBridge()
        assertEquals(BridgeResult.Dropped("scrolledToOrdinal"), BridgeDispatcher(plain, {}, {})
            .dispatch("""{"method":"scrolledToOrdinal","args":["k",1],"documentGeneration":1}"""))
        assertTrue(plain.calls.isEmpty())
        assertEquals(BridgeResult.Handled, BridgeDispatcher(plain, {}, {})
            .dispatch("""{"method":"saveBookmarkNote","args":["b","text"]}"""))
    }

    @Test fun anExplicitlyOpenGateAdmitsEverything() {
        val plain = RecordingBridge()
        assertEquals(BridgeResult.Handled, BridgeDispatcher(plain, {}, {}, OpenDocumentGate)
            .dispatch("""{"method":"scrolledToOrdinal","args":["k",1]}"""))
        assertEquals(1, plain.calls.size)
    }

    /**
     * Review B (#3975): the generation is checked again where the call runs. A navigation (or a view rebind) between
     * the dispatch on the bridge thread and the run on the main thread drops the call; a deferred one is answered.
     */
    @Test fun aCallWhoseDocumentIsReplacedBeforeItRunsIsDropped() {
        assertEquals(BridgeResult.Handled, dispatcher.dispatch(
            """{"method":"scrolledToOrdinal","args":["Gen.1",5],"documentGeneration":7}"""))
        bridge.admitted = NO_DOCUMENT_GENERATION // a new page was requested meanwhile
        bridge.runMain()
        assertTrue(bridge.calls.isEmpty())
    }

    @Test fun aThrowingGuardedCallRunOnTheMainThreadIsAnsweredAndContained() {
        val throwing = object : UnimplementedBibleBridge({}, {}), DocumentGenerationGate {
            override fun isAdmitted(generation: Long?) = true
            override fun runAdmitted(generation: Long?, call: () -> Unit, drop: () -> Unit) = call()
            override fun runForPageDocument(generation: Long?, call: () -> Unit, drop: () -> Unit) = call()
            override fun requestMoreToEnd(callId: Long) = error("boom")
        }
        val result = BridgeDispatcher(throwing, { answered += it }, {})
            .dispatch("""{"method":"requestMoreToEnd","args":[4],"documentGeneration":1}""")
        assertEquals(BridgeResult.Handled, result)
        assertEquals(listOf(4L), answered)
    }
}
