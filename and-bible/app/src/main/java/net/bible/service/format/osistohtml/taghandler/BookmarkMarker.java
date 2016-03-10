package net.bible.service.format.osistohtml.taghandler;

import java.util.HashSet;
import java.util.Set;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.passage.Verse;
import org.xml.sax.Attributes;

/** Display an img if the current verse has MyNote
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class BookmarkMarker implements OsisTagHandler {

	private Set<Integer> bookmarkedVerses= new HashSet<Integer>();
	
	private OsisToHtmlParameters parameters;
	
	private VerseInfo verseInfo;
	
	private HtmlTextWriter writer;
	
	private boolean bookmarkOpenTagWritten = false;
	
	@SuppressWarnings("unused")
	private static final Logger log = new Logger("BookmarkMarker");

	public BookmarkMarker(OsisToHtmlParameters parameters, VerseInfo verseInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.verseInfo = verseInfo;
		this.writer = writer;
		
		// create hashset of verses to optimise verse note lookup
		bookmarkedVerses.clear();
		if (parameters.getVersesWithBookmarks()!=null) {
			for (Verse verse : parameters.getVersesWithBookmarks()) {
				bookmarkedVerses.add(verse.getVerse());
			}
		}
	}
	
	
	public String getTagName() {
        return "";
    }

	/** just after verse start tag
	 */
	public void start(Attributes attrs) {
		if (bookmarkedVerses!=null && parameters.isShowBookmarks()) {
			if (bookmarkedVerses.contains(verseInfo.currentVerseNo)) {
				writer.write("<img src='file:///android_asset/images/GoldStar16x16.png' class='myNoteImg'/>");
//				writer.write("<span class='bookmark'>");
				bookmarkOpenTagWritten = true;
			}
		}
	}

	public void end() {
		if (bookmarkOpenTagWritten) {
//			writer.write("</span>");
			bookmarkOpenTagWritten = false;
		}
	}
}
