package net.bible.service.device.speak

import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.speak.SpeakSettingsChangedEvent

interface SpeakTextProvider {
	val numItemsToTts: Int
	fun getStatusText(showFlag: Int): String
	fun isMoreTextToSpeak(): Boolean
	fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean = false): SpeakCommand
	fun getTotalChars(): Long
	fun getSpokenChars(): Long
	fun pause()
	fun stop()
	fun rewind(amount: SpeakSettings.RewindAmount?)
	fun forward(amount: SpeakSettings.RewindAmount?)
	fun getText(utteranceId: String): String
	fun finishedUtterance(utteranceId: String)
	fun startUtterance(utteranceId: String)
	fun reset()
	fun persistState()
	fun restoreState(): Boolean
	fun clearPersistedState()
	fun prepareForStartSpeaking()
	fun savePosition(fractionCompleted: Float)
	fun updateSettings(speakSettingsChangedEvent: SpeakSettingsChangedEvent) {}
}
