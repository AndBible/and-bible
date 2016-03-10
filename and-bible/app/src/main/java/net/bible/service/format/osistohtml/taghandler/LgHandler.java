package net.bible.service.format.osistohtml.taghandler;

import java.util.Stack;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** The lg or "line group" element is used to contain any group of poetic lines.  Poetic lines are handled at the line level by And Bible, not line group 
 * so this class does nothing.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
@SuppressWarnings("unused")
public class LgHandler implements OsisTagHandler {

	enum LGType {DIV, IGNORE};

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	private Stack<LGType> stack = new Stack<LGType>();
	
	private static final Logger log = new Logger("LGHandler");

	public LgHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}

	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_LG;
    }

	@Override
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

	@Override
	public void end() {
//		LGType lgtype = stack.pop();
//		if (LGType.DIV.equals(lgtype)) {
//			writer.write("</div>");
//		}
	}
}
