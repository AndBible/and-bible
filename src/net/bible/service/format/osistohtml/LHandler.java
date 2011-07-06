package net.bible.service.format.osistohtml;

import java.util.Stack;

import net.bible.service.common.Constants.HTML;
import net.bible.service.sword.Logger;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** This can either signify a quote or Red Letter
 * Example from ESV 
 * 		But he answered them, <q marker="" who="Jesus"><q level="1" marker="“" sID="40024002.1"/>You see all these
 * Example from KJV
 * 		said ... unto them, <q who="Jesus">...See ye
 * 
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class LHandler {

	enum LType {indent, br};

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	private Stack<LType> stack = new Stack<LType>();
	
	private static final Logger log = new Logger("LHandler");

	public LHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return "l";
    }

	public void startL(Attributes attrs) {
		// Refer to Gen 3:14 in ESV for example use of type=x-indent
		String type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE);
		if (StringUtils.isNotEmpty(type)) {
			if (type.contains("indent")) {
				writer.write(HTML.NBSP+HTML.NBSP);
				stack.push(LType.indent);
			} else if (type.contains("br")) {
				writer.write(HTML.BR);
				stack.push(LType.br);
			} else {
				log.debug("Unknown <l> tag type:"+type);
			}
		} else {
			log.debug("Ignoring <l> tag with no type");
		}
	}

	public void endL() {
	}
}
