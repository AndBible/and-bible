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

class SpeakReferencesTest {
    private val phrases = SpeakPhrases("first", "second", "third", "fourth", "fifth", "book", "chapter")
    private val advanced = AdvancedSpeakSettings(DatabaseAppSettings({ error("No settings needed") }, { false }))

    private fun bible(xml: String, footnotes: Boolean = false): String {
        val handler = OsisToBibleSpeak(
            SpeakSettings(playbackSettings = PlaybackSettings(speakFootnotes = footnotes)),
            phrases, advanced, 4000, SaxTextPolicy.MODERN,
        )
        XmlEventParser().parse(xml, handler)
        return handler.speakCommands.filterIsInstance<TextCommand>().joinToString("") { it.text }.trim()
    }

    private fun legacy(xml: String, sayReferences: Boolean = true): String {
        val handler = OsisToSpeakTextSaxHandler(sayReferences, SaxTextPolicy.MODERN)
        XmlEventParser().parse(xml, handler)
        return handler.toString().trim()
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
    fun invalidAndNonBibleLinksKeepTheirVisibleLabels() {
        for (target in listOf("", "bad.target", "https://example.org", "Matt.2.bad", "Matt.2.9 bad")) {
            val xml = "<verse>See <reference osisRef='$target'>the introduction</reference>.</verse>"
            assertEquals("See the introduction.", bible(xml), target)
            assertEquals("See the introduction.", legacy(xml), target)
        }
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
    fun legacyDoesNotEnableReferencesInsideNotesWhenDisabled() {
        assertEquals("Before after.", legacy("<verse>Before<note><reference osisRef='Matt.2.9'>v. 9</reference></note> after.</verse>", sayReferences = false))
    }
}
