package net.bible.android.platform

import net.bible.sharedlogic.platform.SentenceBreaker
import java.text.BreakIterator

object AndroidSentenceBreaker : SentenceBreaker {
    private fun iterator(text: String) = BreakIterator.getSentenceInstance().apply { setText(text) }
    override fun preceding(text: String, offset: Int) = iterator(text).preceding(offset)
    override fun following(text: String, offset: Int) = iterator(text).following(offset)
}
