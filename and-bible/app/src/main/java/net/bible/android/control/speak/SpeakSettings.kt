package net.bible.android.control.speak
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON
import net.bible.service.common.CommonUtils
import java.lang.IllegalArgumentException

@Serializable
data class SpeakSettings(@Optional val synchronize: Boolean = true,
                         @Optional val speakBookChanges: Boolean = true,
                         @Optional val speakChapterChanges: Boolean = true,
                         @Optional val speakTitles: Boolean = true,
                         @Optional val playEarconBook: Boolean = true,
                         @Optional val playEarconChapter: Boolean = false,
                         @Optional val playEarconTitles: Boolean = true,
                         @Optional val continueSentences: Boolean = true,
                         @Optional val autoBookmarkLabelId: Long? = null,
                         @Optional val replaceDivineName: Boolean = false,
                         @Optional val delayOnParagraphChanges: Boolean = true,
                         @Optional val rewindAmount: RewindAmount = RewindAmount.ONE_VERSE,
                         @Optional val autoRewindAmount: RewindAmount = RewindAmount.NONE,
                         @Optional var speed: Int = 100
                         ) {

    enum class RewindAmount {NONE, ONE_VERSE, TEN_VERSES, FULL_CHAPTER}

    companion object {
        const val INVALID_LABEL_ID: Long = -1
        const val PERSIST_SETTINGS = "SpeakSettings"

        fun fromJson(jsonString: String): SpeakSettings {
            return try {
                JSON(nonstrict = true).parse(jsonString)
            } catch (ex: SerializationException) {
                SpeakSettings()
            }
            catch (ex: IllegalArgumentException) {
                SpeakSettings()
            }
        }

        fun fromSharedPreferences(): SpeakSettings {
            val sharedPreferences = CommonUtils.getSharedPreferences()
            return fromJson(sharedPreferences.getString(PERSIST_SETTINGS, ""))
        }
    }

    fun toJson(): String {
        return JSON.stringify(this)
    }

    fun saveSharedPreferences() {
        CommonUtils.getSharedPreferences().edit().putString(PERSIST_SETTINGS, toJson()).apply()
    }
}
