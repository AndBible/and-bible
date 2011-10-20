package net.bible.service.format.osistohtml;

import java.util.HashMap;
import java.util.Map;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

/** Display an img if the current verse has MyNote
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class MyNoteMarker {

	private Map<Integer, Key> myNoteVerseMap= new HashMap<Integer, Key>();
	
	private OsisToHtmlParameters parameters;
	
	private VerseInfo verseInfo; 
	
	private HtmlTextWriter writer;
	
	@SuppressWarnings("unused")
	private static final Logger log = new Logger("VerseHandler");

	public MyNoteMarker(OsisToHtmlParameters parameters, VerseInfo verseInfo, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.verseInfo = verseInfo;
		this.writer = writer;
		
		// create hashmap of verses to optimise verse note lookup
		myNoteVerseMap.clear();
		if (parameters.getKeysWithNotes()!=null) {
			for (Key key : parameters.getKeysWithNotes()) {
				Verse verse = KeyUtil.getVerse(key);
				myNoteVerseMap.put(verse.getVerse(), key);
			}
		}
	}
	
	
	public String getTagName() {
        return "";
    }

	/** just after verse start tag
	 */
	public void start() {
		if (myNoteVerseMap!=null) {
			if (myNoteVerseMap.containsKey(verseInfo.currentVerseNo)) {
				writer.write("<img src='file:///android_asset/pencil16x16.png' class='myNoteImg'/>");
			}
		}
	}

	public void end() {
	}
}
