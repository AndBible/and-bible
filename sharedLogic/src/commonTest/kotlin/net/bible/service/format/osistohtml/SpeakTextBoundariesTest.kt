package net.bible.service.format.osistohtml

import fi.sykero.kdom.input.XmlEventParser
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.device.speak.PostFootnoteCommand
import net.bible.service.device.speak.PreFootnoteCommand
import net.bible.service.device.speak.TextCommand
import net.bible.service.format.SaxTextPolicy
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToSpeakTextSaxHandler
import net.bible.sharedlogic.settings.DatabaseAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Source whitespace, rather than the language or punctuation, determines inline word boundaries. */
class SpeakTextBoundariesTest {
    private fun handler(footnotes: Boolean = false, titles: Boolean = true, maxLength: Int = 4000) =
        OsisToBibleSpeak(
            SpeakSettings(playbackSettings = PlaybackSettings(speakFootnotes = footnotes, speakTitles = titles)),
            SpeakPhrases("first", "second", "third", "fourth", "fifth", "book", "chapter"),
            AdvancedSpeakSettings(DatabaseAppSettings({ error("No settings needed") }, { false })),
            maxLength, SaxTextPolicy.MODERN,
        )

    private fun parse(xml: String, footnotes: Boolean = false, titles: Boolean = true, maxLength: Int = 4000) =
        handler(footnotes, titles, maxLength).also { XmlEventParser().parse(xml, it) }.speakCommands

    private fun texts(xml: String) = parse("<verse>$xml</verse>").filterIsInstance<TextCommand>().map { it.text }

    @Test
    fun frenchElisionsKeepTheirWordAcrossMarkup() {
        for (apostrophe in listOf("’", "ʼ", "'")) {
            assertEquals(listOf("l'Éternel"), texts("l${apostrophe}<w>Éternel</w>"), apostrophe)
        }
    }

    @Test
    fun smallCapsDoNotSplitAWord() {
        assertEquals(listOf("LORD"), texts("L<hi type='small-caps'>ORD</hi>"))
        assertEquals(listOf("LORD"), texts("L<divineName>ORD</divineName>"))
    }

    @Test
    fun adjacentWordElementsWithoutWhitespaceStayJoined() {
        assertEquals(listOf("foobar"), texts("<w>foo</w><w>bar</w>"))
    }

    @Test
    fun possessiveAfterWordMarkupStaysJoined() {
        assertEquals(listOf("Jesus's,"), texts("<w>Jesus</w>’s,"))
    }

    @Test
    fun sourceWhitespaceIsKeptEvenBeforeApostrophes() {
        for ((xml, expected) in listOf(
            "l’ <w>Éternel</w>" to "l' Éternel",
            "l’<w> Éternel</w>" to "l' Éternel",
            "<w>in</w> <w>'s</w>" to "in 's",
            "<w>was</w> <w>'n</w>" to "was 'n",
            "said <w>'Go</w>" to "said 'Go",
            "<w>one</w>\n\t<w>two</w>" to "one two",
        )) assertEquals(listOf(expected), texts(xml), xml)
    }

    @Test
    fun titlesUseTheSameInlineBoundaryRules() {
        val commands = parse("<verse><title>l’<w>Éternel</w> L<hi>ORD</hi></title>body</verse>")
            .filterIsInstance<TextCommand>()
        assertEquals(listOf("l'Éternel LORD", "body"), commands.map { it.text })
        assertEquals(listOf(TextCommand.TextType.TITLE, TextCommand.TextType.NORMAL), commands.map { it.type })
    }

    @Test
    fun structureSeparatesTextEvenWithoutSourceWhitespace() {
        for (element in listOf("p", "l", "lb", "div", "note", "title")) {
            val commands = parse("<verse>foo<$element/>bar</verse>", titles = false)
            assertEquals("foo bar", commands.filterIsInstance<TextCommand>().joinToString(" ") { it.text }, element)
        }
        val commands = parse("<div><verse>foo</verse><verse>bar</verse></div>")
        assertEquals(listOf("foo bar"), commands.filterIsInstance<TextCommand>().map { it.text })
    }

