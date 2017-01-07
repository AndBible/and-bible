package net.bible.service.format;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.sword.SwordContentFacade;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.versification.Versification;


/** Info on a note or cross reference
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Note {

	public enum NoteType {TYPE_GENERAL, TYPE_REFERENCE}
	
	private int verseNo;
	private String noteRef;
	private String noteText;
	private NoteType noteType;
	private String osisRef;
	private Versification v11n;

	public static final String SUMMARY = "summary";
	public static final String DETAIL = "detail";
	
	private static final Logger log = new Logger("Note");
	
	public Note(int verseNo, String noteRef, String noteText, NoteType noteType, String osisRef, Versification v11n) {
		super();
		this.verseNo = verseNo;
		this.noteRef = noteRef;
		this.noteText = noteText;
		this.noteType = noteType;
		this.osisRef = osisRef;
		this.v11n = v11n;
	}

	public String getSummary() {
		return "Ref "+getNoteRef()+": "+getNoteText();
	}

	public String getDetail() {
		String retval = "";
		try {
			if (noteType.equals(NoteType.TYPE_REFERENCE)) {
				String verse = StringUtils.isNotEmpty(osisRef) ? osisRef : noteText; 
				retval = SwordContentFacade.getInstance().getPlainText(ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getCurrentDocument(), verse, 1);
				retval = CommonUtils.limitTextLength(retval);
			}
		} catch (Exception e) {
			log.error("Error getting note detail for osisRef "+osisRef, e);
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
		String ref;
		if (StringUtils.isNotEmpty(osisRef)) {
			ref = osisRef;
		} else {
			ref = noteText;
		}

		CurrentPageManager currentPageControl = ControlFactory.getInstance().getCurrentPageControl();
		currentPageControl.getCurrentBible().setKey(ref);
		currentPageControl.showBible();
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
	/**
	 * If note is reference specific then return the reference otherwise return the text within the note
	 */
	public String getNoteText() {
		String text=null;
		if (noteType.equals(NoteType.TYPE_REFERENCE)) {
			Key key = getReferenceKey();
			if (key!=null) {
				text = key.getName();
			}
		}
		// if not a reference or if reference was invalid return the notes text content
		if (text==null) {
			text = noteText;
		}
		return text;
	}
	
	private Key getReferenceKey() {
		Key key=null;
		try {
			if (noteType.equals(NoteType.TYPE_REFERENCE)) {
				String reference = StringUtils.isNotEmpty(osisRef) ? osisRef : noteText;
				key = PassageKeyFactory.instance().getValidKey(v11n, reference);
			}
		} catch (Exception e) {
			log.warn("Error getting note reference for osisRef "+osisRef, e);
		}
		return key;
	}
}
