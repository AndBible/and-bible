package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.common.Constants.HTML;
import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OSISUtil2;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.PassageInfo;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.VerseInfo;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** 
 * Continuation quotation marks
 * ----------------------------
 * The <milestone type="cQuote"/> can be used to indicate the presence of a continued quote. 
 * If the marker attribute is present, it will use that otherwise it will use a straight double quote, ". 
 * Since there is no level attribute on the milestone element, it is best to specify the marker attribute.
 * http://www.crosswire.org/wiki/OSIS_Bibles#Continuation_quotation_marks
 * 
 * Example from ESV
 * diatheke -b ESV -f OSIS -k Jn 3:16
 * John 3:16: 
 * <q marker=""><milestone marker="“" type="cQuote"/>For God ... eternal life.</q><milestone type="line"/>
 * 
 * 
 * New Line
 * --------
 * Can signify a new line is required
 * 
 * Example from KJV Gen 1:6
 * <verse osisID='Gen.1.6'><milestone marker="¶" type="x-p" /><w lemma="strong:H0430">And God</w>

 * 
 * Example from NETtext Mt 4:14

 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class MilestoneHandler implements OsisTagHandler {

	private HtmlTextWriter writer;

	@SuppressWarnings("unused")
	private OsisToHtmlParameters parameters;
	
	private PassageInfo passageInfo;
	private VerseInfo verseInfo;
	
	private static final String HTML_QUOTE_ENTITY = "&quot;";
	
	private static final Logger log = new Logger("OsisToHtmlSaxHandler");

	public MilestoneHandler(OsisToHtmlParameters parameters, PassageInfo passageInfo, VerseInfo verseInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.passageInfo = passageInfo;
		this.verseInfo = verseInfo;
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil2.OSIS_ELEMENT_MILESTONE;
    }

	@Override
	public void start(Attributes attrs) {
		String type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE);
		if (StringUtils.isNotEmpty(type)) {
			switch (type) {
				case "x-p":
				case "line":
					if (passageInfo.isAnyTextWritten) {
						// if no verse text has yet been written then place the BR before the verse number
						writer.writeOptionallyBeforeVerse(HTML.BR, verseInfo);
					}
					break;
				case "cQuote":
					String marker = TagHandlerHelper.getAttribute(OSISUtil2.OSIS_ATTR_MARKER, attrs, HTML_QUOTE_ENTITY);
					writer.write(marker);
					break;
				default:
					log.debug("Verse "+verseInfo.currentVerseNo+" unsupported milestone type:"+type);
					break;
			}
		}
	}

	@Override
	public void end() {
	}
}
