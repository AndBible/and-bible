package net.bible.service.format.osistohtml.taghandler

import net.bible.service.common.Constants
import net.bible.service.format.Note
import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.OSISUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.SystemKJV
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xml.sax.helpers.AttributesImpl

@RunWith(RobolectricTestRunner::class) // because of english qualifiers
@Config(qualifiers = "en", sdk = [28])
class NoteHandlerTest {
    private var osisToHtmlParameters: OsisToHtmlParameters? = null
    private var verseInfo: VerseInfo? = null
    private var writer: HtmlTextWriter? = null
    private var referenceHandler: ReferenceHandler? = null
    private var noteHandler: NoteHandler? = null
    private var basisRef: Verse? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToHtmlParameters = OsisToHtmlParameters()
        osisToHtmlParameters!!.isShowNotes = true
        verseInfo = VerseInfo()
        val KJV = Versifications.instance().getVersification(SystemKJV.V11N_NAME)
        val verse = Verse(KJV, BibleBook.PS, 14, 1)
        basisRef = Verse(KJV, BibleBook.PS, 14, 0)
        verseInfo!!.osisID = verse.osisID
        osisToHtmlParameters!!.basisRef = basisRef!!
        writer = HtmlTextWriter()
        noteHandler = NoteHandler(osisToHtmlParameters!!, verseInfo!!, writer!!)
        referenceHandler = ReferenceHandler(osisToHtmlParameters!!, noteHandler!!, writer!!)
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    /**
     * rusCARS
     * <note osisID="Ezek.40.5!1" osisRef="Ezek.40.5" type="study"> Букв.:
     * «шесть долгих локтей (простой локоть с ладонью в каждом)».
    </note> *
     */
    @Test
    fun simpleNote() {
        verseInfo!!.osisID = "Ezek.40.5"
        writer!!.write("before note")
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5!1")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Ezek.40.5")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "study")
        noteHandler!!.start(attrs)
        writer!!.write("Букв.: «шесть долгих локтей (простой локоть с ладонью в каждом)».")
        noteHandler!!.end()
        writer!!.write("after note")
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo(String.format("before note<a href='%s:Ezek.40.5/0a' class='noteRef'>0a</a> after note", Constants.NOTE_PROTOCOL)))
        val notesList: List<Note> = noteHandler!!.notesList
        Assert.assertThat(notesList.size, CoreMatchers.equalTo(1))
        Assert.assertThat(noteHandler!!.notesList[0].noteText, CoreMatchers.equalTo("Букв.: «шесть долгих локтей (простой локоть с ладонью в каждом)»."))
    }
    /** Note  */
    /**
     * HunUj note containing refs - seems to give extra blank note at end:
     * törzsnek.<note n="a" osisID="Jas.1.1!crossReference.a" osisRef="Jas.1.1" type="crossReference"> <reference osisRef="Matt.13.55">Mt 13:55</reference>; <reference osisRef="Mark.6.3">Mk 6:3</reference>; <reference osisRef="Acts.15.13">ApCsel 15:13</reference>; <reference osisRef="Gal.1.19">Gal 1:19</reference> </note>
     */
    @Test
    fun crossReference() {
        verseInfo!!.currentVerseNo = 1
        verseInfo!!.osisID = "Jas.1.1"
        writer!!.write("before note")

        // note opening tag
        val attrsNote = AttributesImpl()
        attrsNote.addAttribute(null, null, "n", null, "a")
        attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Jas.1.1!crossReference.a")
        attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Jas.1.1")
        attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "crossReference")
        noteHandler!!.start(attrsNote)

        // space between tags
        writer!!.write(" ")

        // reference 1
        val attrsRef1 = AttributesImpl()
        attrsRef1.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Matt.13.55")
        referenceHandler!!.start(attrsRef1)
        writer!!.write("Mt 13:55")
        referenceHandler!!.end()

        // seperator between tags
        writer!!.write("; ")

        // last reference -	<reference osisRef="Gal.1.19">Gal 1:19</reference>
        val attrsRef2 = AttributesImpl()
        attrsRef2.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gal.1.19")
        referenceHandler!!.start(attrsRef2)
        writer!!.write("Gal 1:19")
        referenceHandler!!.end()

        // space between tags
        writer!!.write(" ")
        noteHandler!!.end()
        writer!!.write("after note")
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo(String.format("before note<a href='%s:Jas.1.1/a' class='noteRef'>a</a> after note", Constants.NOTE_PROTOCOL)))
        val notesList: List<Note> = noteHandler!!.notesList
        Assert.assertThat(notesList.size, CoreMatchers.equalTo(2))
        val note1 = notesList[0]
        // do not use note text for refs but use ref key becasue ref text is often not set
        Assert.assertThat(note1.noteText, CoreMatchers.equalTo("Matthew 13:55"))
        Assert.assertThat(note1.noteRef, CoreMatchers.equalTo("a"))
        Assert.assertThat(note1.verseNo, CoreMatchers.equalTo(1))
        Assert.assertThat(note1.isNavigable, CoreMatchers.equalTo(true))
    }

    /**
     * Text and reference ESV Rev 20:2
     * This is not currently supported i.e. in the following example 'See ' is discarded
     * <note n="p" osisID="Rev.20.2!crossReference.p" osisRef="Rev.20.2" type="crossReference">See <reference osisRef="Rev.12.9">ch. 12:9</reference></note>
     */
    @Test
    fun crossTextAndReference() {
        verseInfo!!.currentVerseNo = 2
        verseInfo!!.osisID = "Rev.20.2"

        // note opening tag
        val attrsNote = AttributesImpl()
        attrsNote.addAttribute(null, null, "n", null, "p")
        attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Rev.20.2!crossReference.p")
        attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Rev.20.2")
        attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "crossReference")
        noteHandler!!.start(attrsNote)

        // space between tags
        //TODO this text is currently thrown away due to the presence of references - AB does not currently support x-refs and text in same note
        writer!!.write("See ")

        // reference 1
        val attrsRef1 = AttributesImpl()
        attrsRef1.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Rev.12.9")
        referenceHandler!!.start(attrsRef1)
        writer!!.write("ch. 12:9")
        referenceHandler!!.end()
        noteHandler!!.end()
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo(String.format("<a href='%s:Rev.20.2/p' class='noteRef'>p</a> ", Constants.NOTE_PROTOCOL)))
        val notesList: List<Note> = noteHandler!!.notesList
        Assert.assertThat(notesList.size, CoreMatchers.equalTo(1))
        val note = noteHandler!!.notesList[0]
        Assert.assertThat(note.noteText, CoreMatchers.equalTo("Revelation of John 12:9"))
        Assert.assertThat(note.noteRef, CoreMatchers.equalTo("p"))
        Assert.assertThat(note.verseNo, CoreMatchers.equalTo(2))
        Assert.assertThat(note.isNavigable, CoreMatchers.equalTo(true))
    }

    /**
     * Issue #652, links to notes did not work on the first chapter of a book.
     */
    @Test
    fun regressionFirstChapter() {
        verseInfo!!.osisID = "Gen.1.1"
        verseInfo!!.currentVerseNo = 1
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Gen.1.1!1")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gen.1.1")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "study")
        noteHandler!!.start(attrs)
        noteHandler!!.end()
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo(String.format("<a href='%s:Gen.1.1/1a' class='noteRef'>1a</a> ", Constants.NOTE_PROTOCOL)))
    } // Can remove isAutoWrapUnwrappedRefsInNote
}
