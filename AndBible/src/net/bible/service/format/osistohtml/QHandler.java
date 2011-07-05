package net.bible.service.format.osistohtml;

import java.util.Stack;

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
public class QHandler {

	enum QType {quote, redLetter};

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	private Stack<QType> stack = new Stack<QType>();
	
	public QHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return "q";
    }

	public void start(Attributes attrs) {
		String who = attrs.getValue(OSISUtil.ATTRIBUTE_Q_WHO);
		if (StringUtils.isEmpty(who)) {
			// quotation, this could be beginning or end of quotation because it is an empty tag
			writer.write("&quot;");
			stack.push(QType.quote);
		} else {
			if (parameters.isRedLetter()) {
				// esv uses q for red-letter and for quote mark
				writer.write("<span class='redLetter'>");
			}
			stack.push(QType.redLetter);
		}
	}

	public void end() {
		QType type = stack.pop();
		if (QType.redLetter.equals(type) && parameters.isRedLetter()) {
			writer.write("</span>");
		}
	}
}
