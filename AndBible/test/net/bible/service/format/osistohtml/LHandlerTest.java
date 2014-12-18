package net.bible.service.format.osistohtml;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

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
		lHandler.startL(attr);
		htmlTextWriter.write("Single line");
		lHandler.endL();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("Single line<br />"));
	}
	/**
	<ps117>
	<verse osisID='Ps.117.0'>
		<lg sID="w222" />
	</verse>
	<verse osisID='Ps.117.1'>
		<title subType="x-preverse" type="section">
			The
			<divineName>Lord</divineName>
			's Faithfulness Endures Forever
		</title>
		<l sID="x7681" />
		<note n="c" osisID="Ps.117.1!crossReference.c" osisRef="Ps.117.1"
			type="crossReference">
			Cited
			<reference osisRef="Rom.15.11">Rom. 15:11</reference>
		</note>
		Praise the
		<divineName>Lord</divineName>
		, all nations!
		<l eID="x7681" type="x-br" />
		<l sID="x7682" type="x-indent" />
		Extol him, all peoples!
		<l eID="x7682" type="x-br" />
	</verse>
	<verse osisID='Ps.117.2'>
		<l sID="x7683" />
		For
		<note n="d" osisID="Ps.117.2!crossReference.d" osisRef="Ps.117.2"
			type="crossReference">
			<reference osisRef="Ps.103.11">Ps. 103:11</reference>
			; [
			<reference osisRef="Ps.116.5">Ps. 116:5</reference>
			]
		</note>
		great is his steadfast love toward us,
		<l eID="x7683" type="x-br" />
		<l sID="x7684" type="x-indent" />
		and
		<note n="e" osisID="Ps.117.2!crossReference.e" osisRef="Ps.117.2"
			type="crossReference">
			[
			<reference osisRef="Ps.100.5">Ps. 100:5</reference>
			]
		</note>
		the faithfulness of the
		<divineName>Lord</divineName>
		endures forever.
		<l eID="x7684" type="x-br" />
		<l sID="x7685" />
		<note n="b" osisID="Ps.117.2!crossReference.b" osisRef="Ps.117.2"
			type="crossReference">
			[See
			<reference osisRef="Ps.116.19">Ps. 116:19</reference>
			above]
		</note>
		Praise the
		<divineName>Lord</divineName>
		!
		<l eID="x7685" />
		<lg eID="w222" />
	</verse>

</ps117>
**/
	/**
	 * ESV Ps 117:
	 <h1 class='heading1'>The Lord's Faithfulness Endures Forever</h1> <span class='verse' id='1'/>&#x200b;</span>Praise the Lord, all nations!<br />&#160;&#160;Extol him, all peoples!<br /> <span class='verse' id='2'/>&#x200b;</span>For great is his steadfast love toward us,<br />&#160;&#160;and the faithfulness of the Lord endures forever.<br />Praise the Lord!<br />
	 */
	
	/** ESV Ps.117.1
	 * <l sID="x7681" />Praise the Lord, all nations!<l eID="x7681" type="x-br" />
	 */
	@Test
	public void testLsIDeIDBr() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7681");
		lHandler.startL(attrs);
		lHandler.endL();
		
		htmlTextWriter.write("Praise the Lord, all nations!");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7681");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-br");
		lHandler.startL(attrs2);
		lHandler.endL();
		
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
		lHandler.startL(attrs);
		lHandler.endL();
		
		htmlTextWriter.write("Extol him, all peoples!");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7682");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-br");
		lHandler.startL(attrs2);
		lHandler.endL();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("&#160;&#160;Extol him, all peoples!<br />"));
	}

	/** ESV Ps.117.2 no type=x-br on eid, but still print BR
	 * <l sID="x7685" />Praise the Lord!<l eID="x7685"/>
	 */
	@Test
	public void testLsIDeID() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7685");
		lHandler.startL(attrs);
		lHandler.endL();
		
		htmlTextWriter.write("Praise the Lord!");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7685");
		lHandler.startL(attrs2);
		lHandler.endL();
		
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
		lHandler.startL(attrs);
		lHandler.endL();
		
		htmlTextWriter.write("Славьте Вечного, все народы,");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7685");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_LEVEL, null, "1");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-to-next-level");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-indent-1");
		lHandler.startL(attrs2);
		lHandler.endL();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("&#160;&#160;Славьте Вечного, все народы,<br />"));
	}
}
