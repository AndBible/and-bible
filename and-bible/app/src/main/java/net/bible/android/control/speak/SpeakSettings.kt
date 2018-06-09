package net.bible.android.control.speak
import kotlinx.serialization.*

@Serializable
data class SpeakSettings(val synchronize: Boolean = false,
                         val chapterChanges: Boolean = false,
                         val continueSentences: Boolean = true,
                         val autoBookmarkLabel: String? = null
                         )
