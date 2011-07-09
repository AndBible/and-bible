package net.bible.service.format.osistohtml;

import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.VerseInfo;
import net.bible.service.sword.Logger;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** This can either signify a quote or Red Letter
 * Example 
 * ESV section heading 	<title subType="x-preverse" type="section">
 * ESV canonical heading<title canonical="true" subType="x-preverse" type="section">To the choirmaster. Of David,
 * WEB when formatted with JSword seems to have type="x-gen"
 * 
 * Apparently quotation marks are not supposed to appear in the KJV (https://sites.google.com/site/kjvtoday/home/Features-of-the-KJV/quotation-marks)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class TitleHandler {

	private HtmlTextWriter writer;
	
	private VerseInfo verseInfo;
	
	private OsisToHtmlParameters parameters;
	
	private boolean isHeaderWritten;
	
	private static final Logger log = new Logger("TitleHandler");

	public TitleHandler(OsisToHtmlParameters parameters, VerseInfo verseInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.verseInfo = verseInfo;
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_TITLE;
    }

	public void start(Attributes attrs) {
		log.debug("Title");
		TagHandlerHelper.printAttributes(attrs);
		//JSword adds the chapter no at the top but hide this because the chapter is in the And Bible header
		boolean addedByJSword = attrs.getLength()==1 && OSISUtil.GENERATED_CONTENT.equals(attrs.getValue(OSISUtil.OSIS_ATTR_TYPE));
		// otherwise show if user wants Titles or the title is canonical
		isHeaderWritten = !addedByJSword && 
							(parameters.isShowTitles() || 
							 "true".equalsIgnoreCase(attrs.getValue(OSISUtil.OSIS_ATTR_CANONICAL)));
		
		if (isHeaderWritten) {
			// section Titles normally come before a verse, so overwrite the, already written verse, which is rewritten on writer.finishedInserting
			writer.beginInsertAt(verseInfo.currentVersePosition);
			writer.write("<h1>");
		} else {
			writer.setDontWrite(true);
		}
	}

	public void end() {
		if (isHeaderWritten) {
			writer.write("</h1>");
			writer.finishInserting();
		} else {
			writer.setDontWrite(false);
		}
	}
}
