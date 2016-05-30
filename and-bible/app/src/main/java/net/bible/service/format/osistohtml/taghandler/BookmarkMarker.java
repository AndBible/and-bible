package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.passage.Verse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Display an img if the current verse has MyNote
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class BookmarkMarker {

	private Set<Integer> bookmarkedVerses= new HashSet<>();
	
	private OsisToHtmlParameters parameters;
	
	private VerseInfo verseInfo;
	
	public BookmarkMarker(OsisToHtmlParameters parameters, VerseInfo verseInfo) {
		this.parameters = parameters;
		this.verseInfo = verseInfo;

		// create hashset of verses to optimise verse note lookup
		bookmarkedVerses.clear();
		if (parameters.getVersesWithBookmarks()!=null) {
			for (Verse verse : parameters.getVersesWithBookmarks()) {
				bookmarkedVerses.add(verse.getVerse());
			}
		}
	}
	
	/** Get any bookmark classes for current verse
	 */
	public List<String> getBookmarkClasses() {
		if (bookmarkedVerses!=null && parameters.isShowBookmarks()) {
			if (bookmarkedVerses.contains(verseInfo.currentVerseNo)) {
				return Arrays.asList(parameters.getDefaultBookmarkStyle().name());
//				writer.write("<img src='file:///android_asset/images/GoldStar16x16.png' class='myNoteImg'/>");
			}
		}
		return Collections.emptyList();
	}
}
