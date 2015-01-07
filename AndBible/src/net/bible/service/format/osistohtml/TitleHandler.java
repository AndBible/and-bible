package net.bible.service.format.osistohtml;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.VerseInfo;

import org.apache.commons.lang.StringUtils;
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
public class TitleHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	private VerseInfo verseInfo;
	
	private OsisToHtmlParameters parameters;
	
	private boolean isShowTitle;
	
	private boolean isMoveBeforeVerse;
	private static final String PREVERSE = "preverse"; // the full string is 'x-preverse' but we just check for contains for extra tolerance
	
	@SuppressWarnings("unused")
	private static final Logger log = new Logger("TitleHandler");

	public TitleHandler(OsisToHtmlParameters parameters, VerseInfo verseInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.verseInfo = verseInfo;
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_TITLE;
    }

	@Override
	public void start(Attributes attrs) {
		//JSword adds the chapter no at the top but hide this because the chapter is in the And Bible header
		boolean addedByJSword = attrs.getLength()==1 && OSISUtil.GENERATED_CONTENT.equals(attrs.getValue(OSISUtil.OSIS_ATTR_TYPE));
		// otherwise show if user wants Titles or the title is canonical
		isShowTitle = !addedByJSword && 
							(parameters.isShowTitles() || 
							 "true".equalsIgnoreCase(attrs.getValue(OSISUtil.OSIS_ATTR_CANONICAL)));
		
		if (isShowTitle) {
			// ESV has subType butNETtext has lower case subtype so concatenate both and search with contains() 
			String subtype = attrs.getValue(OSISUtil.OSIS_ATTR_SUBTYPE)+attrs.getValue(OSISUtil.OSIS_ATTR_SUBTYPE.toLowerCase());
			isMoveBeforeVerse = StringUtils.containsIgnoreCase(subtype, PREVERSE) || (!verseInfo.isTextSinceVerse && verseInfo.currentVerseNo>0);
			if (isMoveBeforeVerse) {
				// section Titles normally come before a verse, so overwrite the, already written verse, which is rewritten on writer.finishedInserting
				writer.beginInsertAt(verseInfo.positionToInsertBeforeVerse);
			}
			
			// get title type from level
			String titleClass = "heading"+TagHandlerHelper.getAttribute(OSISUtil.OSIS_ATTR_LEVEL, attrs, "1");
			
			writer.write("<h1 class='"+titleClass+"'>");
		} else {
			writer.setDontWrite(true);
		}
	}

	@Override
	public void end() {
		if (isShowTitle) {
			writer.write("</h1>");
			if (isMoveBeforeVerse) {
				// move positionToInsertBeforeVerse forward to after this title otherwise any subtitle will be above the title
				verseInfo.positionToInsertBeforeVerse = writer.getPosition();
				writer.finishInserting();
			}
		} else {
			writer.setDontWrite(false);
		}
	}
}
