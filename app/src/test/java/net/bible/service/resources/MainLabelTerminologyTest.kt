package net.bible.service.resources

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** #4087: the English UI and help text use one term, "main label", never "primary label". */
class MainLabelTerminologyTest {
    private fun readText(vararg candidates: String): String =
        candidates.map(::File).first { it.exists() }.readText()

    @Test
    fun englishStringsSayMainLabelNotPrimary() {
        val xml = readText("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
        val offenders = Regex("<string name=\"[^\"]*\"[^>]*>([^<]*)</string>").findAll(xml)
            .map { it.groupValues[1] }.filter { it.contains("primary label", true) || it.contains("as primary", true) }.toList()
        assertTrue("English strings.xml must not say 'primary': $offenders", offenders.isEmpty())
    }

    @Test
    fun bibleviewDefaultYamlSaysMainLabelNotPrimary() {
        val yaml = readText("src/../bibleview-js/src/lang/default.yaml", "app/bibleview-js/src/lang/default.yaml")
        assertTrue(!Regex("primary label", RegexOption.IGNORE_CASE).containsMatchIn(yaml))
    }

    @Test
    fun labelDocsSayMainLabelNotPrimary() {
        val md = readText("../website/content/en/docs/labels.md", "website/content/en/docs/labels.md")
        assertTrue(!Regex("primary", RegexOption.IGNORE_CASE).containsMatchIn(md))
    }
}
