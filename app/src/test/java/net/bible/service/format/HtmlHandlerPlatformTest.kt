package net.bible.service.format

import android.text.Html
import net.bible.service.format.osistohtml.osishandlers.OsisToCanonicalTextSaxHandler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HtmlHandlerPlatformTest {
    private fun canonical(input: String): String {
        val handler = OsisToCanonicalTextSaxHandler(textPolicy = net.bible.android.platform.androidSaxTextPolicy())
        handler.startDocument()
        handler.characters(input.toCharArray(), 0, input.length)
        handler.endDocument()
        return handler.writer.html
    }

    @Test @Config(sdk = [23]) fun legacyCanonicalLeavesEntitiesAndMarkupUntouched() {
        val input = "  &quot;q&quot; &amp; <b>x</b>"
        assertEquals(input, canonical(input))
        assertEquals("\"q\" & x", Html.fromHtml(input).toString())
    }

    @Test @Config(sdk = [33]) fun modernCanonicalDecodesWithoutLeadingSpaceAtDocumentStart() {
        assertEquals("\"q\" & x", canonical("  &quot;q&quot; &amp; <b>x</b>"))
        val handler = OsisToCanonicalTextSaxHandler(textPolicy = net.bible.android.platform.androidSaxTextPolicy())
        handler.startDocument()
        handler.characters("first".toCharArray(), 0, 5)
        handler.characters("  second".toCharArray(), 0, 8)
        handler.endDocument()
        assertEquals("first second", handler.writer.html)
    }

    private fun speech(input: String): String {
        val handler = net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak(
            net.bible.android.database.bookmarks.SpeakSettings(),
            net.bible.android.control.speak.SpeakPhrases("1", "2", "3", "4", "5", "book", "chapter"),
            net.bible.android.control.speak.AdvancedSpeakSettings(net.bible.test.testAppSettings()),
            4000, net.bible.android.platform.androidSaxTextPolicy(),
        )
        handler.startDocument()
        handler.characters(input.toCharArray(), 0, input.length)
        handler.endDocument()
        return (handler.speakCommands.single() as net.bible.service.device.speak.TextCommand).text
    }

    @Test @Config(sdk = [23]) fun legacySpeechOnlyDecodesQuotes() {
        assertEquals("\"q\" &amp; <b>x</b>", speech("&quot;q&quot; &amp; <b>x</b>"))
    }

    @Test @Config(sdk = [33]) fun modernSpeechUsesRealFrameworkDecoding() {
        val input = "&quot;q&quot; &amp; <b>x</b>"
        assertEquals(Html.fromHtml(input, 0).toString(), speech(input))
    }
}
