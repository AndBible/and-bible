package net.bible.sharedlogic.search

import fi.sykero.kdom.Element
import fi.sykero.kdom.Text
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Non-ASCII and wildcard highlight fixtures in commonTest so they also run on Kotlin/Native (iOS), whose regex engine
 * differs from the JVM's on `\b`, `\w` and case folding outside ASCII.
 */
class CommonSearchHighlighterNativeParityTest {
    private fun osis(text: String) = Element("div").addContent(Element("verse").addContent(Text(text)))

    @Test fun greekTermIsHighlightedCaseInsensitively() {
        val result = CommonSearchHighlighter.highlight(osis("ἐν ἀρχῇ ἦν ὁ Λόγος καὶ"), listOf("λόγος"), emptyList())

        assertEquals(
            StyledText(listOf(
                StyledRun("ἐν ἀρχῇ ἦν ὁ "),
                StyledRun("Λόγος", highlight = true),
                StyledRun(" καὶ"),
            )),
            result,
        )
    }

    @Test fun wildcardTermHighlightsWholeWords() {
        val result = CommonSearchHighlighter.highlight(osis("Godhead and godly men"), listOf("god*"), emptyList())

        assertEquals(
            StyledText(listOf(
                StyledRun("Godhead", highlight = true),
                StyledRun(" and "),
                StyledRun("godly", highlight = true),
                StyledRun(" men"),
            )),
            result,
        )
    }

    @Test fun thaiTermWithCombiningMarksIsHighlightedWithoutWordBoundaries() {
        val result = CommonSearchHighlighter.highlight(osis("พระเจ้าทรงสร้างฟ้าสวรรค์"), listOf("พระเจ้า"), emptyList())

        assertEquals(
            StyledText(listOf(StyledRun("พระเจ้า", highlight = true), StyledRun("ทรงสร้างฟ้าสวรรค์"))),
            result,
        )
    }

    /** Hebrew with niqqud: combining marks are inside the word, so the term is matched whole between spaces. */
    @Test fun hebrewTermWithNiqqudIsHighlighted() {
        val result = CommonSearchHighlighter.highlight(
            osis("בְּרֵאשִׁית בָּרָא אֱלֹהִים אֵת"), listOf("בָּרָא"), emptyList(),
        )

        assertEquals(
            StyledText(listOf(
                StyledRun("בְּרֵאשִׁית "),
                StyledRun("בָּרָא", highlight = true),
                StyledRun(" אֱלֹהִים אֵת"),
            )),
            result,
        )
    }
}
