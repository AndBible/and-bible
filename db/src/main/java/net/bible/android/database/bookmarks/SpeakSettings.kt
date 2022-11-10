/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.bookmarks

import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.bible.android.database.json
import org.crosswire.jsword.passage.NoSuchVerseException

import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import java.lang.IllegalArgumentException

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
        return try {VerseRangeFactory.fromString(v11n, splitted[1]) } catch (e: NoSuchVerseException) {null}
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
                json.decodeFromString(serializer(), jsonString)
            } catch (ex: SerializationException) {
                PlaybackSettings()
            } catch (ex: IllegalArgumentException) {
                PlaybackSettings()
            }
        }
    }

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}

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

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }

    fun makeCopy(): SpeakSettings {
        val s = this.copy()
        s.playbackSettings = this.playbackSettings.copy()
        return s
    }

    companion object {
        var currentSettings: SpeakSettings? = null

        fun fromJson(jsonString: String): SpeakSettings {
            return try {
                json.decodeFromString(serializer(), jsonString)
            } catch (ex: SerializationException) {
                SpeakSettings()
            } catch (ex: IllegalArgumentException) {
                SpeakSettings()
            }
        }
    }
}
