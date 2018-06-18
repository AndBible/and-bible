package net.bible.android.control.speak
import kotlinx.serialization.*

@Serializable
data class SpeakSettings(val synchronize: Boolean = false,
                         val chapterChanges: Boolean = false,
                         val continueSentences: Boolean = true,
                         val autoBookmarkLabelId: Long? = null,
                         val speakTitles: Boolean = true,
                         val playEarCons: Boolean = true,
                         val replaceDivineName: Boolean = false
                         ) {
    companion object {
        const val INVALID_LABEL_ID: Long = -1
    }
}
