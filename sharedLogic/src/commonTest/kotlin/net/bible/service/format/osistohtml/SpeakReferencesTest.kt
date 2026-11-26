package net.bible.service.format.osistohtml

import fi.sykero.kdom.input.KdomBuilder
import fi.sykero.kdom.input.XmlEventParser
import fi.sykero.kdom.query.descendantElements
import net.bible.service.format.osistohtml.osishandlers.SpeakReferenceText
import org.crosswire.common.util.KLocale
import org.crosswire.jsword.versification.BookName
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.device.speak.TextCommand
import net.bible.service.format.SaxTextPolicy
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToSpeakTextSaxHandler
import net.bible.sharedlogic.settings.DatabaseAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeakReferencesTest {
    private val phrases = SpeakPhrases("first", "second", "third", "fourth", "fifth", "book", "chapter")
    private val advanced = AdvancedSpeakSettings(DatabaseAppSettings({ error("No settings needed") }, { false }))

    private fun bible(xml: String, footnotes: Boolean = false, links: Boolean = true): String {
        val playback = PlaybackSettings(speakFootnotes = footnotes, speakLinks = links)
        val handler = OsisToBibleSpeak(
            SpeakSettings(playbackSettings = playback),
            phrases, advanced, 4000, SaxTextPolicy.MODERN,
        )
        XmlEventParser().parse(xml, handler)
        return handler.speakCommands.filterIsInstance<TextCommand>().joinToString("") { it.text }.trim()
    }

    private fun legacy(xml: String, sayReferences: Boolean = true, links: Boolean = true): String {
        val handler = OsisToSpeakTextSaxHandler(sayReferences, SaxTextPolicy.MODERN, speakLinks = links)
        XmlEventParser().parse(xml, handler)
        return handler.toString().trim()
    }

    @Test
    fun bibleSkipsReferencesAndTheirEmptyBracketedIntroductions() {
        for (wrapper in listOf("(%s)", "[see %s]", "{see %s, %s}")) {
            val ref = "<reference osisRef='Matt.2.9'>v. 9</reference>"
            val content = wrapper.replace("%s", ref)
            assertEquals("Before after.", bible("<verse>Before $content after.</verse>", links = false))
        }
        assertEquals("Before see after.", bible("<verse>Before see <a href='https://example.org'><hi>link</hi></a> after.</verse>", links = false))
        assertEquals("Before after.", bible("<verse>Before (<reference><note type='study'>hidden</note><verse>also hidden</verse></reference>) after.</verse>", footnotes = true, links = false))
        assertEquals("Keep (real words) after.", bible("<verse>Keep (real words <reference>link</reference>) after.</verse>", links = false))
    }

    @Test
    fun skippedLinksSeparateAdjacentWords() {
        for (link in listOf("<reference osisRef='Matt.2.9'/>", "<a href='https://example.org'><hi>link</hi></a>")) {
            assertEquals("word next.", bible("<verse>word${link}next.</verse>", links = false))
        }
    }

    @Test
    fun referenceOnlyNotesAndTitlesLeaveNoCommands() {
        for (element in listOf("note type='study'", "title")) {
            val name = element.substringBefore(' ')
            val handler = OsisToBibleSpeak(
                SpeakSettings(playbackSettings = PlaybackSettings(speakFootnotes = true, speakTitles = true, speakLinks = false)),
                phrases, advanced, 4000, SaxTextPolicy.MODERN,
            )
            XmlEventParser().parse("<verse>Before.<$element><reference osisRef='Matt.2.9'/>.</$name>After.</verse>", handler)
            assertTrue(handler.speakCommands.all { it is TextCommand }, handler.speakCommands.toString())
            assertTrue(handler.speakCommands.filterIsInstance<TextCommand>().all { it.text.any(Char::isLetterOrDigit) })
            assertEquals("Before.After.", handler.speakCommands.filterIsInstance<TextCommand>().joinToString("") { it.text })
        }
    }

    @Test
    fun realNoteAndTitleContentKeepsItsCommandsWhenLinksAreSkipped() {
        val handler = OsisToBibleSpeak(
            SpeakSettings(playbackSettings = PlaybackSettings(speakFootnotes = true, speakTitles = true, speakLinks = false)),
            phrases, advanced, 4000, SaxTextPolicy.MODERN,
        )
        XmlEventParser().parse("<verse><title>Heading <reference/></title><note type='study'>Explanation <reference/>.</note></verse>", handler)
        assertEquals(6, handler.speakCommands.size)
        assertEquals(listOf("Heading", "Explanation."), handler.speakCommands.filterIsInstance<TextCommand>().map { it.text })
    }

    @Test
    fun bibleSpeaksBooklessAndFullyLabelledReferences() {
        for (label in listOf("v. 9", "2:9", "Matt 2:9", "Matthew 2:9")) {
            assertEquals("See Matthew 2:9 now.", bible("<verse>See <reference osisRef='Matt.2.9'>$label</reference> now.</verse>"), label)
        }
    }

    @Test
    fun legacySpeaksFullReferenceInsteadOfBooklessLabel() {
        assertEquals("See Matthew 2 verse 9 now.", legacy("<div>See <reference osisRef='Matt.2.9'>v. 9</reference> now.</div>"))
    }

    @Test
    fun rangesListsAndChapterReferencesKeepTheirTargets() {
        val formatter = SpeakReferenceText(KLocale.ENGLISH)
        for ((target, expected) in listOf(
            "Matt.2.6-Matt.2.9" to "Matthew 2:6-2:9",
            "Matt.2.6-Luke.2.9" to "Matthew 2:6-Luke 2:9",
            "Matt.2.6 Luke.2.9" to "Matthew 2:6; Luke 2:9",
            "Matt.2" to "Matthew 2",
        )) assertEquals(expected, formatter.expand(target))
    }

    @Test
    fun workPrefixesAndGrainsAreIgnored() {
        val formatter = SpeakReferenceText(KLocale.ENGLISH)
        for ((target, expected) in listOf(
            "Bible:Matt.2.9" to "Matthew 2:9",
            "KJV:Matt.2.9" to "Matthew 2:9",
            "Matt.2.9!crossReference.a" to "Matthew 2:9",
            "Matt.2.9@s[x]" to "Matthew 2:9",
            "Bible:Matt.2.9!a-Bible:Matt.2.12!b" to "Matthew 2:9-2:12",
            "Matt.2.9!a-Luke.2.12@s[x]" to "Matthew 2:9-Luke 2:12",
            "KJV:Matt.2.9!a Bible:Luke.2.9@s[x]" to "Matthew 2:9; Luke 2:9",
        )) assertEquals(expected, formatter.expand(target), target)
    }

    /** Modules such as HCB write osisRef book ids in any case or as short names ("ps.89.39", "Deu.18.15"). */
    @Test
    fun nonCanonicalBookIdsAreResolvedToTheirBook() {
        val formatter = SpeakReferenceText(KLocale.ENGLISH)
        for ((target, expected) in listOf(
            "ps.89.39" to "Psalms 89:39",
            "Deu.18.15-Deu.18.19" to "Deuteronomy 18:15-18:19",
            "Exo.13.2" to "Exodus 13:2",
            "Genesis.1.1" to "Genesis 1:1",
            "1Chro.24.1" to "1 Chronicles 24:1",
            "Jam.1.5 Hosea.6.6" to "James 1:5; Hosea 6:6",
            "MATT.2.9" to "Matthew 2:9",
        )) assertEquals(expected, formatter.expand(target), target)
        // Matt 2:23 in HCB, as the reporter heard it.
        val spoken = bible("<verse>a Nazarite {see <reference osisRef=\"ps.89.39\">ps 89:39</reference>,<reference osisRef=\"ps.132.18\">ps 132:18</reference>}.</verse>")
        assertTrue("Psalms 89:39" in spoken && "Psalms 132:18" in spoken && "ps " !in spoken, spoken)
    }

    @Test
    fun invalidAndNonBibleLinksKeepTheirVisibleLabels() {
        for (target in listOf("", "bad.target", "https://example.org", "Matt.2.bad", "Matt.2.9 bad")) {
            val xml = "<verse>See <reference osisRef='$target'>the introduction</reference>.</verse>"
            assertEquals("See the introduction.", bible(xml), target)
            assertEquals("See the introduction.", legacy(xml), target)
        }
    }

    @Test
    fun legacySkipsVisibleLinksAndBracketDebrisWhenDisabled() {
        assertEquals("Before after.", legacy("<div>Before [see <reference osisRef='Matt.2.9'><hi>v. 9</hi></reference>] after.</div>", links = false))
        assertEquals("Before after.", legacy("<div>Before (<a href='https://example.org'>link</a>) after.</div>", links = false))
    }

    @Test
    fun aReferenceSplitAcrossAnchorsIsSpokenOnceWithoutMutatingTheDocument() {
        val root = KdomBuilder().build("<div><reference osisRef='Matt.2.9'><BVA ordinal='0'>Matt.</BVA><hi><BVA ordinal='1'> 2:9</BVA></hi></reference></div>").rootElement
        val anchors = root.descendantElements().filter { it.name == "BVA" }.toList()
        val formatter = SpeakReferenceText(KLocale.ENGLISH)
        assertEquals(listOf("Matthew 2:9", ""), anchors.map { formatter.forAnchor(it) })
        assertEquals(listOf("Matt.", " 2:9"), anchors.map { it.value })
    }

    @Test
    fun skippedGeneralBookAndAiLinksKeepOrdinalsAndDoNotMutateDocument() {
        for (link in listOf("<reference osisRef='Matt.2.9'><BVA ordinal='1'>v. 9</BVA></reference>",
            "<a href='https://example.org'><hi><BVA ordinal='1'>AI source</BVA></hi></a>")) {
            val root = KdomBuilder().build("<div><BVA ordinal='0'>Before [see </BVA>$link<BVA ordinal='2'>] after.</BVA></div>").rootElement
            val anchors = root.descendantElements().filter { it.name == "BVA" }.toList()
            val original = anchors.map { it.value }
            val formatter = SpeakReferenceText(KLocale.ENGLISH)
            assertEquals(listOf("Before ", "", "after."), formatter.forAnchors(anchors, speakLinks = false))
            assertEquals(original, anchors.map { it.value })
            assertTrue(formatter.forAnchors(anchors, speakLinks = true)[1].isNotBlank())
        }
    }

    @Test
    fun aiDocumentPlainHtmlLinksAreSkippedAfterRealAnchorGeneration() {
        val root = KdomBuilder().build("<div><p>Before (see <a href='https://example.org'><b>AI source</b></a>) after.</p></div>").rootElement
        net.bible.service.sword.SwordContentFacade.addAnchors(root, "en")
        val anchors = root.descendantElements().filter { it.name == "BVA" }.toList()
        val formatter = SpeakReferenceText(KLocale.ENGLISH)
        assertEquals("Before after.", formatter.forAnchors(anchors, speakLinks = false).joinToString(""))
        assertEquals("Before (see AI source) after.", formatter.forAnchors(anchors, speakLinks = true).joinToString(""))
    }

    @Test
    fun referenceBookNamesUseTheDocumentLanguageNotAbbreviationPreferences() {
        val original = BookName.isFullBookName()
        try {
            BookName.setFullBookName(false)
            assertEquals("Matthew 2:9", SpeakReferenceText(KLocale.ENGLISH).expand("Matt.2.9"))
            assertEquals("Matthäus 2:9", SpeakReferenceText(KLocale("de")).expand("Matt.2.9"))
        } finally {
            BookName.setFullBookName(original)
        }
    }

    @Test
    fun bibleKeepsReferencesInExcludedNotesSilent() {
        assertEquals("Before after.", bible("<verse>Before<note type='crossReference'><reference osisRef='Matt.2.9'>v. 9</reference></note> after.</verse>"))
    }

    @Test
    fun bibleSpeaksReferencesInEnabledStudyNotes() {
        assertEquals("Matthew 2:9", bible("<verse><note type='study'><reference osisRef='Matt.2.9'>v. 9</reference></note></verse>", footnotes = true))
    }

    @Test
    fun legacyStillSpeaksVisibleReferencesWhenOnlyNoteReferencesAreDisabled() {
        assertEquals("See Matthew 2 verse 9 now.", legacy("<div>See <reference osisRef='Matt.2.9'>v. 9</reference> now.</div>", sayReferences = false))
    }

    @Test
    fun legacyDoesNotEnableReferencesInsideNotesWhenDisabled() {
        assertEquals("Before after.", legacy("<verse>Before<note><reference osisRef='Matt.2.9'>v. 9</reference></note> after.</verse>", sayReferences = false))
    }
}
