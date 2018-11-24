package net.bible.service.format.osistohtml.osishandlers;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class OsisToSpeakTextSaxHandlerTest {
	private OsisToSpeakTextSaxHandler osisToSpeakTextSaxHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToSpeakTextSaxHandler = new OsisToSpeakTextSaxHandler(true);
	}

	@Test
	public void testTopLevelReferenceIsWritten() throws Exception {
		osisToSpeakTextSaxHandler.startDocument();
		osisToSpeakTextSaxHandler.startElement(null, null, "reference", null);
		osisToSpeakTextSaxHandler.characters("something".toCharArray(), 0, 9);
		osisToSpeakTextSaxHandler.endElement(null, null, "reference");
		osisToSpeakTextSaxHandler.endDocument();
		
		assertThat(osisToSpeakTextSaxHandler.getWriter().getHtml(), equalTo("something"));
	}
	
	@Test
	public void testNoteWithReferenceIsAlsoWritten() throws Exception {
		osisToSpeakTextSaxHandler.startDocument();
		osisToSpeakTextSaxHandler.startElement(null, null, "note", null);
		osisToSpeakTextSaxHandler.characters("inNoteBeforeRef".toCharArray(), 0, 15);
		osisToSpeakTextSaxHandler.startElement(null, null, "reference", null);

		osisToSpeakTextSaxHandler.characters("something".toCharArray(), 0, 9);

		osisToSpeakTextSaxHandler.endElement(null, null, "reference");
		osisToSpeakTextSaxHandler.characters("inNoteAfterRef".toCharArray(), 0, 14);
		osisToSpeakTextSaxHandler.endElement(null, null, "note");
		osisToSpeakTextSaxHandler.endDocument();
		
		assertThat(osisToSpeakTextSaxHandler.getWriter().getHtml(), equalTo("something"));
	}
}
