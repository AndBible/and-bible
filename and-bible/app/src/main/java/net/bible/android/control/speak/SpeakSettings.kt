package net.bible.android.control.speak
import kotlinx.serialization.*

@Serializable
data class SpeakSettings(val synchronize: Boolean = true,

                         val speakBookChanges: Boolean = true,
                         val speakChapterChanges: Boolean = true,
                         val speakTitles: Boolean = true,

                         val playEarconBook: Boolean = true,
                         val playEarconChapter: Boolean = false,
                         val playEarconTitles: Boolean = true,

                         val continueSentences: Boolean = true,
                         val autoBookmarkLabelId: Long? = null,
                         val replaceDivineName: Boolean = false,
                         val delayOnParagraphChanges: Boolean = true,
                         val rewindAmount: RewindAmount = RewindAmount.ONE_VERSE,
                         val autoRewindAmount: RewindAmount = RewindAmount.NONE
                         ) {

    enum class RewindAmount {NONE, ONE_VERSE, TEN_VERSES, FULL_CHAPTER}

    companion object {
        const val INVALID_LABEL_ID: Long = -1
    }
}
