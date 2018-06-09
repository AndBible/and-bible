package net.bible.android.control.speak
import kotlinx.serialization.*

@Serializable
data class SpeakSettings(val synchronize: Boolean = false,
                         val chapterChanges: Boolean = false,
                         val continueSentences: Boolean = true,
                         val autoBookmarkLabelId: Long? = null
                         ) {
    companion object {
        const val INVALID_LABEL_ID: Long = -1
    }
}
