package net.bible.android.control.speak
import android.util.Log
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON
import net.bible.service.common.CommonUtils
import java.lang.IllegalArgumentException

const val PERSIST_SETTINGS = "SpeakSettings"
const val INVALID_LABEL_ID: Long = -1
const val TAG = "SpeakSettings"

@Serializable
data class PlaybackSettings (
                         @Optional val speakBookChanges: Boolean = true,
                         @Optional val speakChapterChanges: Boolean = true,
                         @Optional val speakTitles: Boolean = true,
                         @Optional val playEarconBook: Boolean = true,
                         @Optional val playEarconChapter: Boolean = false,
                         @Optional val playEarconTitles: Boolean = true,
                         @Optional var speed: Int = 100
) {
    companion object {
        fun fromJson(jsonString: String): PlaybackSettings {
            return try {
                JSON(nonstrict = true).parse(jsonString)
            } catch (ex: SerializationException) {
                PlaybackSettings()
            } catch (ex: IllegalArgumentException) {
                PlaybackSettings()
            }
        }
    }

    fun toJson(): String {
        return JSON.stringify(this)
    }
}

@Serializable
data class SpeakSettings(@Optional val synchronize: Boolean = true,
                         @Optional val continueSentences: Boolean = true,
                         @Optional val autoBookmarkLabelId: Long? = null,
                         @Optional val replaceDivineName: Boolean = false,
                         @Optional val delayOnParagraphChanges: Boolean = true,
                         @Optional val rewindAmount: RewindAmount = RewindAmount.ONE_VERSE,
                         @Optional val autoRewindAmount: RewindAmount = RewindAmount.NONE,
                         @Optional val restoreSettingsFromBookmarks: Boolean = false,
                         @Optional var playbackSettings: PlaybackSettings = PlaybackSettings()
                         ) {
    enum class RewindAmount {NONE, ONE_VERSE, TEN_VERSES, FULL_CHAPTER}

    fun toJson(): String {
        return JSON.stringify(this)
    }

    fun saveSharedPreferences() {
        CommonUtils.getSharedPreferences().edit().putString(PERSIST_SETTINGS, toJson()).apply()
        Log.d(TAG, "SpeakSettings saved! $this")
    }

    companion object {
        fun fromJson(jsonString: String): SpeakSettings {
            return try {
                JSON(nonstrict = true).parse(jsonString)
            } catch (ex: SerializationException) {
                SpeakSettings()
            } catch (ex: IllegalArgumentException) {
                SpeakSettings()
            }
        }

        fun fromSharedPreferences(): SpeakSettings {
            val sharedPreferences = CommonUtils.getSharedPreferences()
            val settings = fromJson(sharedPreferences.getString(PERSIST_SETTINGS, ""))
            Log.d(TAG, "SpeakSettings loaded! $settings")
            return settings
        }
    }
}
