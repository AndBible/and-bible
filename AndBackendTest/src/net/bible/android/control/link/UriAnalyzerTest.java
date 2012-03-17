package net.bible.android.control.link;

import java.net.URI;

import junit.framework.TestCase;
import net.bible.android.control.link.UriAnalyzer.DocType;

public class UriAnalyzerTest extends TestCase {

	private UriAnalyzer uriAnalyzer = new UriAnalyzer();
	
	public void testFullSwordUrn() {
		String uri = "sword://StrongsRealGreek/01909";
		uriAnalyzer.analyze(uri);
		assertEquals(DocType.SPECIFIC_DOC, uriAnalyzer.getDocType());
		assertEquals("StrongsRealGreek", uriAnalyzer.getBook());
		assertEquals("01909", uriAnalyzer.getKey());
	}
	
	public void testDefaultBibleUrn() {
		String uri = "sword:///Gen.1.1";
		uriAnalyzer.analyze(uri);
		assertEquals(DocType.SPECIFIC_DOC, uriAnalyzer.getDocType());
		assertEquals("StrongsRealGreek", uriAnalyzer.getBook());
		assertEquals("01909", uriAnalyzer.getKey());
	}

	/** standard URI is: sword://module/key
	 * see http://www.crosswire.org/wiki/Frontends:URI_Standard
	 * and http://www.mail-archive.com/sword-devel@crosswire.org/msg20670.html
	 */
	public void testUrl() {
		try {
			URI uri = new URI("sword://StrongsRealGreek/01909");
			assertEquals("sword", uri.getScheme());
			assertEquals("StrongsRealGreek", uri.getHost());
			assertEquals("/01909", uri.getPath());
			assertEquals("/01909", uri.getRawPath());
			assertEquals(null, uri.getQuery());
			assertEquals(null, uri.getFragment());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


}
