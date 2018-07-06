package net.bible.android.control.speak
import android.util.Log
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON
import net.bible.android.control.event.ABEventBus
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

data class SpeakSettingsChangedEvent(val speakSettings: SpeakSettings, val updateBookmark: Boolean = false, val sleepTimerChanged: Boolean = false)

@Serializable
data class SpeakSettings(@Optional val synchronize: Boolean = true,
                         @Optional val autoBookmarkLabelId: Long? = null,
                         @Optional val replaceDivineName: Boolean = false,
                         @Optional val autoRewindAmount: RewindAmount = RewindAmount.NONE,
                         @Optional val restoreSettingsFromBookmarks: Boolean = false,
                         @Optional var playbackSettings: PlaybackSettings = PlaybackSettings(),
                         @Optional var sleepTimer: Int = 0,
                         @Optional var lastSleepTimer: Int = 10,
                         // General book speak settings
                         @Optional var queue: Boolean = true,
                         @Optional var repeat: Boolean = false,
                         @Optional var numPagesToSpeakId: Int = 0
                         ) {
    enum class RewindAmount {NONE, ONE_VERSE, TEN_VERSES, SMART}

    private fun toJson(): String {
        return JSON.stringify(this)
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
                JSON(nonstrict = true).parse(jsonString)
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
