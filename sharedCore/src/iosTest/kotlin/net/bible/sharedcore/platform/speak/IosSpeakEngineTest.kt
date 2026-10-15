package net.bible.sharedcore.platform.speak

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.timeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosSpeakEngineTest {
    private class RecordingListener(val onDone: (String) -> Unit = {}) : SpeakEngineListener {
        val events = mutableListOf<String>()
        val initialized = mutableListOf<Boolean>()
        override fun onInit(success: Boolean) { checkMain(); initialized += success }
        override fun onUtteranceStart(utteranceId: String) { checkMain(); events += "start:$utteranceId" }
        override fun onUtteranceDone(utteranceId: String) {
            checkMain()
            events += "done:$utteranceId"
            onDone(utteranceId)
        }
        override fun onUtteranceError(utteranceId: String) { checkMain(); events += "error:$utteranceId" }
        private fun checkMain() = assertTrue(NSThread.isMainThread)
        fun awaitEvents(n: Int, timeoutMs: Long = 15000) {
            val deadline = NSDate().timeIntervalSince1970 + timeoutMs / 1000.0
            while (events.size < n && NSDate().timeIntervalSince1970 < deadline) pump(0.05)
            assertTrue(events.size >= n, "Expected $n events, got $events")
        }
    }

    private fun withEngine(block: (IosSpeakEngine, RecordingListener) -> Unit) {
        assertTrue(NSThread.isMainThread, "The iOS consumer must call the engine on Main")
        val path = (NSTemporaryDirectory() + "speech-${platform.Foundation.NSUUID().UUIDString}.wav").toPath()
        // PCM RIFF: 0.1 seconds, 8 kHz mono, 16-bit samples (1600 data bytes).
        val header = byteArrayOf(
            82, 73, 70, 70, 100, 6, 0, 0, 87, 65, 86, 69,
            102, 109, 116, 32, 16, 0, 0, 0, 1, 0, 1, 0,
            64, 31, 0, 0, -128, 62, 0, 0, 2, 0, 16, 0,
            100, 97, 116, 97, 64, 6, 0, 0,
        )
        FileSystem.SYSTEM.sink(path).buffer().use { it.write(header); it.write(ByteArray(1600)) }
        val listener = RecordingListener()
        val engine = IosSpeakEngine(listener, mapOf("[pre-title]" to path.toString()))
        try { block(engine, listener) } finally { engine.shutdown(); FileSystem.SYSTEM.delete(path) }
    }

    @Test fun queuedSpeechKeepsEnqueueVoiceAndRate() {
        val spoken = mutableListOf<platform.AVFAudio.AVSpeechUtterance>()
        val synthesizer = object : platform.AVFAudio.AVSpeechSynthesizer() {
            override fun speakUtterance(utterance: platform.AVFAudio.AVSpeechUtterance) { spoken += utterance }
        }
        val engine = IosSpeakEngine(RecordingListener(), emptyMap(), synthesizer = synthesizer)
        try {
            engine.playSilence(50, "barrier")
            engine.setLanguage("en-US")
            engine.setSpeechRate(0.5f)
            engine.speakText("first", "1")
            engine.setLanguage("fi-FI")
            engine.setSpeechRate(1.5f)
            engine.speakText("second", "2")
            pump(0.2)
            assertEquals(1, spoken.size)
            val first = spoken.single()
            assertEquals(platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate * 0.5f, first.rate)
            assertEquals(AVSpeechSynthesisVoice.voiceWithLanguage("en-US")?.identifier, first.voice?.identifier)
            synthesizer.delegate?.speechSynthesizer(synthesizer, didFinishSpeechUtterance = first)
            assertEquals(2, spoken.size)
            assertEquals(platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate * 1.5f, spoken.last().rate)
            assertEquals(AVSpeechSynthesisVoice.voiceWithLanguage("fi-FI")?.identifier, spoken.last().voice?.identifier)
        } finally { engine.shutdown() }
    }

    @Test fun queueOrderIsPreserved() = withEngine { engine, listener ->
        engine.speakText("a", "1")
        engine.playEarcon("[pre-title]", 0.2f, "2")
        engine.playSilence(50, "3")
        engine.speakText("b", "4")
        val deadline = NSDate().timeIntervalSince1970 + 15.0
        while (listener.events.count { it.startsWith("done:") || it.startsWith("error:") } < 4 &&
            NSDate().timeIntervalSince1970 < deadline) pump(0.05)
        val terminal = listener.events.filter { it.startsWith("done:") || it.startsWith("error:") }
        if (AVSpeechSynthesisVoice.speechVoices().isNotEmpty()) {
            assertEquals(listOf("done:1", "done:2", "done:3", "done:4"), terminal)
        } else {
            assertEquals(listOf("1", "2", "3", "4"), terminal.map { it.substringAfter(':') })
            assertTrue(terminal.indexOf("done:2") < terminal.indexOf("done:3"))
            assertTrue("done:2" in terminal && "done:3" in terminal)
        }
        assertEquals(listOf(true), listener.initialized)
    }

    @Test fun flushDropsQueuedItemsDuringSilence() = withEngine { engine, listener ->
        engine.playSilence(500, "1")
        engine.speakText("x", "2")
        engine.flush()
        pump(1.0)
        assertEquals(emptyList(), listener.events, "Even the posted start must be invalidated")
        engine.playSilence(10, "3")
        listener.awaitEvents(2)
        assertEquals(listOf("start:3", "done:3"), listener.events)
    }

    @Test fun flushDropsQueuedItemsDuringEarcon() = withEngine { engine, listener ->
        engine.playEarcon("[pre-title]", 0.2f, "1")
        engine.speakText("x", "2")
        engine.flush()
        engine.playSilence(200, "3")
        listener.awaitEvents(2)
        pump(0.3)
        assertEquals(listOf("start:3", "done:3"), listener.events)
    }

    @Test fun flushAfterStartSuppressesCompletion() = withEngine { engine, listener ->
        engine.playSilence(500, "1")
        listener.awaitEvents(1)
        engine.flush()
        pump(0.7)
        assertEquals(listOf("start:1"), listener.events)
    }

    @Test fun flushFromListenerDropsAlreadyPostedNextStart() {
        lateinit var engine: IosSpeakEngine
        val listener = RecordingListener { id -> if (id == "1") engine.flush() }
        engine = IosSpeakEngine(listener, emptyMap())
        try {
            engine.playSilence(0, "1")
            engine.playSilence(50, "2")
            listener.awaitEvents(2)
            pump(0.2)
            assertEquals(listOf("start:1", "done:1"), listener.events)
        } finally { engine.shutdown() }
    }

    @Test fun missingEarconPlaysZeroSilence() = withEngine { engine, listener ->
        engine.playEarcon("[unknown]", 1f, "1")
        listener.awaitEvents(2)
        assertEquals(listOf("start:1", "done:1"), listener.events)
    }

    @Test fun invalidEarconReportsErrorAndAdvancesQueue() {
        val listener = RecordingListener()
        val engine = IosSpeakEngine(listener, mapOf("bad" to "/no-such-speech-earcon.wav"))
        try {
            engine.playEarcon("bad", 1f, "1")
            engine.playSilence(10, "2")
            listener.awaitEvents(4)
            assertEquals(listOf("start:1", "error:1", "start:2", "done:2"), listener.events)
        } finally { engine.shutdown() }
    }

    @Test fun shutdownSuppressesPostedCallbacksAndFurtherWork() = withEngine { engine, listener ->
        engine.playSilence(10, "1")
        engine.shutdown()
        engine.playSilence(0, "2")
        pump(0.2)
        assertTrue(listener.events.isEmpty())
        assertTrue(listener.initialized.isEmpty())
    }

    @Test fun stopFlushesAndAllowsReuse() = withEngine { engine, listener ->
        engine.playSilence(500, "1")
        engine.stop()
        engine.playSilence(0, "2")
        listener.awaitEvents(2)
        assertEquals(listOf("start:2", "done:2"), listener.events)
    }

    @Test fun unknownLanguageIsNotSupported() = withEngine { engine, _ ->
        assertEquals(SpeakLanguageResult.NOT_SUPPORTED, engine.setLanguage("xx-YY"))
        if (AVSpeechSynthesisVoice.speechVoices().isNotEmpty()) {
            assertEquals(SpeakLanguageResult.AVAILABLE, engine.setLanguage("en"))
        }
        assertEquals(4000, engine.maxSpeechInputLength)
    }

    @Test fun extremeRatesReachTerminalEvents() = withEngine { engine, listener ->
        engine.setSpeechRate(100f)
        engine.speakText("a", "1")
        engine.setSpeechRate(0f)
        engine.speakText("b", "2")
        val deadline = NSDate().timeIntervalSince1970 + 15.0
        while (listener.events.count { it.startsWith("done:") || it.startsWith("error:") } < 2 &&
            NSDate().timeIntervalSince1970 < deadline) pump(0.05)
        assertEquals(listOf("1", "2"), listener.events.filter {
            it.startsWith("done:") || it.startsWith("error:")
        }.map { it.substringAfter(':') })
        assertFalse(listener.events.isEmpty())
    }

    companion object {
        private fun pump(seconds: Double) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(seconds))
        }
    }
}