    @Test
    fun footnotesSeparateTheSurroundingTextWhetherSpokenOrNot() {
        val xml = "<verse>l’<note type='study'>x</note>Éternel</verse>"
        assertEquals(listOf("l' Éternel"), parse(xml).filterIsInstance<TextCommand>().map { it.text })
        val spoken = parse(xml, footnotes = true)
        assertEquals(listOf("l'", "x", "Éternel"), spoken.filterIsInstance<TextCommand>().map { it.text })
        assertTrue(spoken[1] is PreFootnoteCommand)
        assertTrue(spoken[3] is PostFootnoteCommand)
    }

    @Test
    fun invisibleReferenceLabelsDoNotChangeTheVisibleBoundary() {
        assertEquals(listOf("Matthew 2:9's"), texts("<reference osisRef='Matt.2.9'> ignored </reference><w>'s</w>"))
    }

    @Test
    fun spokenReferenceExpansionIsSeparatedFromTheSourceWordsAroundIt() {
        // ISV places cross references right after the word or punctuation, with no source whitespace. The expansion is
        // synthetic text, so it must not glue to its neighbours ("spirit,Psalms", "thronesPsalms").
        assertEquals(listOf("in spirit, Psalms 51:17; next"),
            texts("in spirit,<reference osisRef='Ps.51.17'>x</reference>; next"))
        assertEquals(listOf("their thrones Psalms 51:17 and"),
            texts("their thrones<reference osisRef='Ps.51.17'>x</reference> and"))
    }

    @Test
    fun saxChunkingDoesNotInventWordBoundariesButWhitespaceOnlyChunksDo() {
        val handler = handler()
        handler.startDocument()
        for (chunk in listOf("fo", "o", " ", "b", "ar")) {
            handler.characters(chunk.toCharArray(), 0, chunk.length)
        }
        handler.endDocument()
        assertEquals(listOf("foo bar"), handler.speakCommands.filterIsInstance<TextCommand>().map { it.text })
    }

    @Test
    fun joinedTitleFitsExactlyAtTheEngineLimitAndKeepsItsType() {
        val commands = parse("<verse><title>f<hi>oo</hi></title></verse>", maxLength = 3)
            .filterIsInstance<TextCommand>()
        assertEquals(listOf("foo"), commands.map { it.text })
        assertEquals(TextCommand.TextType.TITLE, commands.single().type)
    }

    @Test
    fun delimiterAfterSeparatorKeepsLegacyJoinRule() {
        assertEquals(listOf("word, next"), texts("word<note type='crossReference'>x</note>, next"))
        assertEquals(listOf("Éternel, et"), texts("Éternel<note type='crossReference'>x</note>, et"))
        val verses = parse("<div><verse>foo</verse><verse>, bar</verse></div>").filterIsInstance<TextCommand>()
        assertEquals(listOf("foo, bar"), verses.map { it.text })
        assertEquals(listOf("foo bar"), texts("foo<note>x</note> bar"))
    }

    @Test
    fun milestonesAndLineGroupsSeparateText() {
        for (xml in listOf("foo<milestone type='line'/>bar", "foo<milestone type='x-p'/>bar", "foo<lg/>bar")) {
            assertEquals("foo bar", parse("<verse>$xml</verse>").filterIsInstance<TextCommand>().joinToString(" ") { it.text }, xml)
        }
    }

    @Test
    fun legacyTypographicPossessiveIsUnchanged() {
        val handler = OsisToSpeakTextSaxHandler(false, SaxTextPolicy.MODERN)
        XmlEventParser().parse("<div><divineName>Lord</divineName>’s house</div>", handler)
        assertEquals("Lord’s house", handler.toString().trim())
    }
}
