/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.speak
import android.util.Log
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import net.bible.android.control.event.ABEventBus
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.JSON_CONFIG
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import java.lang.IllegalArgumentException

const val PERSIST_SETTINGS = "SpeakSettings"
const val TAG = "SpeakSettings"

@Serializer(forClass = VerseRange::class)
object VerseRangeSerializer: KSerializer<VerseRange?> {
    override fun serialize(encoder: Encoder, obj: VerseRange?) {
        if(obj != null) {
            encoder.encodeString("${obj.versification.name}::${obj.osisRef}")
        }
        else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): VerseRange? {
        val str = decoder.decodeString()
        val splitted = str.split("::")
        val v11n = Versifications.instance().getVersification(splitted[0])
        return VerseRangeFactory.fromString(v11n, splitted[1])
    }
}

@Serializable
data class PlaybackSettings (
        val speakChapterChanges: Boolean = true,
        val speakTitles: Boolean = true,
        val speakFootnotes: Boolean = false,
        var speed: Int = 100,

        // Bookmark related metadata.
        // Restoring bookmark from widget uses this.
        var bookId: String? = null,
        var bookmarkWasCreated: Boolean? = null,
        @Serializable(with=VerseRangeSerializer::class) var verseRange: VerseRange? = null
) {
    companion object {

        fun fromJson(jsonString: String): PlaybackSettings {
            return try {
                Json(JSON_CONFIG).parse(PlaybackSettings.serializer(), jsonString)
            } catch (ex: SerializationException) {
                PlaybackSettings()
            } catch (ex: IllegalArgumentException) {
                PlaybackSettings()
            }
        }
    }

    fun toJson(): String {
        return Json(JSON_CONFIG).stringify(PlaybackSettings.serializer(), this)
    }
}

data class SpeakSettingsChangedEvent(val speakSettings: SpeakSettings, val updateBookmark: Boolean = false, val sleepTimerChanged: Boolean = false)

@Serializable
data class SpeakSettings(var synchronize: Boolean = true,
                         var replaceDivineName: Boolean = false,
                         var autoBookmark: Boolean = false,
                         var restoreSettingsFromBookmarks: Boolean = false,
                         var playbackSettings: PlaybackSettings = PlaybackSettings(),
                         var sleepTimer: Int = 0,
                         var lastSleepTimer: Int = 10,
                         // General book speak settings
                         var queue: Boolean = true,
                         var repeat: Boolean = false,
                         var numPagesToSpeakId: Int = 0
                         ) {
    enum class RewindAmount {NONE, ONE_VERSE, TEN_VERSES, SMART}

    private fun toJson(): String {
        return Json(JSON_CONFIG).stringify(SpeakSettings.serializer(), this)
    }

    fun makeCopy(): SpeakSettings {
        val s = this.copy()
        s.playbackSettings = this.playbackSettings.copy()
        return s
    }

    fun save(updateBookmark: Boolean = false) {
        if(currentSettings?.equals(this) != true) {
            CommonUtils.sharedPreferences.edit().putString(PERSIST_SETTINGS, toJson()).apply()
            Log.d(TAG, "SpeakSettings saved! $this")
            val oldSettings = currentSettings
            currentSettings = this.makeCopy()
            ABEventBus.getDefault().post(SpeakSettingsChangedEvent(this,
                    updateBookmark && oldSettings?.playbackSettings?.equals(this.playbackSettings) != true,
                     oldSettings?.sleepTimer != this.sleepTimer))
        }
    }

    fun save() {
        save(false)
    }

    companion object {
        private var currentSettings: SpeakSettings? = null

        private fun fromJson(jsonString: String): SpeakSettings {
            return try {
                Json(JSON_CONFIG).parse(SpeakSettings.serializer(), jsonString)
            } catch (ex: SerializationException) {
                SpeakSettings()
            } catch (ex: IllegalArgumentException) {
                SpeakSettings()
            }
        }

        fun load(): SpeakSettings {
            val rv = currentSettings?.makeCopy()?: {
                val sharedPreferences = CommonUtils.sharedPreferences
                val settings = fromJson(sharedPreferences.getString(PERSIST_SETTINGS, "")!!)
                settings }()
            Log.d(TAG, "SpeakSettings loaded! $rv")
            return rv
        }
    }
}
