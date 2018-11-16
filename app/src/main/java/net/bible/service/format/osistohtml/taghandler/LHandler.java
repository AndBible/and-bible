package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.common.Constants.HTML;
import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

import java.util.Stack;

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
 *	  The copyright to this program is held by it's author. 
 */
public class LHandler implements OsisTagHandler {

	private enum LType {INDENT, BR, END_BR, IGNORE}

	private HtmlTextWriter writer;
	
	@SuppressWarnings("unused")
	private OsisToHtmlParameters parameters;
	
	private Stack<LType> stack = new Stack<>();
	
	private static String indent_html = HTML.NBSP+HTML.NBSP;
	
	private static final Logger log = new Logger("LHandler");

	public LHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
		int indentCharCount = parameters.getIndentDepth();
		indent_html = StringUtils.repeat(HTML.NBSP, indentCharCount);
	}
	
	
	/* (non-Javadoc)
	 * @see net.bible.service.format.osistohtml.Handler#getTagName()
	 */
	@Override
	public String getTagName() {
		return OSISUtil.OSIS_ELEMENT_L;
	}

	/* (non-Javadoc)
	 * @see net.bible.service.format.osistohtml.Handler#start(org.xml.sax.Attributes)
	 */
	@Override
	public void start(Attributes attrs) {
		// Refer to Gen 3:14 in ESV for example use of type=x-indent
		String type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE);
		int level = TagHandlerHelper.getAttribute(OSISUtil.OSIS_ATTR_LEVEL, attrs, 1);
		// make numIndents default to zero
		int numIndents = Math.max(0, level-1);
		
		LType ltype;
		if (TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_EID, attrs)) {
			// e.g. Isaiah 40:12
			writer.write(HTML.BR);
			ltype = LType.BR;
		} else if (StringUtils.isNotEmpty(type)) {
			if (type.contains("indent")) {
				// this tag is specifically for indenting so ensure there is an indent
				numIndents = numIndents+1;
				writer.write(StringUtils.repeat(indent_html, numIndents));
				ltype = LType.INDENT;
			} else if (type.contains("br")) {
				writer.write(HTML.BR);
				ltype = LType.BR;
			} else {
				ltype = LType.IGNORE;
				log.debug("Unknown <l> tag type:"+type);
			}
		} else if (TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_SID, attrs)) {
			writer.write(StringUtils.repeat(indent_html, numIndents));
			ltype = LType.IGNORE;
		} else {
			//simple <l>
			writer.write(StringUtils.repeat(indent_html, numIndents));
			ltype = LType.END_BR;
		}
		stack.push(ltype);
	}

	/* (non-Javadoc)
	 * @see net.bible.service.format.osistohtml.Handler#end()
	 */
	@Override
	public void end() {
		LType type = stack.pop();
		if (LType.END_BR.equals(type)) {
			writer.write(HTML.BR);
		}
	}
}
