package net.bible.service.device.speak

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.text.BreakIterator
import java.util.regex.Pattern

/** [LegacySpeakTextProvider] text splitting against the original java.util.regex / BreakIterator code. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LegacySpeakBreakTest {
    private val provider = LegacySpeakTextProvider(
        net.bible.test.testAppSettings(), net.bible.test.testCoreStrings(), net.bible.android.platform.androidSaxTextPolicy(),
    )

    // Oracle: the pre-change implementation, inlined.
    private val oldPattern = Pattern.compile(".{100,2000}[a-z]+[.?!][\\s]{1,}+", Pattern.DOTALL)

    private fun oldBreakUp(text: String): List<String> {
        val chunks1 = mutableListOf<String>()
        if (text.length < 4000) {
            chunks1.add(text)
        } else {
            val matcher = oldPattern.matcher(text)
            var matchedUpTo = 0
            while (matcher.find()) {
                val nextEnd = matcher.end()
                chunks1.add(text.substring(matchedUpTo, nextEnd))
                matchedUpTo = nextEnd
            }
            if (matchedUpTo < text.length) chunks1.add(text.substring(matchedUpTo))
        }
        val chunks2 = mutableListOf<String>()
        for (chunk in chunks1) {
            if (chunk.length < 4000) chunks2.add(chunk)
            else {
                var start = 0
                val size = 3990
                while (start < chunk.length) {
                    chunks2.add(chunk.substring(start, Math.min(chunk.length, start + size)))
                    start += size
                }
            }
        }
        return chunks2
    }

    private fun sentences(length: Int, seed: Int = 1, separator: String = " "): String {
        val rnd = java.util.Random(seed.toLong())
        val sb = StringBuilder()
        while (sb.length < length) {
            val words = 5 + rnd.nextInt(40)
            repeat(words) { sb.append("word".repeat(1 + rnd.nextInt(3))).append(' ') }
            sb.append("end").append(listOf(".", "?", "!")[rnd.nextInt(3)]).append(if (rnd.nextInt(4) == 0) "\n\n" else separator)
        }
        return sb.substring(0, length)
    }

    private fun breakUp(text: String): List<String?> =
        ReflectionHelpers.callInstanceMethod(provider, "breakUpText", ClassParameter.from(String::class.java, text))

    @Test fun breakUpTextMatchesOldRegexOnLongTexts() {
        val texts = listOf(
            sentences(9000), sentences(9000, 2, "\n"), sentences(4000, 3), sentences(4001, 4), sentences(3999, 5),
            "word ".repeat(1800), // no sentence ends: forced equal split (spaces keep old-regex backtracking bounded)
            sentences(9000, 6).replace('e', 'E'), // upper-case letters defeat [a-z]+ before the punctuation
        )
        for (text in texts) {
            assertEquals("chunks for length ${text.length}", oldBreakUp(text), breakUp(text))
        }
        assertTrue(breakUp(sentences(9000)).size > 2)
    }

    private class Pos(val found: Boolean, val startPosition: Int, val text: String, val fraction: Float)

    private fun pos(method: String, text: String, fraction: Double): Pos {
        val r = ReflectionHelpers.callInstanceMethod<Any>(
            provider, method, ClassParameter.from(String::class.java, text), ClassParameter.from(Double::class.javaPrimitiveType, fraction),
        )
        fun <T> f(name: String): T = r.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(r) as T
        return Pos(f("found"), f("startPosition"), f("text"), f("actualFractionOfWhole"))
    }

    @Test fun sentenceStartPositionsMatchBreakIterator() {
        val text = sentences(2500, 7)
        for (fraction in listOf(0.0, 0.3, 1.0)) {
            val offset = (Math.min(1.0, fraction) * text.length).toInt()
            val bi = BreakIterator.getSentenceInstance().also { it.setText(text) }
            val prev = bi.preceding(offset)
            val p = pos("getPrevTextStartPos", text, fraction)
            assertEquals("prev found at $fraction", prev >= 0, p.found)
            if (prev >= 0) {
                assertEquals("prev start at $fraction", prev, p.startPosition)
                assertEquals(text.substring(prev), p.text)
                assertEquals(prev.toFloat() / text.length, p.fraction)
            }
            val bi2 = BreakIterator.getSentenceInstance().also { it.setText(text) }
            val fwd = bi2.following(offset)
            val f = pos("getForwardTextStartPos", text, fraction)
            assertEquals("fwd found at $fraction", fwd >= 0, f.found)
            if (fwd >= 0) {
                val expected = if (fwd < text.length - 1 - 1) fwd + 1 else fwd
                assertEquals("fwd start at $fraction", expected, f.startPosition)
                assertEquals(text.substring(expected), f.text)
            }
        }
    }
}
