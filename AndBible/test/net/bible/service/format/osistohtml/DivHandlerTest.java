package net.bible.service.format.osistohtml;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.PassageInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

public class DivHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	private PassageInfo passageInfo;
	private HtmlTextWriter htmlTextWriter;
	
	private DivHandler divHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		passageInfo = new PassageInfo();
		htmlTextWriter = new HtmlTextWriter();
		
		divHandler = new DivHandler(osisToHtmlParameters, passageInfo, htmlTextWriter);
	}

	/**
	 * This is the sort of example found but no reference to which module
	 * <div type='paragraph' sID='xyz'/>
	 * Some text
	 * <div type='paragraph' eID='xyz'/>
	 */
	@Test
	public void testCommonParagraphSidAndEid() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7681");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "paragraph");
		divHandler.start(attrs);
		divHandler.end();

		htmlTextWriter.write("Some text");
		passageInfo.isAnyTextWritten = true;
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7681");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "paragraph");
		divHandler.start(attrs2);
		divHandler.end();

		assertThat(htmlTextWriter.getHtml(), equalTo("Some text<p />"));
	}

	/**
	 * <div type='paragraph'>
	 * Some text
	 * </div>
	 */
	@Test
	public void testSimpleParagraph() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "paragraph");
		divHandler.start(attrs);

		htmlTextWriter.write("Some text");
		passageInfo.isAnyTextWritten = true;
		
		divHandler.end();

		assertThat(htmlTextWriter.getHtml(), equalTo("Some text<p />"));
	}
}
