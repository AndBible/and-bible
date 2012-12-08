package net.bible.service.format.osistohtml;

import java.util.Stack;

import net.bible.service.common.Logger;

import org.xml.sax.Attributes;

/** This can either signify a quote or Red Letter
 * Example from ESV Prov 19:1
 * 		<l sID="x9938"/>...<l eID="x9938" type="x-br"/><l sID="x9939" type="x-indent"/>..<l eID="x9939" type="x-br"/>
 * 
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class LGHandler {

	enum LGType {DIV, IGNORE};

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	private Stack<LGType> stack = new Stack<LGType>();
	
	private static final Logger log = new Logger("LGHandler");

	public LGHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	public String getTagName() {
        return "lg";
    }

	public void start(Attributes attrs) {
// ignore this for now because it is untested
//		LGType lgtype = LGType.IGNORE;
//		if (TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_SID, attrs) ||
//			TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_EID, attrs)) {
//			lgtype = LGType.IGNORE;
//		} else {
//			// allow spacing around groups of poetry
//			writer.write("<div class='lg'>");
//			lgtype = LGType.DIV;
//		}
//		stack.push(lgtype);
	}

	public void end() {
//		LGType lgtype = stack.pop();
//		if (LGType.DIV.equals(lgtype)) {
//			writer.write("</div>");
//		}
	}
}
