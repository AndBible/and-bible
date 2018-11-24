package net.bible.service.format.osistohtml.strongs;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Strongs Handler tests - includes morphology
 */
public class StrongsHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	
	private HtmlTextWriter writer;
	
	private StrongsHandler strongsHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		osisToHtmlParameters.setShowStrongs(true);
		osisToHtmlParameters.setShowMorphology(true);
		
		writer = new HtmlTextWriter();

		strongsHandler = new StrongsHandler(osisToHtmlParameters, writer);
	}

	/**
	 * Test Single Strongs reference
	 * <w lemma="strong:H07225">In the beginning</w>
	 */
	@Test
	public void testLoneStrongs() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_LEMMA, null, "strong:H07225");		
		strongsHandler.start(attrs);

		writer.write("In the beginning");

		strongsHandler.end();
		
		assertThat(writer.getHtml(), equalTo("In the beginning <a href='hdef:07225' class='strongs'>07225</a> "));
	}

	/**
	 * Test Strongs and unknown Morphology reference
	 * <w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w>
	 */
	@Test
	public void testStrongsAndUnknownMorph() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_LEMMA, null, "strong:H0853");		
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_MORPH, null, "strongMorph:TH8804");
		strongsHandler.start(attrs);

		writer.write("In the beginning");

		strongsHandler.end();
		
		assertThat(writer.getHtml(), equalTo("In the beginning <a href='hdef:00853' class='strongs'>0853</a> "));
	}
	
	/**
	 * Test Strongs and unknown Morphology reference
	 * <w lemma="strong:H0853 strong:H01254" morph="robinson:N-PRI">text</w>
	 */
	@Test
	public void testMultipleStrongsAndKnownMorph() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_LEMMA, null, "strong:H0853 strong:H01254");		
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_MORPH, null, "robinson:N-PRI");
		strongsHandler.start(attrs);

		writer.write("text");

		strongsHandler.end();
		
		assertThat(writer.getHtml(), equalTo("text <a href='hdef:01254' class='strongs'>01254</a> <a href='hdef:00853' class='strongs'>0853</a><a href='robinson:N-PRI' class='morphology'>N-PRI</a> "));
	}
	
	/**
	 * Test Strongs and known Morphology reference
	 * <w lemma="strong:G11" morph="robinson:N-PRI">text</w>
	 */
	@Test
	public void testStrongsAndKnownMorph() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_LEMMA, null, "strong:G11");		
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_MORPH, null, "robinson:N-PRI");
		strongsHandler.start(attrs);

		writer.write("text");

		strongsHandler.end();
		
		assertThat(writer.getHtml(), equalTo("text <a href='gdef:00011' class='strongs'>11</a><a href='robinson:N-PRI' class='morphology'>N-PRI</a> "));
	}

	/**
	 * Test lone Morphology reference
	 * <w morph="robinson:N-PRI"/>
	 */
	@Test
	public void testLoneMorph() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_W_MORPH, null, "robinson:N-PRI");
		strongsHandler.start(attrs);

		writer.write("text");

		strongsHandler.end();
		
		assertThat(writer.getHtml(), equalTo("text <a href='robinson:N-PRI' class='morphology'>N-PRI</a> "));
	}
}
