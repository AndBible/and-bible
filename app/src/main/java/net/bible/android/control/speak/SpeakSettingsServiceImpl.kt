/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.speak

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.common.AdvancedSpeakSettings
import net.bible.sharedcore.speak.AdvancedSpeakVd
import net.bible.sharedcore.speak.SpeakPlaybackVd
import net.bible.sharedcore.speak.SpeakSettingsService

/**
 * Android impl of [SpeakSettingsService]. Reads/writes the classic DB-backed [SpeakSettings] and the
 * global [AdvancedSpeakSettings]; every playback mutation saves through the classic
 * `SpeakSettings.save(updateBookmark = true)` path so the [SpeakSettingsChangedEvent] broadcast is
 * unchanged, and re-emits [playback] when that event fires. Advanced settings don't broadcast, so
 * their setters refresh [advanced] directly. Registered as a Koin single (lives for the process).
 */
class SpeakSettingsServiceImpl : SpeakSettingsService {
    private val _playback = MutableStateFlow(readPlayback())
    override val playback: StateFlow<SpeakPlaybackVd> = _playback.asStateFlow()

    private val _advanced = MutableStateFlow(readAdvanced())
    override val advanced: StateFlow<AdvancedSpeakVd> = _advanced.asStateFlow()

    init {
        ABEventBus.register(this) {
            onMain<SpeakSettingsChangedEvent> { _playback.value = readPlayback() }
        }
    }

    private fun readPlayback(): SpeakPlaybackVd {
        val s = SpeakSettings.load()
        val p = s.playbackSettings
        return SpeakPlaybackVd(
            speedPercent = p.speed,
            speakChapterChanges = p.speakChapterChanges,
            speakTitles = p.speakTitles,
            speakFootnotes = p.speakFootnotes,
            sleepTimerMinutes = s.sleepTimer,
            repeatRangeName = p.verseRange?.name,
        )
    }

    private fun readAdvanced() = AdvancedSpeakVd(
        synchronize = AdvancedSpeakSettings.synchronize,
        replaceDivineName = AdvancedSpeakSettings.replaceDivineName,
        autoBookmark = AdvancedSpeakSettings.autoBookmark,
        restoreSettingsFromBookmarks = AdvancedSpeakSettings.restoreSettingsFromBookmarks,
    )

    /** Load → mutate the one field → save; the save posts SpeakSettingsChangedEvent → _playback refresh. */
    private inline fun mutatePlayback(block: (SpeakSettings) -> Unit) {
        val s = SpeakSettings.load()
        block(s)
        s.save(updateBookmark = true)
    }

    override fun setSpeed(percent: Int) = mutatePlayback { it.playbackSettings = it.playbackSettings.copy(speed = percent) }
    override fun setSpeakChapterChanges(on: Boolean) = mutatePlayback { it.playbackSettings = it.playbackSettings.copy(speakChapterChanges = on) }
    override fun setSpeakTitles(on: Boolean) = mutatePlayback { it.playbackSettings = it.playbackSettings.copy(speakTitles = on) }
    override fun setSpeakFootnotes(on: Boolean) = mutatePlayback { it.playbackSettings = it.playbackSettings.copy(speakFootnotes = on) }
    override fun clearRepeatRange() = mutatePlayback { it.playbackSettings = it.playbackSettings.copy(verseRange = null) }

    override fun setSynchronize(on: Boolean) { AdvancedSpeakSettings.synchronize = on; _advanced.value = readAdvanced() }
    override fun setReplaceDivineName(on: Boolean) { AdvancedSpeakSettings.replaceDivineName = on; _advanced.value = readAdvanced() }
    override fun setAutoBookmark(on: Boolean) { AdvancedSpeakSettings.autoBookmark = on; _advanced.value = readAdvanced() }
    override fun setRestoreSettingsFromBookmarks(on: Boolean) { AdvancedSpeakSettings.restoreSettingsFromBookmarks = on; _advanced.value = readAdvanced() }
}
