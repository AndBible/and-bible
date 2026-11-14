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

    @Test fun aStemmedTermLocatesTheInflectedWord() {
        // Lucene stems "vines" to "vine": the text holds "vine" and the term "vines" has no literal match.
        val text = "$filler the true vine and $filler"
        assertTrue(searchExcerpt(text, listOf("vines"), 60).contains("the true vine and"))
        // ... and the other way round: term "vine", text "vines" / "vineyard".
        assertTrue(searchExcerpt("$filler many vines grow $filler", listOf("vine"), 60).contains("many vines grow"))
    }

    @Test fun matchIgnoresCaseAndAccents() {
        assertTrue(searchExcerpt("$filler le Café du Roi $filler", listOf("cafe"), 50).contains("Café du Roi"))
        assertTrue(searchExcerpt("$filler the cafe is open $filler", listOf("CAFÉ"), 50).contains("the cafe is open"))
        assertTrue(searchExcerpt("$filler Ångström was here $filler", listOf("angstrom"), 50).contains("Ångström was here"))
    }

    @Test fun aLiteralMatchIsPreferredToAStemMatch() {
        // The literal match is what gets highlighted, so the excerpt shows it rather than an earlier inflection.
        val text = "$filler vines early. $filler the vine late $filler"
        assertTrue(searchExcerpt(text, listOf("vine"), 50).contains("the vine late"))
    }

    @Test fun shortUnrelatedWordsAreNotTakenForAStem() {
        // "a" is a prefix of the term "apple" but is not a stem of it.
        val excerpt = searchExcerpt("a $filler $filler", listOf("apple"), 40)
        assertTrue(excerpt.startsWith("a word1"), excerpt)
    }

    @Test fun anExcerptDoesNotSplitASurrogatePair() {
        val emoji = "\uD83D\uDE00"
        fun assertPaired(s: String) {
            s.forEachIndexed { i, c ->
                if (c.isHighSurrogate()) assertTrue(s.getOrNull(i + 1)?.isLowSurrogate() == true, "lone high surrogate in $s")
                if (c.isLowSurrogate()) assertTrue(s.getOrNull(i - 1)?.isHighSurrogate() == true, "lone low surrogate in $s")
            }
        }
        assertPaired(searchExcerpt(emoji.repeat(100), listOf("vine"), 41))
        assertPaired(searchExcerpt(emoji.repeat(100) + "vine" + emoji.repeat(100), listOf("vine"), 40))
        assertPaired(searchExcerpt(emoji.repeat(100) + "vine" + emoji.repeat(100), listOf("vine"), 41))
    }

    @Test fun aLemmaMarkedExcerptIsCutAroundTheFirstBoldRun() {
        val text = "$filler <b>Lord</b> and <b>God</b> $filler"
        val excerpt = searchExcerptAtBold(text, 50)
        assertTrue(excerpt.startsWith("…") && excerpt.endsWith("…"), excerpt)
        assertTrue(excerpt.contains("<b>Lord</b> and"), excerpt)
    }

    @Test fun withoutABoldRunTheLemmaExcerptIsTheStart() {
        val excerpt = searchExcerptAtBold("$filler $filler", 40)
        assertTrue(excerpt.startsWith("word1 word2") && excerpt.endsWith("…"), excerpt)
    }
}
