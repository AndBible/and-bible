package net.bible.service.format.osistohtml.taghandler;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.PassageInfo;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

public class DivHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	private PassageInfo passageInfo;
	private VerseInfo verseInfo;
	private HtmlTextWriter htmlTextWriter;
	
	private DivHandler divHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		passageInfo = new PassageInfo();
		verseInfo = new VerseInfo();
		htmlTextWriter = new HtmlTextWriter();
		
		divHandler = new DivHandler(osisToHtmlParameters, verseInfo, passageInfo, htmlTextWriter);
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

	/**
	 * Osis2mod has started using type="x-p" for paragraphs, see JS-292
	 * <div type='x-p' sID='xyz'/>
	 * Some text
	 * <div type='x-p' eID='xyz'/>
	 */
	@Test
	public void testNewFreJndParagraphs() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7681");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p");
		divHandler.start(attrs);
		divHandler.end();

		htmlTextWriter.write("Some text");
		passageInfo.isAnyTextWritten = true;
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7681");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p");
		divHandler.start(attrs2);
		divHandler.end();

		assertThat(htmlTextWriter.getHtml(), equalTo("Some text<p />"));
	}
	
	/**
	 * Some pre-verse titles are not marked as pre-verse directly but are wrapped in a div marked as pre-verse
	 * E.g. ESVS Ps 25:	 * 
	 * <div><verse osisID='Ps.25.1'><div type="x-milestone" subType="x-preverse" sID="pv2905"/> <title>Teach Me Your Paths</title> <title canonical="true" type="psalm"> Of David.</title> <div type="x-milestone" subType="x-preverse" eID="pv2905"/>
	 */
	@Test
	public void testEsvsTitleInPreVerseDiv() {
		// verse comes first
		htmlTextWriter.write("v1");
		verseInfo.currentVerseNo = 1;
		verseInfo.positionToInsertBeforeVerse = 0;
		verseInfo.isTextSinceVerse = false;

		// then a div with a pre-verse attribute
		AttributesImpl attrsSidOpen = new AttributesImpl();
		attrsSidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-preverse");
		attrsSidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "pv2905");
		divHandler.start(attrsSidOpen);
		divHandler.end();

		htmlTextWriter.write("Preverse text");
		passageInfo.isAnyTextWritten = true;
		
		AttributesImpl attrsEidOpen = new AttributesImpl();
		attrsEidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-preverse");
		attrsEidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "pv2905");
		divHandler.start(attrsEidOpen);
		divHandler.end();

		htmlTextWriter.write("Verse content");

		assertThat(htmlTextWriter.getHtml(), equalTo("Preverse textv1Verse content"));
	}
}
