package net.bible.service.format.osistohtml;

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
public class QHandler implements TagHandler {

	enum QType {QUOTE, RED_LETTER, IGNORE};

	public QHandler() {
	}
		
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_Q;
    }

	@Override
	public void start(TagHandlerData tagHandlerData, CommonHandlerData commonHandlerData) {
		QHandlerData qHandlerData = (QHandlerData)tagHandlerData;
		switch (qHandlerData.qType) {
		case QUOTE:
			// one null quote tag occurs at beginning and end so write a quote in start only 
			commonHandlerData.getWriter().write("&quot;");
			break;
		case RED_LETTER:
			commonHandlerData.getWriter().write("<span class='redLetter'>");
			break;
		case IGNORE:
			break;
		}
	}

	@Override
	public void end(TagHandlerData tagHandlerData, CommonHandlerData commonHandlerData) {
		QHandlerData qHandlerData = (QHandlerData)tagHandlerData;
		switch (qHandlerData.qType) {
		case QUOTE:
			// one null quote tag occurs at beginning and end so write a quote in start only
			break;
		case RED_LETTER:
			commonHandlerData.getWriter().write("</span>");
			break;
		}
	}
	
	@Override
	public TagHandlerData createData(Attributes attrs, CommonHandlerData commonHandlerData) {
		QHandlerData data = new QHandlerData();
		String who = attrs.getValue(OSISUtil.ATTRIBUTE_Q_WHO);
		if (StringUtils.isEmpty(who)) {
			data.qType = QType.QUOTE;
		} else {
			// who is set so presume it is Jesus
			if (commonHandlerData.getParameters().isRedLetter()) {
				// esv uses q for red-letter and for quote mark
				data.qType = QType.RED_LETTER;
			} else {
				data.qType = QType.IGNORE;
			}
		}

		return data;
	}

	/** data prepared once from tag context and passed to start & end tags
	 */
	private static class QHandlerData implements TagHandlerData {

		private QType qType;
		
		@Override
		public String getTagName() {
			return OSISUtil.OSIS_ELEMENT_Q;
		}
	}
}
