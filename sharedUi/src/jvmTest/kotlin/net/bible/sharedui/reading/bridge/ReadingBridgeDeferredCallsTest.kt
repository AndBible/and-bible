package net.bible.sharedui.reading.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.SessionPaging

/** Deferred bridge calls must answer the JS promise even when the session is gone or the handler fails. */
class ReadingBridgeDeferredCallsTest {
    private class FakeHost : JsHost {
        val evaluated = mutableListOf<String>()
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            evaluated += js
            callback?.invoke("null")
        }
    }

    private val surface = object : ReadingWindowSurface {
        override fun copyText(label: String?, text: String) {}
        override fun shareText(text: String, subject: String?) {}
        override fun shareHtml(html: String, title: String) {}
        override fun stopSelection(removeRanges: Boolean) {}
        override fun toast(text: String) {}
        override fun openWebSearch(query: String) {}
    }

    private val navigator = java.lang.reflect.Proxy.newProxyInstance(
        ReadingNavigator::class.java.classLoader, arrayOf(ReadingNavigator::class.java),
    ) { _, m, _ -> error("unexpected navigator call ${m.name}") } as ReadingNavigator

    private fun session(host: FakeHost, paging: SessionPaging): BibleWindowSession {
        val channel = BibleViewJsChannel(host) {}
        channel.markClientReady()
        return BibleWindowSession(null, channel, { CoroutineScope(Dispatchers.Unconfined) }, surface, navigator).also { it.paging = paging }
    }

    private class RecordingPaging(val fail: Boolean = false) : SessionPaging {
        var calls = 0
        private fun hit() { calls++; if (fail) error("boom") }
        override fun requestMoreToBeginning(callId: Long) = hit()
        override fun requestMoreToEnd(callId: Long) = hit()
        override fun setClientReady() = hit()
        override fun parseRef(callId: Long, s: String) = hit()
    }

    @Test
    fun disposedSessionAnswersNullAndDoesNotForward() {
        val host = FakeHost()
        val paging = RecordingPaging()
        val s = session(host, paging)
        val bridge = ReadingBridge(s)
        s.dispose()
        bridge.requestMoreToEnd(7)
        bridge.parseRef(8, "x")
        bridge.refChooserDialog(9)
        assertEquals(0, paging.calls)
        assertEquals(
            listOf("bibleView.response(7, null);", "bibleView.response(8, null);", "bibleView.response(9, null);"),
            host.evaluated,
        )
    }

    @Test
    fun failingPagingHandlerStillAnswersNull() {
        val host = FakeHost()
        val bridge = ReadingBridge(session(host, RecordingPaging(fail = true)))
        bridge.requestMoreToBeginning(1)
        bridge.parseRef(2, "x")
        assertEquals(listOf("bibleView.response(1, null);", "bibleView.response(2, null);"), host.evaluated)
    }

    @Test
    fun refChooserAnswersEmptyStringWhenMissingOrDismissed() {
        val host = FakeHost()
        val s = session(host, RecordingPaging())
        val bridge = ReadingBridge(s)
        bridge.refChooserDialog(5)
        s.verseChooser = { null }
        bridge.refChooserDialog(6)
        assertEquals(listOf("bibleView.response(5, \"\");", "bibleView.response(6, \"\");"), host.evaluated)
    }

    @Test
    fun refChooserAnswersChosenVerseName() {
        val host = FakeHost()
        val s = session(host, RecordingPaging())
        s.verseChooser = { "John.3.16" }
        ReadingBridge(s).refChooserDialog(5)
        val answer = host.evaluated.single()
        assertTrue(answer.startsWith("bibleView.response(5, \"") && answer.contains("3:16"), answer)
    }
}
