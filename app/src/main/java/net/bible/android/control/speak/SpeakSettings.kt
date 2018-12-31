/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
import kotlinx.serialization.json.JSON
import net.bible.android.control.event.ABEventBus
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import java.lang.IllegalArgumentException

const val PERSIST_SETTINGS = "SpeakSettings"
const val TAG = "SpeakSettings"

@Serializer(forClass = VerseRange::class)
object VerseRangeSerializer: KSerializer<VerseRange?> {
    override fun serialize(output: Encoder, obj: VerseRange?) {
        if(obj != null) {
            output.encodeString("${obj.versification.name}::${obj.osisRef}")
        }
        else {
            output.encodeNull()
        }
    }

    override fun deserialize(input: Decoder): VerseRange? {
        val str = input.decodeString()
        val splitted = str.split("::")
        val v11n = Versifications.instance().getVersification(splitted[0])
        return VerseRangeFactory.fromString(v11n, splitted[1])
    }
}

@Serializable
data class PlaybackSettings (
        @Optional val speakChapterChanges: Boolean = true,
        @Optional val speakTitles: Boolean = true,
        @Optional val speakFootnotes: Boolean = false,
        @Optional var speed: Int = 100,

        // Bookmark related metadata.
        // Restoring bookmark from widget uses this.
        @Optional var bookId: String? = null,
        @Optional var bookmarkWasCreated: Boolean? = null,
        @Optional @Serializable(with=VerseRangeSerializer::class) var verseRange: VerseRange? = null
) {
    companion object {
        fun fromJson(jsonString: String): PlaybackSettings {
            return try {
                JSON(strictMode = false).parse(PlaybackSettings.serializer(), jsonString)
            } catch (ex: SerializationException) {
                PlaybackSettings()
            } catch (ex: IllegalArgumentException) {
                PlaybackSettings()
            }
        }
    }

    fun toJson(): String {
        return JSON.stringify(PlaybackSettings.serializer(), this)
    }
}

data class SpeakSettingsChangedEvent(val speakSettings: SpeakSettings, val updateBookmark: Boolean = false, val sleepTimerChanged: Boolean = false)

@Serializable
data class SpeakSettings(@Optional var synchronize: Boolean = true,
                         @Optional var replaceDivineName: Boolean = false,
                         @Optional var autoBookmark: Boolean = false,
                         @Optional var restoreSettingsFromBookmarks: Boolean = false,
                         @Optional var playbackSettings: PlaybackSettings = PlaybackSettings(),
                         @Optional var sleepTimer: Int = 0,
                         @Optional var lastSleepTimer: Int = 10,
                         @Optional var multiTranslation: Boolean = false,
                         // General book speak settings
                         @Optional var queue: Boolean = true,
                         @Optional var repeat: Boolean = false,
                         @Optional var numPagesToSpeakId: Int = 0
                         ) {
    enum class RewindAmount {NONE, ONE_VERSE, TEN_VERSES, SMART}

    private fun toJson(): String {
        return JSON.stringify(SpeakSettings.serializer(), this)
    }

    fun makeCopy(): SpeakSettings {
        val s = this.copy()
        s.playbackSettings = this.playbackSettings.copy()
        return s
    }

    fun save(updateBookmark: Boolean = false) {
        if(currentSettings?.equals(this) != true) {
            CommonUtils.getSharedPreferences().edit().putString(PERSIST_SETTINGS, toJson()).apply()
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
                JSON(strictMode = false).parse(SpeakSettings.serializer(), jsonString)
            } catch (ex: SerializationException) {
                SpeakSettings()
            } catch (ex: IllegalArgumentException) {
                SpeakSettings()
            }
        }

        fun load(): SpeakSettings {
            val rv = currentSettings?.makeCopy()?: {
                val sharedPreferences = CommonUtils.getSharedPreferences()
                val settings = fromJson(sharedPreferences.getString(PERSIST_SETTINGS, ""))
                settings }()
            Log.d(TAG, "SpeakSettings loaded! $rv")
            return rv
        }
    }
}
