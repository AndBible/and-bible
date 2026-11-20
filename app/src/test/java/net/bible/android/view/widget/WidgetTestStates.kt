package net.bible.android.view.widget

import net.bible.android.control.widget.ListeningRow
import net.bible.android.control.widget.SpeakPhase
import net.bible.android.control.widget.SpeakWidgetState

/** A speaking Psalm 23:1 state; tests copy what they vary. */
fun speakingState() = SpeakWidgetState(
    phase = SpeakPhase.SPEAKING, title = "Psalm 23", reference = "Psalm 23:1", translation = "WEB", percent = 34,
    verseText = "The LORD is my shepherd; I shall not want.", nextVerseText = "He makes me lie down in green pastures.",
    statusLine = "Psalm 23:1 · 34 % · WEB", sleepMinutesLeft = 18, listening = emptyList(), autoBookmarkOn = true,
)

fun stoppedState() = speakingState().copy(
    phase = SpeakPhase.STOPPED, title = null, verseText = "", nextVerseText = null,
    statusLine = "Continue from Psalm 23:1 · WEB", sleepMinutesLeft = null,
)

fun row(reference: String, translation: String, percent: Int? = 40, repeats: Boolean = false, current: Boolean = false, id: String = reference) =
    ListeningRow(bookmarkId = id, reference = reference, translation = translation, repeats = repeats, percent = percent, current = current)
