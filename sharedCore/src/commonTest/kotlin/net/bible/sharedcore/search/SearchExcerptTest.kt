package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #4092: a long commentary entry is shown as a short excerpt around the first match. */
class SearchExcerptTest {
    private val filler = (1..60).joinToString(" ") { "word$it" }

    @Test fun shortTextIsKeptWithWhitespaceCollapsed() {
        assertEquals("I am the vine, ye are the branches.", searchExcerpt("  I am the\n vine,   ye are the branches. ", listOf("vine"), 50))
    }

    @Test fun longTextIsCutAroundTheFirstMatchAtWordBoundaries() {
        val text = "$filler the true vine and $filler"
        val excerpt = searchExcerpt(text, listOf("vine"), 60)
        assertTrue(excerpt.startsWith("…") && excerpt.endsWith("…"), excerpt)
        assertTrue(excerpt.length <= 62, "${excerpt.length}: $excerpt")
        assertTrue(excerpt.contains("the true vine and"), excerpt)
        val inner = excerpt.removePrefix("…").removeSuffix("…")
        val words = text.split(" ")
        assertTrue(inner.split(" ").all { it in words }, "no word is cut in half: $excerpt")
    }

    @Test fun matchNearTheStartKeepsTheStartUnmarked() {
        val excerpt = searchExcerpt("Vine is first. $filler", listOf("vine"), 40)
        assertTrue(excerpt.startsWith("Vine is first."), excerpt)
        assertTrue(excerpt.endsWith("…"), excerpt)
    }

    @Test fun matchAtTheEndKeepsTheEndUnmarked() {
        val excerpt = searchExcerpt("$filler the last vine.", listOf("vine"), 40)
        assertTrue(excerpt.startsWith("…") && excerpt.endsWith("the last vine."), excerpt)
        assertTrue(excerpt.length <= 42, excerpt)
    }

    @Test fun phraseAndWildcardTermsLocateTheMatch() {
        assertTrue(searchExcerpt("$filler burning bush $filler", listOf("\"burning bush\""), 50).contains("burning bush"))
        assertTrue(searchExcerpt("$filler vineyards $filler", listOf("vine*"), 50).contains("vineyards"))
    }

    @Test fun noVisibleMatchFallsBackToTheStart() {
        val excerpt = searchExcerpt("$filler $filler", listOf("vine"), 40)
        assertTrue(excerpt.startsWith("word1 word2") && excerpt.endsWith("…"), excerpt)
    }

    @Test fun oneUnbrokenWordLongerThanTheBudgetIsHardCut() {
        val excerpt = searchExcerpt("x".repeat(500), listOf("vine"), 40)
        assertEquals("x".repeat(40) + "…", excerpt)
    }
}
