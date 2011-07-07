package net.bible.service.format;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordApi;

import org.apache.commons.lang.StringUtils;

import android.util.Log;


/** Info on a note or cross reference
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Note {

	public enum NoteType {TYPE_GENERAL, TYPE_REFERENCE};
	
	private int verseNo;
	private String noteRef;
	private String osisRef;
	private String noteText;
	private NoteType noteType;

	public static final String SUMMARY = "summary";
	public static final String DETAIL = "detail";
	
	private static final String TAG = "Note";
	
	public Note(int verseNo, String noteRef, String noteText, NoteType noteType, String osisRef) {
		super();
		this.verseNo = verseNo;
		this.noteRef = noteRef;
		this.noteText = noteText;
		this.noteType = noteType;
		this.osisRef = osisRef;
	}

	public String getSummary() {
		return "Ref "+getNoteRef()+": "+getNoteText();
	}

	public String getDetail() {
		String retval = "";
		try {
			if (noteType.equals(NoteType.TYPE_REFERENCE)) {
				String verse = StringUtils.isNotEmpty(osisRef) ? osisRef : noteText; 
				retval = SwordApi.getInstance().getPlainText(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument(), verse, 1);
				retval = CommonUtils.limitTextLength(retval);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting search result", e);
		}
		return retval;
	}
	
	public boolean isNavigable() {
		return noteType.equals(NoteType.TYPE_REFERENCE);
	}
	
	/** Jump to the verse in the ref 
	 * if the osisRef is available then use that becsue sometimes the noteText itself misses out the book o fthe bible
	 */
	public void navigateTo() {
		String ref = "";
		if (StringUtils.isNotEmpty(osisRef)) {
			ref = osisRef;
		} else {
			ref = noteText;
		}

		CurrentPageManager.getInstance().getCurrentBible().setKey(ref);
		CurrentPageManager.getInstance().showBible();
	}

	@Override
	public String toString() {
		return noteRef+":"+noteText;
	}

	public int getVerseNo() {
		return verseNo;
	}
	public String getNoteRef() {
		return noteRef;
	}
	public String getNoteText() {
		return noteText;
	}
}
