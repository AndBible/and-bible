package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.Note;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class NoteHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	
	private VerseInfo verseInfo;
	
	private HtmlTextWriter writer;
	
	private ReferenceHandler referenceHandler;

	private NoteHandler noteHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		osisToHtmlParameters.setShowNotes(true);
		verseInfo = new VerseInfo();
		writer = new HtmlTextWriter();

		noteHandler = new NoteHandler(osisToHtmlParameters, verseInfo, writer);
		referenceHandler = new ReferenceHandler(osisToHtmlParameters, noteHandler, writer);
	}

	/**
	 * rusCARS
	 * 	<note osisID="Ezek.40.5!1" osisRef="Ezek.40.5" type="study"> Букв.:
	 *		«шесть долгих локтей (простой локоть с ладонью в каждом)».
	 *	</note>
	 */
	@Test
	public void simpleNote() {
		writer.write("before note");

		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5!1");		
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Ezek.40.5");		
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "study");		
		noteHandler.start(attrs);

		writer.write("Букв.: «шесть долгих локтей (простой локоть с ладонью в каждом)».");

		noteHandler.end();
		writer.write("after note");
		
		assertThat(writer.getHtml(), equalTo("before note<span class='noteRef'>a</span> after note"));
		List<Note> notesList = noteHandler.getNotesList();
		assertThat(notesList.size(), equalTo(1));
		assertThat(noteHandler.getNotesList().get(0).getNoteText(), equalTo("Букв.: «шесть долгих локтей (простой локоть с ладонью в каждом)»."));
	}


	/** Note */
	
	
	/**
	 * HunUj note containing refs - seems to give extra blank note at end:
	 * törzsnek.<note n="a" osisID="Jas.1.1!crossReference.a" osisRef="Jas.1.1" type="crossReference"> <reference osisRef="Matt.13.55">Mt 13:55</reference>; <reference osisRef="Mark.6.3">Mk 6:3</reference>; <reference osisRef="Acts.15.13">ApCsel 15:13</reference>; <reference osisRef="Gal.1.19">Gal 1:19</reference> </note>
	 */
	@Test
	public void crossReference() {
		verseInfo.currentVerseNo = 1;
		writer.write("before note");

		// note opening tag
		AttributesImpl attrsNote = new AttributesImpl();
		attrsNote.addAttribute(null, null, "n", null, "a");		
		attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Jas.1.1!crossReference.a");		
		attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Jas.1.1");		
		attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "crossReference");		
		noteHandler.start(attrsNote);

		// space between tags
		writer.write(" ");

		// reference 1
		AttributesImpl attrsRef1 = new AttributesImpl();
		attrsRef1.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Matt.13.55");		
		referenceHandler.start(attrsRef1);
		writer.write("Mt 13:55");
		referenceHandler.end();

		// seperator between tags
		writer.write("; ");
		
		// last reference -	<reference osisRef="Gal.1.19">Gal 1:19</reference>
		AttributesImpl attrsRef2 = new AttributesImpl();
		attrsRef2.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gal.1.19");		
		referenceHandler.start(attrsRef2);
		writer.write("Gal 1:19");
		referenceHandler.end();
		
		// space between tags
		writer.write(" ");

		noteHandler.end();
		writer.write("after note");
		
		assertThat(writer.getHtml(), equalTo("before note<span class='noteRef'>a</span> after note"));
		List<Note> notesList = noteHandler.getNotesList();
		assertThat(notesList.size(), equalTo(2));
		Note note1 = notesList.get(0);
		// do not use note text for refs but use ref key becasue ref text is often not set
		assertThat(note1.getNoteText(), equalTo("Matthew 13:55"));
		assertThat(note1.getNoteRef(), equalTo("a"));
		assertThat(note1.getVerseNo(), equalTo(1));
		assertThat(note1.isNavigable(), equalTo(true));
	}
	
	/**
	 * Text and reference ESV Rev 20:2
	 * This is not currently supported i.e. in the following example 'See ' is discarded
	 * <note n="p" osisID="Rev.20.2!crossReference.p" osisRef="Rev.20.2" type="crossReference">See <reference osisRef="Rev.12.9">ch. 12:9</reference></note>
	 */
	@Test
	public void crossTextAndReference() {
		verseInfo.currentVerseNo = 2;
		
		// note opening tag
		AttributesImpl attrsNote = new AttributesImpl();
		attrsNote.addAttribute(null, null, "n", null, "p");		
		attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Rev.20.2!crossReference.p");		
		attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Rev.20.2");		
		attrsNote.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "crossReference");		
		noteHandler.start(attrsNote);

		// space between tags
		//TODO this text is currently thrown away due to the presence of references - AB does not currently support x-refs and text in same note
		writer.write("See ");

		// reference 1
		AttributesImpl attrsRef1 = new AttributesImpl();
		attrsRef1.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Rev.12.9");		
		referenceHandler.start(attrsRef1);
		writer.write("ch. 12:9");
		referenceHandler.end();

		noteHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<span class='noteRef'>p</span> "));
		List<Note> notesList = noteHandler.getNotesList();
		assertThat(notesList.size(), equalTo(1));
		Note note = noteHandler.getNotesList().get(0);
		assertThat(note.getNoteText(), equalTo("Revelation of John 12:9"));
		assertThat(note.getNoteRef(), equalTo("p"));
		assertThat(note.getVerseNo(), equalTo(2));
		assertThat(note.isNavigable(), equalTo(true));
	}
	
	// Can remove isAutoWrapUnwrappedRefsInNote
}
