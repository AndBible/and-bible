/**
 * 
 */
package net.bible.service.format;

import junit.framework.TestCase;

/**
 * @author denha1m
 *
 */
public class OsisToCanonicalTextSaxHandlerTest extends TestCase {

	private OsisToCanonicalTextSaxHandler testClass;
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		testClass = new OsisToCanonicalTextSaxHandler();
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link net.scripture.service.format.OsisToCanonicalTextSaxHandler#startDocument()}.
	 */
	public void testStartDocument() throws Exception {
		String testString = "this is the test"; 
		char[] testChars = testString.toCharArray();
		testClass.startDocument();
		testClass.characters(testChars, 0, testChars.length);
		testClass.endDocument();
		assertEquals("startDocument failed", testClass.toString(), testString);
	}

	/**
	 * Test method for {@link net.scripture.service.format.OsisToCanonicalTextSaxHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)}.
	 */
	public void testStartElementStringStringStringAttributes() throws Exception {
		char[] written = "written text".toCharArray();
		char[] ignored = "ignored".toCharArray();
		
		testClass.startDocument();
		testClass.startElement(null, "verse", null, null);
		testClass.characters(written, 0, written.length); // this should be written

		testClass.startElement(null, "note", null, null);
		testClass.characters(ignored, 0, ignored.length); // this should be ignored
		testClass.endElement(null, "note", null);

		testClass.characters(written, 0, written.length); // this should be written
		testClass.endElement(null, "verse", null);
		testClass.endDocument();
		String result = testClass.toString();
		System.out.println(result);
		assertTrue("startElement failed", result.contains(new String(written)+new String(written)));
	}

}
