package net.bible.service.format

import android.text.Html
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.device.speak.TextCommand
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToCanonicalTextSaxHandler
import org.crosswire.common.xml.JDOMSAXEventProvider
import org.jdom2.input.SAXBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = net.bible.android.TestBibleApplication::class)
class HtmlSaxCharRefTest {
    @Test @Config(sdk = [23]) fun actualXmlCharacterReferencesKeepApi23LegacySpeech() {
        for ((reference, captured) in listOf("&#13;" to "a\rb", "&#133;" to "a\u0085b", "&amp;#133;" to "a&#133;b")) {
            val document = SAXBuilder().build(StringReader("<verse>a${reference}b</verse>"))
            val policy = net.bible.android.platform.androidSaxTextPolicy()
            assertEquals(SaxTextPolicy.LEGACY, policy)
            val canonical = OsisToCanonicalTextSaxHandler(textPolicy = policy)
            JDOMSAXEventProvider(document).provideSAXEvents(canonical)
            assertEquals("$captured ", canonical.writer.html)
            val speech = OsisToBibleSpeak(SpeakSettings(), SpeakPhrases("1", "2", "3", "4", "5", "book", "chapter"),
                AdvancedSpeakSettings(net.bible.test.testAppSettings()), 4000, policy)
            JDOMSAXEventProvider(document).provideSAXEvents(speech)
            assertEquals(captured, (speech.speakCommands.single() as TextCommand).text)
        }
    }

    @Test fun actualXmlCharacterReferencesReachBothModernHandlers() {
        val differences = mutableListOf<String>()
        for ((reference, captured) in listOf("&#13;" to "a\rb", "&#133;" to "a\u0085b", "&amp;#133;" to "a&#133;b")) {
            val document = SAXBuilder().build(StringReader("<verse>a${reference}b</verse>"))
            val provider = JDOMSAXEventProvider(document)
            val chunks = mutableListOf<String>()
            provider.provideSAXEvents(object : DefaultHandler() {
                override fun characters(ch: CharArray, start: Int, length: Int) { chunks.add(String(ch, start, length)) }
            })
            assertEquals(captured, chunks.joinToString(""))
            println("XML_SAX_CAPTURE $reference " + chunks.joinToString(",") { java.util.Base64.getEncoder().encodeToString(it.toByteArray()) })
            val expected = chunks.joinToString("") { Html.fromHtml(it, 0).toString() }
            val canonical = OsisToCanonicalTextSaxHandler(textPolicy = SaxTextPolicy.MODERN)
            provider.provideSAXEvents(canonical)
            if (canonical.writer.html != "$expected ") differences.add("canonical $reference expected=$expected actual=${canonical.writer.html}")
            val speech = OsisToBibleSpeak(SpeakSettings(), SpeakPhrases("1", "2", "3", "4", "5", "book", "chapter"),
                AdvancedSpeakSettings(net.bible.test.testAppSettings()), 4000, SaxTextPolicy.MODERN)
            provider.provideSAXEvents(speech)
            val spoken = (speech.speakCommands.single() as TextCommand).text
            if (spoken != expected) differences.add("speech $reference expected=$expected actual=$spoken")
            val legacy = OsisToCanonicalTextSaxHandler(textPolicy = SaxTextPolicy.LEGACY)
            provider.provideSAXEvents(legacy)
            assertEquals("$captured ", legacy.writer.html)
        }
        assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
    }
}
