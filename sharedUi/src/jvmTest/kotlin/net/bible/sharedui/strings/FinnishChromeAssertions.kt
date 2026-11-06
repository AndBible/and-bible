package net.bible.sharedui.strings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertTrue

/** Resource-derived counterpart of the former Swift recursive chrome scan (not Bible text). */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
internal object FinnishChromeAssertions {
    private fun resources(directory: String): Map<String, String> {
        val file = File(System.getProperty("repoRoot"), "app/src/main/res/$directory/strings.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return buildMap {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as org.w3c.dom.Element
                val hasMarkup = (0 until node.childNodes.length).any { node.childNodes.item(it).nodeType == org.w3c.dom.Node.ELEMENT_NODE }
                if (!hasMarkup && node.getAttribute("translatable") != "false") {
                    put(node.getAttribute("name"), node.textContent.replace("\\'", "'").replace("\\\"", "\"").trim())
                }
            }
        }
    }

    private val translatedEnglish: Set<String> by lazy {
        val en = resources("values")
        val fi = resources("values-fi")
        val legitimate = (fi.values + en.filter { (key, value) -> key !in fi || fi[key] == value }.values)
            .map { it.lowercase() }.toSet()
        en.filter { (key, value) ->
            fi[key] != null && fi[key] != value && value.length in 4..60 &&
                !Regex("[%\\\\\\n<>{}]|\\.\\.\\.|…$").containsMatchIn(value) && value.lowercase() !in legitimate
        }.values.toSet()
    }

    fun ComposeUiTest.assertNoTranslatedEnglish(screen: String) {
        val labels = onAllNodes(SemanticsMatcher("all chrome") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }.toSet()
        val leftovers = labels intersect translatedEnglish
        assertTrue(leftovers.isEmpty(), "$screen contains translated English chrome: $leftovers")
    }
}
