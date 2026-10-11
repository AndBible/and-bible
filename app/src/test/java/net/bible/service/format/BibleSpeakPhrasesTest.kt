package net.bible.service.format

import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.device.speak.TextCommand
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.test.testAppSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = net.bible.android.TestBibleApplication::class)
class BibleSpeakPhrasesTest {
    @Test fun suppliedDivineNamesAndAdvancedSettingControlReplacement() {
        val advanced = AdvancedSpeakSettings(testAppSettings())
        val original = advanced.replaceDivineName
        try {
            val phrases = SpeakPhrases("1", "2", "3", "4", "5", "book", "chapter", listOf("NAME", ""), listOf("REPLACED", "BAD"))
            for (replace in listOf(true, false)) {
                advanced.replaceDivineName = replace
                val handler = OsisToBibleSpeak(SpeakSettings(), phrases, advanced, 4000, SaxTextPolicy.MODERN)
                handler.startDocument()
                handler.startElement("", "", "divineName", null)
                handler.characters("NAME".toCharArray(), 0, 4)
                handler.endElement("", "", "divineName")
                handler.endDocument()
                assertEquals(if (replace) "REPLACED" else "NAME", (handler.speakCommands.single() as TextCommand).text)
            }
        } finally {
            advanced.replaceDivineName = original
        }
    }
}
