package net.bible.service.device.speak

interface SpeakTextProvider {
    fun isMoreTextToSpeak(): Boolean
    fun getNextTextToSpeak(): String
    fun getTotalChars(): Long
    fun getSpokenChars(): Long
    fun pause(fractionCompleted: Float)
    fun stop()
    fun rewind()
    fun forward()
    fun finishedUtterance(utteranceId: String)
    fun reset()
    fun persistState()
    fun restoreState(): Boolean
    fun clearPersistedState()
    fun prepareForContinue()
}
