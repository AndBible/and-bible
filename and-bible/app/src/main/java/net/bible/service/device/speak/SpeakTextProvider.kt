package net.bible.service.device.speak

import net.bible.service.format.osistohtml.osishandlers.SpeakCommand

interface SpeakTextProvider {
    val numItemsToTts: Int
    fun isMoreTextToSpeak(): Boolean
    fun getNextSpeakCommand(utteranceId: String): SpeakCommand
    fun getTotalChars(): Long
    fun getSpokenChars(): Long
    fun pause(fractionCompleted: Float)
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
}
