package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class LHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	private HtmlTextWriter htmlTextWriter;
	
	private LHandler lHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		htmlTextWriter = new HtmlTextWriter();
		
		lHandler = new LHandler(osisToHtmlParameters, htmlTextWriter);
	}

	/**
	 * No attributes. Just start title, write title, end title.
	 * 
	 * <l>Single line</l>
	 */
	@Test
	public void testSimpleL() {
		Attributes attr = new AttributesImpl();
		lHandler.start(attr);
		htmlTextWriter.write("Single line");
		lHandler.end();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("Single line<br />"));
	}

	/**
	 * ESV Ps 117:
	 <h1 class='heading1'>The Lord's Faithfulness Endures Forever</h1> <span class='verseNo' id='1'/>&#x200b;</span>Praise the Lord, all nations!<br />&#160;&#160;Extol him, all peoples!<br /> <span class='verseNo' id='2'/>&#x200b;</span>For great is his steadfast love toward us,<br />&#160;&#160;and the faithfulness of the Lord endures forever.<br />Praise the Lord!<br />
	 */
	
	/** ESV Ps.117.1
	 * <l sID="x7681" />Praise the Lord, all nations!<l eID="x7681" type="x-br" />
	 */
	@Test
	public void testLsIDeIDBr() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7681");
		lHandler.start(attrs);
		lHandler.end();
		
		htmlTextWriter.write("Praise the Lord, all nations!");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7681");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-br");
		lHandler.start(attrs2);
		lHandler.end();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("Praise the Lord, all nations!<br />"));
	}
	
	/** ESV Ps.117.1
	<l sID="x7682" type="x-indent" />Extol him, all peoples!<l eID="x7682" type="x-br" />
	*/
	@Test
	public void testLsIDIndent() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7682");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-indent");
		lHandler.start(attrs);
		lHandler.end();
		
		htmlTextWriter.write("Extol him, all peoples!");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7682");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-br");
		lHandler.start(attrs2);
		lHandler.end();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("&#160;&#160;Extol him, all peoples!<br />"));
	}

	/** ESV Ps.117.2 no type=x-br on eid, but still print BR
	 * <l sID="x7685" />Praise the Lord!<l eID="x7685"/>
	 */
	@Test
	public void testLsIDeID() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7685");
		lHandler.start(attrs);
		lHandler.end();
		
		htmlTextWriter.write("Praise the Lord!");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7685");
		lHandler.start(attrs2);
		lHandler.end();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("Praise the Lord!<br />"));
	}

	/** 
	 * An eID should always cause a BR no matter what other attributes are associated with it
	 * CARS Ps.116 (actually 117 because Synodal)
	 * <l level="1" sID="gen18394" subType="x-to-next-level" type="x-indent-1" />
	 *			Славьте Вечного, все народы,
	 * <l eID="gen18394" level="1" subType="x-to-next-level" type="x-indent-1" />
	 */
	@Test
	public void testLeIDAlwaysAddsBr() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_LEVEL, null, "1");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "gen18394");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-to-next-level");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-indent-1");
		lHandler.start(attrs);
		lHandler.end();
		
		htmlTextWriter.write("Славьте Вечного, все народы,");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7685");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_LEVEL, null, "1");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-to-next-level");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-indent-1");
		lHandler.start(attrs2);
		lHandler.end();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("&#160;&#160;Славьте Вечного, все народы,<br />"));
	}
}
