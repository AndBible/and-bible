package net.bible.service.device.speak

interface SpeakTextProvider {
    val numItemsToTts: Int
    fun getStatusText(): String
    fun isMoreTextToSpeak(): Boolean
    fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean = false): SpeakCommand
    fun getTotalChars(): Long
    fun getSpokenChars(): Long
    fun pause()
    fun stop()
    fun rewind()
    fun forward()
    fun getText(utteranceId: String): String
    fun finishedUtterance(utteranceId: String)
    fun startUtterance(utteranceId: String)
    fun reset()
    fun persistState()
    fun restoreState(): Boolean
    fun clearPersistedState()
    fun prepareForContinue()
    fun savePosition(fractionCompleted: Float) {
    }
}
