package net.bible.service.format;

import net.bible.service.common.Logger;

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

    public boolean isNavigable() {
        return noteType.equals(NoteType.TYPE_REFERENCE);
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

    public String getOsisRef() {
        return osisRef;
    }

    public NoteType getNoteType() {
        return noteType;
    }
}
