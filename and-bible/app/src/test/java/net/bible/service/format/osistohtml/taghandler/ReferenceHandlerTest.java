package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Continuation quotation marks
 * The <milestone type="cQuote"/> can be used to indicate the presence of a continued quote. 
 * If the marker attribute is present, it will use that otherwise it will use a straight double quote, ". 
 * Since there is no level attribute on the milestone element, it is best to specify the marker attribute.
 * http://www.crosswire.org/wiki/OSIS_Bibles#Continuation_quotation_marks
 */
public class ReferenceHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	
	private VerseInfo verseInfo;
	
	private NoteHandler noteHandler;
	
	private HtmlTextWriter writer;
	
	private ReferenceHandler referenceHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		verseInfo = new VerseInfo();
		writer = new HtmlTextWriter();

		noteHandler = new NoteHandler(osisToHtmlParameters, verseInfo, writer);
		
		referenceHandler = new ReferenceHandler(osisToHtmlParameters, noteHandler, writer);
	}

	/**
	 * Test OSIS key reference
	 * Abbott Rev 15:3: "(<reference osisRef="Exod.15.1-Exod.15.19">Ex. 15:1-19</reference>.)"
	 */
	@Test
	public void testOSISReference() {
		writer.write("(");

		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Exod.15.1-Exod.15.19");		
		referenceHandler.start(attrs);

		writer.write("Ex. 15:1-19");

		referenceHandler.end();
		writer.write(".)");
		
		assertThat(writer.getHtml(), equalTo("(<a href='bible:Exod.15.1-Exod.15.19'>Ex. 15:1-19</a>.)"));
	}

	/**
	 * When there is no osis ref the content is used if it is a valid reference
	 */

	@Test
	public void testGenBookUrlWithSpace() {
		writer.write("(");

		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "ESV Study Bible Articles:Book introductions");
		referenceHandler.start(attrs);

		writer.write("Some content");

		referenceHandler.end();
		writer.write(".)");

		assertThat(writer.getHtml(), equalTo("(<a href='sword://ESV Study Bible Articles/Book introductions'>Some content</a>.)"));
	}

	/**
	 * When there is no osis ref the content is used if it is a valid reference
	 */
	@Test
	public void testNoOSISReference() {
		writer.write("(");

		AttributesImpl attrs = new AttributesImpl();
		referenceHandler.start(attrs);

		writer.write("Ex. 15:1-19");

		referenceHandler.end();
		writer.write(".)");
		
		assertThat(writer.getHtml(), equalTo("(<a href='bible:Exod.15.1'>Exodus 15:1-19</a>.)"));
	}

	@Test
	public void testReferenceContentSingleRef() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Ps.121.2");
		referenceHandler.start(attrs);

		writer.write("121:2");

		referenceHandler.end();

		// note that just the first verse in each range is referenced - it might be better to reference the whole range although the final navigation when pressed would be ifentical
		assertThat(writer.getHtml(), equalTo("<a href='bible:Ps.121.2'>121:2</a>"));
	}

	@Test
	public void testReferenceContentWithSingleRange() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gen.1.1-Gen.2.1");
		referenceHandler.start(attrs);

		writer.write("1:1-2:1");

		referenceHandler.end();

		// note that just the first verse in each range is referenced - it might be better to reference the whole range although the final navigation when pressed would be ifentical
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gen.1-Gen.2.1'>1:1-2:1</a>"));
	}

	@Test
	public void testReferenceContentWithSingleRange2() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gen.1.2-Gen.2.1");
		referenceHandler.start(attrs);

		writer.write("1:2-2:1");

		referenceHandler.end();

		// note that just the first verse in each range is referenced - it might be better to reference the whole range although the final navigation when pressed would be ifentical
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gen.1.2-Gen.2.1'>1:2-2:1</a>"));
	}

	/**
	 * Test long complex ref from TSk Psa 118:2
	 * Ref:Ps.115.9-Ps.115.11 Ps.135.19-Ps.135.20 Ps.145.10 Ps.147.19-Ps.147.20 Gal.6.16 Heb.13.15 Content:115:9-11; 135:19,20; 145:10; 147:19,20; Ga 6:16; Heb 13:15 
	 */
	@Test
	public void testComplexReference() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Ps.115.9-Ps.115.11 Ps.135.19-Ps.135.20 Ps.145.10 Ps.147.19-Ps.147.20 Gal.6.16 Heb.13.15");		
		referenceHandler.start(attrs);

		writer.write("115:9-11; 135:19,20; 145:10; 147:19,20; Ga 6:16; Heb 13:15");

		referenceHandler.end();

		// note that just the first verse in each range is referenced - it might be better to reference the whole range although the final navigation when pressed would be ifentical
		assertThat(writer.getHtml(), equalTo("<a href='bible:Ps.115.9'>Psalms 115:9-11</a> <a href='bible:Ps.135.19'>Psalms 135:19-20</a> <a href='bible:Ps.145.10'>Psalms 145:10</a> <a href='bible:Ps.147.19'>Psalms 147:19-20</a> <a href='bible:Gal.6.16'>Galatians 6:16</a> <a href='bible:Heb.13.15'>Hebrews 13:15</a>"));
	}
	
	/**
	 * If osisref is null and content is partial reference use basis to extend content to full ref
	 * E.g. 1 or 2 preceding the actual verse in TSK.  No examples with 3 digits can be found in TSK
	 * ref=1
	 * ref=12
	 * Use basis for ref=chap:verseNo
	 * ref=1:2
	 */
	@Test
	public void testVerseContentBasisReference() {
		osisToHtmlParameters.setBasisRef(getVerse(BibleBook.GAL, 1, 1));
		AttributesImpl attrs = new AttributesImpl();
		referenceHandler.start(attrs);
		writer.write("3");
		referenceHandler.end();
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gal.1.3'>3</a>"));

		writer.reset();
		referenceHandler.start(attrs);
		writer.write("12");
		referenceHandler.end();
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gal.1.12'>12</a>"));
	}

	/**
	 * If osisref is null and content is partial reference use basis to extend content to full ref
	 */
	@Test
	public void testChapterVerseContentBasisReference() {
		osisToHtmlParameters.setBasisRef(getVerse(BibleBook.GAL, 1, 1));
		AttributesImpl attrs = new AttributesImpl();
		referenceHandler.start(attrs);
		writer.write("2:9");
		referenceHandler.end();
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gal.2.9'>2:9</a>"));
	}
	
	/**
	 * ref= book:key
	 * E.g. StrongsRealGreek:01909
	 */
	@Test
	public void testStrongsReference() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "StrongsGreek:01909");		
		referenceHandler.start(attrs);

		writer.write("01909");

		referenceHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<a href='sword://StrongsGreek/01909'>01909</a>"));
	}
	
	/**
	 * Full sword reference to book/key with sword:// prefix
	 * ref= sword://book:key
	 * <a href="sword://StrongsRealHebrew/00433">433</a>
	 */
	@Test
	public void testFullSwordReference() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "sword://StrongsRealHebrew/00433");		
		referenceHandler.start(attrs);

		writer.write("00433");

		referenceHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<a href='sword://StrongsRealHebrew/00433'>00433</a>"));
	}

	/**
	 * ref = single verse.  Content is short e.g. '6'.
	 */
	@Test
	public void testSingleVerseShortContent() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gal.1.6");		
		referenceHandler.start(attrs);

		writer.write("6");

		referenceHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gal.1.6'>6</a>"));
	}

	/**
	 * ref = single verse.  Content is complex e.g. 'Matt 1:2'.
	 * I am not sure why this is handled differently to the single verse short content
	 */
	@Test
	public void testNoContent() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gal.1.6");		
		referenceHandler.start(attrs);
		referenceHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gal.1.6'>Galatians 1:6</a>"));
	}

	/**
	 * ref = multi verse.  Content is short.
	 */
	@Test
	public void testMultiVerseShortContent() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_REF, null, "Gal.1.3 Gal.1.6");		
		referenceHandler.start(attrs);

		writer.write("3,6");

		referenceHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<a href='bible:Gal.1.3'>Galatians 1:3</a> <a href='bible:Gal.1.6'>Galatians 1:6</a>"));
	}

	private Verse getVerse(BibleBook book, int chapter, int verse) {
		Versification v11n = Versifications.instance().getVersification(SystemKJV.V11N_NAME);
		return new Verse(v11n, book, chapter, verse);
	}
}
