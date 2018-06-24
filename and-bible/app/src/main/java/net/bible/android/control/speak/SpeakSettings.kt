package net.bible.android.control.speak
import android.util.Log
import de.greenrobot.event.EventBus
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
                         @Optional val autoBookmarkLabelId: Long? = null,
                         @Optional val replaceDivineName: Boolean = false,
                         @Optional val autoRewindAmount: RewindAmount = RewindAmount.NONE,
                         @Optional val restoreSettingsFromBookmarks: Boolean = false,
                         @Optional var playbackSettings: PlaybackSettings = PlaybackSettings(),
                         @Optional var sleepTimer: Int = 0,
                         @Optional var lastSleepTimer: Int = 10
                         ) {
    enum class RewindAmount {NONE, ONE_VERSE, TEN_VERSES, SMART}

    private fun toJson(): String {
        return JSON.stringify(this)
    }

    fun save() {
        if(currentSettings?.equals(this) != true) {
            CommonUtils.getSharedPreferences().edit().putString(PERSIST_SETTINGS, toJson()).apply()
            Log.d(TAG, "SpeakSettings saved! $this")
            EventBus.getDefault().post(this)
            val settings = this.copy()
            settings.playbackSettings = playbackSettings.copy()
            currentSettings = settings
        }
    }

    companion object {
        var currentSettings: SpeakSettings? = null

        private fun fromJson(jsonString: String): SpeakSettings {
            return try {
                JSON(nonstrict = true).parse(jsonString)
            } catch (ex: SerializationException) {
                SpeakSettings()
            } catch (ex: IllegalArgumentException) {
                SpeakSettings()
            }
        }

        fun load(): SpeakSettings {
            val rv = currentSettings ?: {
                val sharedPreferences = CommonUtils.getSharedPreferences()
                val settings = fromJson(sharedPreferences.getString(PERSIST_SETTINGS, ""))
                settings }()
            Log.d(TAG, "SpeakSettings loaded! $rv")
            return rv
        }
    }
}
