package net.bible.sharedcore.platform.speak

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import net.bible.sharedcore.log.Log
import platform.AVFAudio.*
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * Adapts the separate Apple speech/audio APIs to [SpeakEngine]'s single FIFO.
 * Construction, all public calls and all state access must happen on Main; the iOS consumer
 * owns that dispatching (unlike Android's handler-thread consumer). Listener delivery is posted
 * to Main and rechecks the flush generation there, not just when scheduling the callback.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSpeakEngine(
    listener: SpeakEngineListener,
    private val earconFiles: Map<String, String>,
    private val deviceLocaleTag: () -> String = { NSLocale.currentLocale.localeIdentifier.replace('_', '-') },
) : SpeakEngine {
    private sealed class Item(val id: String) {
        class Utterance(val text: String, id: String) : Item(id)
        class Earcon(val path: String, val volume: Float, id: String) : Item(id)
        class Silence(val ms: Long, id: String) : Item(id)
    }

    private var listener: SpeakEngineListener? = listener
    private val queue = ArrayDeque<Item>()
    private var active: Item? = null
    private var generation = 0L
    private var rate = AVSpeechUtteranceDefaultSpeechRate
    private var voice: AVSpeechSynthesisVoice? = null
    private var player: AVAudioPlayer? = null
    private var playerDelegate: AVAudioPlayerDelegateProtocol? = null
    private val utterances = HashMap<AVSpeechUtterance, Pair<Item, Long>>()
    private var synthesizer: AVSpeechSynthesizer? = AVSpeechSynthesizer()
    // Apple delegate properties are weak; retain both delegates for their complete operation lifetime.
    private val speechDelegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didStartSpeechUtterance: AVSpeechUtterance) {
            val (item, gen) = utterances[didStartSpeechUtterance] ?: return
            notify(gen) { it.onUtteranceStart(item.id) }
        }
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
            speechFinished(didFinishSpeechUtterance, error = false)
        }
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) {
            speechFinished(didCancelSpeechUtterance, error = true)
        }
    }

    init {
        synthesizer?.delegate = speechDelegate
        dispatch_async(dispatch_get_main_queue()) { this.listener?.onInit(synthesizer != null) }
    }

    override val maxSpeechInputLength = 4000
    override fun speakText(text: String, utteranceId: String) = enqueue(Item.Utterance(text, utteranceId))
    override fun playEarcon(earcon: String, volume: Float, utteranceId: String) {
        val path = earconFiles[earcon]
        enqueue(if (path == null) Item.Silence(0, utteranceId) else Item.Earcon(path, volume, utteranceId))
    }
    override fun playSilence(durationMs: Long, utteranceId: String) = enqueue(Item.Silence(durationMs, utteranceId))

    override fun setLanguage(languageTag: String): SpeakLanguageResult {
        voice = speakVoiceCandidates(languageTag, deviceLocaleTag()).firstNotNullOfOrNull { candidate ->
            AVSpeechSynthesisVoice.voiceWithLanguage(candidate)?.takeUnless {
                // voiceTraits is newer than our iOS 16 deployment target.
                it.respondsToSelector(NSSelectorFromString("voiceTraits")) &&
                    it.voiceTraits and (AVSpeechSynthesisVoiceTraitIsNoveltyVoice or AVSpeechSynthesisVoiceTraitIsPersonalVoice) != 0uL
            }
        }
        return if (voice == null) SpeakLanguageResult.NOT_SUPPORTED else SpeakLanguageResult.AVAILABLE
    }

    override fun setSpeechRate(rate: Float) {
        this.rate = (AVSpeechUtteranceDefaultSpeechRate * rate)
            .coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
    }

    override fun flush() {
        generation++
        queue.clear()
        active = null
        utterances.clear()
        synthesizer?.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        releasePlayer()
    }
    override fun stop() = flush()
    override fun shutdown() {
        flush()
        synthesizer?.delegate = null
        synthesizer = null
        voice = null
        listener = null
    }

    private fun enqueue(item: Item) {
        if (synthesizer == null) return
        queue.addLast(item)
        if (active == null) next()
    }

    private fun next() {
        val item = queue.removeFirstOrNull() ?: return
        active = item
        val gen = generation
        try {
            when (item) {
                is Item.Utterance -> {
                    val utterance = AVSpeechUtterance.speechUtteranceWithString(item.text)
                    utterance.rate = rate
                    voice?.let { utterance.voice = it }
                    utterances[utterance] = item to gen
                    synthesizer?.speakUtterance(utterance) ?: done(item, gen, error = true)
                }
                is Item.Earcon -> {
                    notify(gen) { it.onUtteranceStart(item.id) }
                    val audio = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(item.path), error = null)
                    player = audio
                    playerDelegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
                        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
                            done(item, gen, error = !successfully)
                        }
                        override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
                            Log.e(TAG, "Earcon decode error: $error")
                            done(item, gen, error = true)
                        }
                    }
                    audio.delegate = playerDelegate
                    audio.volume = item.volume
                    if (!audio.play()) {
                        Log.e(TAG, "Earcon player refused playback")
                        done(item, gen, error = true)
                    }
                }
                is Item.Silence -> {
                    notify(gen) { it.onUtteranceStart(item.id) }
                    // Clamp conversion to avoid wrapping dispatch_time's nanosecond delta.
                    val nanos = item.ms.coerceIn(0, Long.MAX_VALUE / 1_000_000) * 1_000_000
                    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, nanos), dispatch_get_main_queue()) {
                        done(item, gen, error = false)
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not start speech queue item", error)
            done(item, gen, error = true)
        }
    }

    private fun speechFinished(utterance: AVSpeechUtterance, error: Boolean) {
        val (item, gen) = utterances.remove(utterance) ?: return
        done(item, gen, error)
    }

    private fun done(item: Item, gen: Long, error: Boolean) {
        if (gen != generation || active !== item) return
        if (item is Item.Earcon) releasePlayer()
        active = null
        notify(gen) { if (error) it.onUtteranceError(item.id) else it.onUtteranceDone(item.id) }
        next()
    }

    private fun releasePlayer() {
        player?.delegate = null
        player?.stop()
        player = null
        playerDelegate = null
    }

    private fun notify(gen: Long, callback: (SpeakEngineListener) -> Unit) {
        dispatch_async(dispatch_get_main_queue()) {
            if (gen == generation) listener?.let(callback)
        }
    }

    private companion object { const val TAG = "IosSpeakEngine" }
}
