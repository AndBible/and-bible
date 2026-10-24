package net.bible.sharedcore.platform.speak

/** Exclusive audio output for speech, and the platform's signals that it was lost or regained. */
interface AudioFocus {
    /** @return false when the platform refuses; [onFocusChange] later reports transient loss (false) and regain (true). */
    fun requestAudioFocus(onFocusChange: (gained: Boolean) -> Unit): Boolean
    fun abandonAudioFocus()
    /** Android: telephony state. iOS: no-op, a call arrives through [requestAudioFocus]'s callback. */
    fun startPhoneCallMonitoring(onCallStateChanged: (active: Boolean) -> Unit)
}
