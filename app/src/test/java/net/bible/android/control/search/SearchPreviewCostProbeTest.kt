/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.search

import android.os.Build
import android.text.Html
import fi.sykero.kdom.Element
import fi.sykero.kdom.Text
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.search.prepareSearchTerms
import net.bible.sharedcore.search.splitSearchTerms
import net.bible.test.testCoreStrings
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.crosswire.jsword.index.search.SearchType
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * Opt-in cost probe (K6 follow-ups, task 3): where does the time of the MHC result sheet go?
 *
 * Skipped unless `ANDBIBLE_K6_CORPUS` names a Sword directory (containing `mods.d` and `modules`) with the MHC
 * module; `ANDBIBLE_K6_QUERY` (default `god`) is the search. Per result row it times, separately,
 * `SwordContentFacade.readOsisFragment`, `SearchHighlighter.highlight` and the `Html.fromHtml` step inside it
 * (re-run here on the same flattened text, because `htmlToSpan` is private to the highlighter's walk). Rows whose
 * entry was already read hit `SwordContentFacade`'s fragment cache, exactly as in the app. The summary goes to
 * stdout and `.local/k6-followups/task3-probe.txt` (resolved from the project root).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SearchPreviewCostProbeTest {
    private val control: SearchControl get() = GlobalContext.get().get()

    /** Flattens the verse children the way `SearchHighlighter.processElementChildren` does (word-term mode). */
    private fun flatten(parent: Element, out: StringBuilder) {
        for (o in parent.content) when (o) {
            is Element -> if (o.name != "note" && o.name != "reference") {
                if (o.children.isEmpty()) out.append(o.text) else flatten(o, out)
            }
            is Text -> out.append(o.text)
            else -> out.append(o.toString())
        }
    }

    private fun outline(e: Element, depth: Int = 0, out: StringBuilder = StringBuilder()): StringBuilder {
        if (depth > 4) return out
        out.append("  ".repeat(depth)).append(e.name).append(" text=").append(e.content.filterIsInstance<Text>().sumOf { it.text.length }).append('\n')
        e.children.take(6).forEach { outline(it, depth + 1, out) }
        return out
    }

    @Test fun mhcPreviewCost() {
        val corpusEnv = System.getenv("ANDBIBLE_K6_CORPUS")
        assumeTrue("ANDBIBLE_K6_CORPUS not set; opt-in probe", corpusEnv != null)
        val corpus = File(corpusEnv!!)
        assumeTrue("corpus dir $corpus has no mods.d", File(corpus, "mods.d").isDirectory)
        val query = System.getenv("ANDBIBLE_K6_QUERY")?.takeIf { it.isNotBlank() } ?: "god"

        val previous = SwordBookPathJvm.getAugmentPath()
        try {
            SwordBookPathJvm.setAugmentPath((previous.toList() + corpus).distinct().toTypedArray())
            val mhc = Books.installed().getBook("MHC") as? SwordBook
            assumeTrue("MHC is not in the corpus $corpus", mhc != null)
            mhc!!

            val indexStart = System.nanoTime()
            FtsTestSupport.ensureIndexed(mhc)
            val indexMs = (System.nanoTime() - indexStart) / 1e6

            val searchStart = System.nanoTime()
            val dto = runBlocking { control.getMultiSearchResults(listOf("MHC"), query) }
            val searchMs = (System.nanoTime() - searchStart) / 1e6

            val terms = splitSearchTerms(prepareSearchTerms(control.highlightSearchString(query, SearchType.ALL_WORDS)))
            val strings = testCoreStrings()
            val matches = (dto.mainSearchResults + dto.otherSearchResults).flatMap { it.translationMatches }

            var readNs = 0L; var highlightNs = 0L; var htmlNs = 0L
            var maxReadNs = 0L; var maxHighlightNs = 0L; var maxHtmlNs = 0L
            var prodChars = 0L; var emptyProd = 0; var fullNs = 0L; var maxFullNs = 0L
            var totalChars = 0L; var maxChars = 0; var errors = 0
            val distinct = HashSet<String>()
            var rows0 = 0
            val diag = StringBuilder()
            for (m in matches) {
                try {
                    var t = System.nanoTime()
                    val osis = SwordContentFacade.readOsisFragment(m.book, m.key, strings = strings)
                    val read = System.nanoTime() - t
                    readNs += read; maxReadNs = maxOf(maxReadNs, read)

                    t = System.nanoTime()
                    val styled = SearchHighlighter.highlight(osis, terms, emptyList())
                    val hl = System.nanoTime() - t
                    highlightNs += hl; maxHighlightNs = maxOf(maxHighlightNs, hl)

                    val prodText = styled.plainText()
                    prodChars += prodText.length
                    if (prodText.isEmpty()) emptyProd++
                    // What-if: the MHC fragment has no <verse> children, so the production walk sees nothing. Wrap a clone
                    // of the whole fragment in a synthetic <verse> to measure a walk that does see the text.
                    val wrapped = if (osis.getChildren("verse").isEmpty())
                        Element("div").addContent(Element("verse").addContent(osis.clone())) else osis
                    t = System.nanoTime()
                    val styledFull = SearchHighlighter.highlight(wrapped, terms, emptyList())
                    val full = System.nanoTime() - t
                    fullNs += full; maxFullNs = maxOf(maxFullNs, full)
                    val flat = StringBuilder().also { sb -> wrapped.getChildren("verse").forEach { flatten(it, sb) } }.toString()
                    t = System.nanoTime()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.fromHtml(flat, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(flat)
                    val html = System.nanoTime() - t
                    htmlNs += html; maxHtmlNs = maxOf(maxHtmlNs, html)

                    val text = styledFull.plainText()
                    if (rows0 < 3) {
                        rows0++
                        if (rows0 == 1) diag.append(outline(osis))
                        diag.appendLine("row $rows0: key=${m.key.getOsisID()} book=${m.book.initials} osis=<${osis.name}> children=${osis.children.map { it.name }.take(8)} " +
                            "flatLen=${flat.length} flatHead=${flat.take(80).replace('\n', ' ')} previewLen(full)=${text.length} prodLen=${prodText.length} previewHead=${text.take(80).replace('\n', ' ')}")
                    }
                    distinct += text
                    totalChars += text.length
                    maxChars = maxOf(maxChars, text.length)
                } catch (e: Exception) {
                    errors++
                }
            }

            fun ms(ns: Long) = String.format(Locale.ROOT, "%.1f", ns / 1e6)
            val rows = matches.size
            val summary = buildString {
                appendLine("K6 task 3 preview cost probe (MHC)")
                appendLine("query: $query   terms: $terms")
                appendLine("corpus: $corpus")
                appendLine("grouped rows: ${dto.size} (main ${dto.mainSearchResults.size}, other ${dto.otherSearchResults.size}); preview rows timed: $rows; errors: $errors")
                appendLine("index ensure ms: ${String.format(Locale.ROOT, "%.1f", indexMs)}; getMultiSearchResults ms: ${String.format(Locale.ROOT, "%.1f", searchMs)}")
                appendLine("readOsisFragment total ms: ${ms(readNs)}  max ms: ${ms(maxReadNs)}")
                appendLine("SearchHighlighter.highlight total ms: ${ms(highlightNs)}  max ms: ${ms(maxHighlightNs)}")
                appendLine("  Html.fromHtml (htmlToSpan step, re-run) total ms: ${ms(htmlNs)}  max ms: ${ms(maxHtmlNs)}")
                appendLine("read+highlight total ms: ${ms(readNs + highlightNs)}; highlight share: ${if (readNs + highlightNs > 0) "%.1f%%".format(Locale.ROOT, 100.0 * highlightNs / (readNs + highlightNs)) else "n/a"}; read share: ${if (readNs + highlightNs > 0) "%.1f%%".format(Locale.ROOT, 100.0 * readNs / (readNs + highlightNs)) else "n/a"}")
                appendLine("what-if (whole fragment wrapped in <verse>): highlight total ms: ${ms(fullNs)}  max ms: ${ms(maxFullNs)}; production-path previews empty: $emptyProd of $rows (production chars total $prodChars)")
                appendLine("distinct preview texts (what-if full text): ${distinct.size} of $rows rows (ratio ${if (rows > 0) "%.3f".format(Locale.ROOT, distinct.size.toDouble() / rows) else "n/a"})")
                append(diag)
                appendLine("preview chars (what-if full text): total $totalChars  mean ${if (rows > 0) totalChars / rows else 0}  max $maxChars")
            }
            println(summary)
            File(System.getProperty("user.dir"), "../.local/k6-followups").canonicalFile.apply {
                mkdirs()
                File(this, "task3-probe.txt").writeText(summary)
            }
        } finally {
            SwordBookPathJvm.setAugmentPath(previous)
        }
    }
}
