package net.bible.service.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Keeps the six existing tutorial-label translations after removing the qualified resource key. */
class TutorialVideoLabelResourcesTest {
    @Test
    fun localesWithQualifiedLabelTranslationsKeepTheirPlainTranslations() {
        val locales = listOf("values-az", "values-kk", "values-ml", "values-my", "values-uz", "values-yue")
        val missing = locales.filter { locale ->
            val resource = File("src/main/res/$locale/strings.xml")
            if (!resource.exists()) return@filter true
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource)
            val nodes = document.getElementsByTagName("string")
            (0 until nodes.length).none { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue == "watch_tutorial_video_plain" && node.textContent.isNotBlank()
            }
        }
        assertEquals("Tutorial label translations must remain localized", emptyList<String>(), missing)
    }
}
