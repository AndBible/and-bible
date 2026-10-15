package net.bible.sharedcore.platform.speak

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import net.bible.sharedcore.log.Log
import platform.AVFAudio.*
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.darwin.NSObjectProtocol

/** Translates audio-session notifications; the consumer owns resume policy and Main-thread access. */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class IosAudioFocus(
    private val session: () -> AVAudioSession = { AVAudioSession.sharedInstance() },
    private val center: NSNotificationCenter = NSNotificationCenter.defaultCenter,
) : AudioFocus {
    private var activeSession: AVAudioSession? = null
    private var callback: ((Boolean) -> Unit)? = null
    private val observers = mutableListOf<NSObjectProtocol>()

    override fun requestAudioFocus(onFocusChange: (Boolean) -> Unit): Boolean {
        val audio = activeSession ?: session()
        val configured = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
            val category = audio.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeSpokenAudio, 0u, error.ptr)
            if (!category) {
                Log.w(TAG, "Audio session category refused: ${error.value?.localizedDescription}")
                false
            } else {
                error.value = null
                audio.setActive(true, error.ptr).also {
                    if (!it) Log.w(TAG, "Audio session activation refused: ${error.value?.localizedDescription}")
                }
            }
        }
        if (!configured) {
            removeObservers()
            return false
        }
        activeSession = audio
        callback = onFocusChange
        if (observers.isEmpty()) {
            observers += center.addObserverForName(AVAudioSessionInterruptionNotification, audio, null) { notification ->
                val info = notification?.userInfo
                when ((info?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue) {
                    AVAudioSessionInterruptionTypeBegan -> callback?.invoke(false)
                    AVAudioSessionInterruptionTypeEnded -> {
                        val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
                        if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) callback?.invoke(true)
                    }
                }
            }
            observers += center.addObserverForName(AVAudioSessionRouteChangeNotification, audio, null) { notification ->
                val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
                if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) callback?.invoke(false)
            }
        }
        return true
    }

    private fun removeObservers() {
        observers.forEach(center::removeObserver)
        observers.clear()
        callback = null
    }

    override fun abandonAudioFocus() {
        removeObservers()
        val audio = activeSession ?: return
        activeSession = null
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
            if (!audio.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error.ptr)) {
                Log.w(TAG, "Audio session deactivation refused: ${error.value?.localizedDescription}")
            }
        }
    }

    /** Calls are already delivered as audio-session interruptions. */
    override fun startPhoneCallMonitoring(onCallStateChanged: (Boolean) -> Unit) = Unit

    private companion object { const val TAG = "IosAudioFocus" }
}
