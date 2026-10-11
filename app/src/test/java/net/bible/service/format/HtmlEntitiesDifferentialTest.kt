package net.bible.service.format

import android.text.Html
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HtmlEntitiesDifferentialTest {
    private val corpus = listOf(
        "plain", "a &amp; b", "&amp;quot;x&amp;quot;", "&quot;q&quot;", "&lt;tag&gt;", "x&nbsp;y", "&#8212;", "&#x2014;",
        "a < b", "1 <2", "trailing &", "&unknown;", "  two  spaces  ", "line\nbreak", "tab\there", "Ἐν ἀρχῇ", "שָׁלוֹם",
        "<b>bold</b>", "<br>", "a&amp;amp;b",
        // Distinct characters() values captured from the two existing handler fixture suites.
        "something", "  ", " ", "Be exalted,", "O God", "inNoteBeforeRef", "inNoteAfterRef",
        "&copy; &apos; &euro;", "a&#32;&#32;b", "a&#10;b", "a\r\nb", "<p>x</p><p>y</p>",
        "a<br>b", "<i>x</i>", "<unknown>x</unknown>", "a <", "x &amp y", "a<!-- hidden -->b",
    )

    @Test fun portableNamedEntitiesRetainCoverageAndMatchFramework() {
        val differences = htmlEntityCodePoints.mapNotNull { (name, _) ->
            val input = "a&$name;b"
            val expected = Html.fromHtml(input, 0).toString()
            val actual = decodeHtmlEntities(input)
            println("NAMED_ORACLE $name " + java.util.Base64.getEncoder().encodeToString(expected.toByteArray()))
            if (expected == actual) null else "entity $name expected=$expected actual=$actual"
        }
        assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
        for (input in listOf("a&#128512;b", "a&#x1F600;b", "a&unknown;b", "a&AMP;b", "a&#0;b", "a&#13;b")) {
            assertEquals("input $input", Html.fromHtml(input, 0).toString(), decodeHtmlEntities(input))
        }
    }

    @Test fun fullInstalledFrameworkRepertoireMatchesActualHtml() {
        // The input inventory belongs to the installed API-33 framework, not our replacement map.
        Html.fromHtml("initialize schema", 0)
        val parser = Class.forName("android.text.Html\$HtmlParser")
        val schema = org.robolectric.util.ReflectionHelpers.getStaticField<Any>(parser, "schema")
        val entities = org.robolectric.util.ReflectionHelpers.getField<Map<String, Int>>(schema, "theEntities")
        assertEquals("Installed android-13-robolectric-9030017 schema inventory", 2111, entities.size)
        val differences = mutableListOf<String>()
        for (name in entities.keys.sorted()) {
            for (input in listOf("a&$name;b", "a&$name b", "&$name;")) {
                val expected = Html.fromHtml(input, 0).toString()
                val actual = decodeHtmlEntities(input)
                println("FRAMEWORK_NAMED_ORACLE $name ${java.util.Base64.getEncoder().encodeToString(input.toByteArray())} ${java.util.Base64.getEncoder().encodeToString(expected.toByteArray())}")
                if (expected != actual) differences += "input=$input expected=$expected actual=$actual"
            }
        }
        for (input in listOf("a&check;b", "a&checkmark;b", "a&ohm;b", "a&b.alpha;b", "a&Tab;b", "a&NewLine;b", "a&phi;b", "a&check.unknown;b")) {
            val expected = Html.fromHtml(input, 0).toString()
            val actual = decodeHtmlEntities(input)
            if (expected != actual) differences += "independent input=$input expected=$expected actual=$actual"
        }
        assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
    }

    @Test fun namedWhitespaceAndPunctuationFollowScannerBoundaries() {
        val inputs = listOf("&thinsp;", "&thinsp;a", "&thinsp;&thinsp;a", "a&thinsp;b", "&thinsp;&nbsp;",
            "\t", "\ta", "&Tab;", "a&Tab;b", "&NewLine;", "a&NewLine;b", "&b.alpha;", "&check.foo;",
            "&amp;check;", " &thinsp; ", "a&thinsp;", "&emsp;<br>")
        val differences = inputs.mapNotNull { input ->
            val expected = Html.fromHtml(input, 0).toString()
            val actual = decodeHtmlEntities(input)
            println("SCANNER_ORACLE ${java.util.Base64.getEncoder().encodeToString(input.toByteArray())} ${java.util.Base64.getEncoder().encodeToString(expected.toByteArray())}")
            if (expected == actual) null else "input=$input expected=$expected actual=$actual"
        }
        assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
    }

    @Test fun carriageReturnsAndWindowsCharactersMatchFramework() {
        val inputs = listOf("a\rb", "a\r\nb", "a\u0085b", "a&#133;b") +
            (0x80..0x9f).flatMap { code -> listOf("a${code.toChar()}b", "a&#$code;b", "a&#x${code.toString(16)};b", "a&#$code b") }
        val differences = inputs.mapNotNull { input ->
            val expected = Html.fromHtml(input, 0).toString()
            println("CONTROL_ORACLE ${java.util.Base64.getEncoder().encodeToString(input.toByteArray())} ${java.util.Base64.getEncoder().encodeToString(expected.toByteArray())}")
            val actual = decodeHtmlEntities(input)
            if (expected == actual) null else "input=$input expected=$expected actual=$actual"
        }
        assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
    }

    @Test fun matchesFrameworkHtml() {
        val differences = corpus.mapNotNull { input ->
            val expected = Html.fromHtml(input, 0).toString()
            println("HTML_ORACLE ${input.toByteArray().let { java.util.Base64.getEncoder().encodeToString(it) }} ${expected.toByteArray().let { java.util.Base64.getEncoder().encodeToString(it) }}")
            val actual = decodeHtmlEntities(input)
            if (expected == actual) null else "input=$input expected=$expected actual=$actual"
        }
        assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
    }
}
