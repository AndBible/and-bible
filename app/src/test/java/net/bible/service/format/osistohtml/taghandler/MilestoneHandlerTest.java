package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OSISUtil2;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.PassageInfo;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.book.OSISUtil;
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
public class MilestoneHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	private PassageInfo passageInfo;
	private VerseInfo verseInfo;
	private HtmlTextWriter writer;
	
	private MilestoneHandler milestoneHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		passageInfo = new PassageInfo();
		verseInfo = new VerseInfo();
		writer = new HtmlTextWriter();
		
		milestoneHandler = new MilestoneHandler(osisToHtmlParameters, passageInfo, verseInfo, writer);
	}

	/**
	 * milestone marker='"' at start of verse
	 * diatheke -b ESV -f OSIS -k Jn 3:16
	 * 
	 * John 3:16: 
	 * <q marker=""><milestone marker="“" type="cQuote"/>For God ... eternal life.</q>
	 */
	@Test
	public void testQuote() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "“");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "cQuote");		
		milestoneHandler.start(attrs);
		milestoneHandler.end();

		writer.write("For God ... eternal life.");
		passageInfo.isAnyTextWritten = true;
		
		assertThat(writer.getHtml(), equalTo("“For God ... eternal life."));
	}

	/**
	 * milestone marker='"' at start of verse
	 * diatheke -b ESV -f OSIS -k Jn 3:16
	 * 
	 * John 3:16: 
	 * <q marker=""><milestone marker="“" type="cQuote"/>For God ... eternal life.</q>
	 */
	@Test
	public void testDefaultQuote() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "cQuote");		
		milestoneHandler.start(attrs);
		milestoneHandler.end();
		writer.write("For God ... eternal life.");
		
		assertThat(writer.getHtml(), equalTo("&quot;For God ... eternal life."));
	}

	/**
	 * KJV Gen 1:6
	 * <milestone marker="¶" type="x-p" /><w lemma="strong:H0430">And God</w>
	 */
	@Test
	public void testNewLine() {
		writer.write("passage start");
		passageInfo.isAnyTextWritten = true;
		verseInfo.positionToInsertBeforeVerse = writer.getPosition();
		verseInfo.isTextSinceVerse = true;
		
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "¶");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "line");		
		milestoneHandler.start(attrs);
		milestoneHandler.end();
		writer.write("And God");
		
		assertThat(writer.getHtml(), equalTo("passage start<br />And God"));
	}

	/**
	 * KJV Gen 1:6
	 * <milestone marker="¶" type="x-p" /><w lemma="strong:H0430">And God</w>
	 */
	@Test
	public void testNewLineXP() {
		writer.write("passage start");
		passageInfo.isAnyTextWritten = true;
		verseInfo.positionToInsertBeforeVerse = writer.getPosition();
		verseInfo.isTextSinceVerse = true;
		
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "¶");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p");		
		milestoneHandler.start(attrs);
		milestoneHandler.end();
		writer.write("And God");
		
		assertThat(writer.getHtml(), equalTo("passage start<br />And God"));
	}

	/**
	 * If verse marker has just been written then move BR to just before verse marker.
	 */
	@Test
	public void testNewLineMovedBeforeVerseNo() {
		writer.write("passage start");
		passageInfo.isAnyTextWritten = true;
		verseInfo.positionToInsertBeforeVerse = writer.getPosition();
		writer.write("<span class='verseNo' id='1'>1</span>");
		verseInfo.isTextSinceVerse = false;
		
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "¶");
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p");		
		milestoneHandler.start(attrs);
		milestoneHandler.end();
		writer.write("And God");
		
		assertThat(writer.getHtml(), equalTo("passage start<br /><span class='verseNo' id='1'>1</span>And God"));
	}
}
