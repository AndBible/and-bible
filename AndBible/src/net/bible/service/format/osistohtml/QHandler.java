package net.bible.service.format.osistohtml;

import java.util.Stack;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** This can either signify a quote or Red Letter.  Red letter is not implemented in milestone form because it maps onto opening and closing tags around text.
 * Example from ESV 
 * 		But he answered them, <q marker="" who="Jesus"><q level="1" marker="�" sID="40024002.1"/>You see all these
 * Example from KJV
 * 		said ... unto them, <q who="Jesus">...See ye
 * 
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class QHandler {

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	// quotes can be embedded so maintain a stack of info about each quote to be used when closing quote
	private Stack<QuoteInfo> stack = new Stack<QuoteInfo>();
	
	enum QType {quote, redLetter};
	private static final String MARKER = "marker";
	private static final String HTML_QUOTE_ENTITY = "&quot;";

	public QHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return "q";
    }

	public void start(Attributes attrs) {
		QuoteInfo quoteInfo = new QuoteInfo();

		String who = attrs.getValue(OSISUtil.ATTRIBUTE_Q_WHO);
		boolean isWho = who!=null;

		quoteInfo.isMilestone = TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_SID, attrs) || TagHandlerHelper.isAttr(OSISUtil.OSIS_ATTR_EID, attrs); 

		// Jesus -> no default quote
		quoteInfo.marker = TagHandlerHelper.getAttribute(MARKER, attrs, isWho ? "" : HTML_QUOTE_ENTITY);

		quoteInfo.isRedLetter = parameters.isRedLetter() && "Jesus".equals(who);

		// apply the above logic
		writer.write(quoteInfo.marker);
		if (quoteInfo.isRedLetter) {
			writer.write("<span class='redLetter'>");
		}
		
		// and save the info for the closing tag
		stack.push(quoteInfo);
	}

	public void end() {
		QuoteInfo quoteInfo = stack.pop();
		
		// Jesus words
		if (quoteInfo.isRedLetter) {
			writer.write("</span>");
		}
		
		// milestone opening and closing tags are doubled up so ensure not double quotes
		if (!quoteInfo.isMilestone) {
			writer.write(quoteInfo.marker);
		}
	}
	
	private static class QuoteInfo {
		private String marker = HTML_QUOTE_ENTITY;
		private boolean isMilestone;
		private boolean isRedLetter;
	}
}
