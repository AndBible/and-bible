package net.bible.service.format;

import junit.framework.TestCase;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler;

import org.xml.sax.helpers.AttributesImpl;

public class OsisToHtmlSaxHandlerTest extends TestCase {
	
	private OsisToHtmlSaxHandler testClass;
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		try {
		testClass = new OsisToHtmlSaxHandler(new OsisToHtmlParameters());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link net.scripture.service.format.OsisToCanonicalTextSaxHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)}.
	 */
	public void testStartElementStringStringStringAttributes() throws Exception {
		char[] written = "written text".toCharArray();
		char[] ignored = "ignored".toCharArray();
//		testClass.setShowNotes(false);
		
		testClass.startDocument();
		testClass.startElement(null, "verse", null, new AttributesImpl());
		testClass.characters(written, 0, written.length); // this should be written

		testClass.startElement(null, "note", null, new AttributesImpl());
		testClass.characters(ignored, 0, ignored.length); // this should be ignored
		testClass.endElement(null, "note", null);

		testClass.characters(written, 0, written.length); // this should be written
		testClass.endElement(null, "verse", null);
		testClass.endDocument();
		
		String result = testClass.toString();
		System.out.println(result);
		assertTrue(result.contains(" dir='ltr'"));
		assertTrue(result.contains("written textwritten text"));
		assertTrue(!result.contains("ignored"));
	}
	
	/**
	 * <w lemma="strong:H07225">In the beginning</w> <w lemma="strong:H0430">God</w> <w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w>
	 */
	public void testStrongs() {
		String input = "<w lemma=\"strong:H07225\">In the beginning</w> <w lemma=\"strong:H0430\">God</w> <w lemma=\"strong:H0853 strong:H01254\" morph=\"strongMorph:TH8804\">created</w>";
	}
	
	public void testHtmlTextWriterInsert() {
		HtmlTextWriter htmlTextWriter = new HtmlTextWriter();
		htmlTextWriter.write("123");
		int posn = htmlTextWriter.getPosition();
		htmlTextWriter.write("789");
		htmlTextWriter.beginInsertAt(posn);
		htmlTextWriter.write("456");
		htmlTextWriter.finishInserting();
		htmlTextWriter.write("ABC");
		String result = htmlTextWriter.getHtml();
		System.out.println(result);
	}

}
