package net.bible.service.format.osistohtml.osishandlers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

public class OsisToCanonicalTextSaxHandlerTest {

	private OsisToCanonicalTextSaxHandler osisToCanonicalTextSaxHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToCanonicalTextSaxHandler = new OsisToCanonicalTextSaxHandler();
		osisToCanonicalTextSaxHandler.startDocument();
	}

	@Test
	public void testTopLevelReferenceIsWritten() throws Exception {
		osisToCanonicalTextSaxHandler.startElement(null, null, "reference", null);
		osisToCanonicalTextSaxHandler.characters("something".toCharArray(), 0, 9);;
		osisToCanonicalTextSaxHandler.endElement(null, null, "reference");
		
		assertThat(osisToCanonicalTextSaxHandler.getWriter().getHtml(), equalTo("something"));
	}
	
	@Test
	public void testReferenceInNoteIsNotWritten() throws Exception {
		osisToCanonicalTextSaxHandler.startElement(null, null, "note", null);
		osisToCanonicalTextSaxHandler.startElement(null, null, "reference", null);

		osisToCanonicalTextSaxHandler.characters("something".toCharArray(), 0, 9);;

		osisToCanonicalTextSaxHandler.endElement(null, null, "reference");
		osisToCanonicalTextSaxHandler.endElement(null, null, "note");
		
		assertThat(osisToCanonicalTextSaxHandler.getWriter().getHtml(), isEmptyString());
	}
}
