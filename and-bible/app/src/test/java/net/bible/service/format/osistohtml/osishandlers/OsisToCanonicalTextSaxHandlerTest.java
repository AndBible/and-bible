package net.bible.service.format.osistohtml.osishandlers;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.Assert.assertThat;

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
		osisToCanonicalTextSaxHandler.characters("something".toCharArray(), 0, 9);
		osisToCanonicalTextSaxHandler.endElement(null, null, "reference");
		
		assertThat(osisToCanonicalTextSaxHandler.getWriter().getHtml(), equalTo("something"));
	}
	
	@Test
	public void testReferenceInNoteIsNotWritten() throws Exception {
		osisToCanonicalTextSaxHandler.startElement(null, null, "note", null);
		osisToCanonicalTextSaxHandler.startElement(null, null, "reference", null);

		osisToCanonicalTextSaxHandler.characters("something".toCharArray(), 0, 9);

		osisToCanonicalTextSaxHandler.endElement(null, null, "reference");
		osisToCanonicalTextSaxHandler.endElement(null, null, "note");
		
		assertThat(osisToCanonicalTextSaxHandler.getWriter().getHtml(), isEmptyString());
	}

	@Test
	public void testExtraSpacesAreRemoved() throws Exception {
		osisToCanonicalTextSaxHandler.startElement(null, null, "verse", null);
		osisToCanonicalTextSaxHandler.characters("  ".toCharArray(), 0, 2);
		osisToCanonicalTextSaxHandler.characters(" ".toCharArray(), 0, 1);
		osisToCanonicalTextSaxHandler.characters("Be exalted,".toCharArray(), 0, 11);
		osisToCanonicalTextSaxHandler.characters(" ".toCharArray(), 0, 1);
		osisToCanonicalTextSaxHandler.characters("  ".toCharArray(), 0, 2);
		osisToCanonicalTextSaxHandler.characters("O God".toCharArray(), 0, 5);
		osisToCanonicalTextSaxHandler.endElement(null, null, "verse");

		// the last verse tag just add an extra space at the end but that is required to separate verses and is easier to leave.
		assertThat(osisToCanonicalTextSaxHandler.getWriter().getHtml(), equalTo("Be exalted, O God "));
	}

}
