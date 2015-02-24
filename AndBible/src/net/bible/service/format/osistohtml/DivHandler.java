package net.bible.service.format.osistohtml;

import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.PassageInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** This can either signify a quote or Red Letter
 * Example from ESV Prov 19:1
 * 		<l sID="x9938"/>...<l eID="x9938" type="x-br"/><l sID="x9939" type="x-indent"/>..<l eID="x9939" type="x-br"/>
 * 
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 * 
 * http://www.crosswire.org/wiki/List_of_eXtensions_to_OSIS_used_in_SWORD
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class DivHandler implements OsisTagHandler {

	private enum DivType {PARAGRAPH, IGNORE};

	private HtmlTextWriter writer;
	
	@SuppressWarnings("unused")
	private OsisToHtmlParameters parameters;
	
	private PassageInfo passageInfo;
	
	private Stack<DivType> stack = new Stack<DivType>();
	
	private static List<String> PARAGRAPH_TYPE_LIST = Arrays.asList("paragraph", "x-p", "x-end-paragraph");
	
	@SuppressWarnings("unused")
	private static final Logger log = new Logger("DivHandler");

	public DivHandler(OsisToHtmlParameters parameters, PassageInfo passageInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.passageInfo = passageInfo;
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_DIV;
    }

	@Override
	public void start(Attributes attrs) {
		DivType divType = DivType.IGNORE;
		String type = attrs.getValue("type");
		if (PARAGRAPH_TYPE_LIST.contains(type)) {
			// ignore sID start paragraph sID because it often comes after the verse no and causes a gap between verse no verse text
			// could enhance this to use writeOptionallyBeforeVerse('<p>') and then write </p> in end() if there is no sID or eID 
			String sID = attrs.getValue("sID");
			if (sID==null) {
				divType = DivType.PARAGRAPH;
			}
		}
		stack.push(divType);
	}

	@Override
	public void end() {
		DivType type = stack.pop();
		if (DivType.PARAGRAPH.equals(type) && passageInfo.isAnyTextWritten) {
			writer.write("<p />");
		}
	}
}
