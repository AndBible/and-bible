package net.bible.service.format.osistohtml;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bible.service.common.Constants;
import net.bible.service.format.Note;
import net.bible.service.format.Note.NoteType;
import net.bible.service.sword.Logger;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.RestrictionType;
import org.xml.sax.Attributes;

/**
 * Convert OSIS tags into html tags
 * 
 *  Example OSIS tags from KJV Ps 119 v1 showing title, w, note
<title canonical="true" subType="x-preverse" type="section">
	<foreign n="?">ALEPH.</foreign>
</title>
<w lemma="strong:H0835">Blessed</w> <transChange type="added">are</transChange> <w lemma="strong:H08549">the undefiled</w>
...  <w lemma="strong:H01980" morph="strongMorph:TH8802">who walk</w> 
... <w lemma="strong:H03068">of the <seg><divineName>Lord</divineName></seg></w>.
<note type="study">undefiled: or, perfect, or, sincere</note>

Example of notes cross references from ESV 
In the <note n="a" osisID="Gen.1.1!crossReference.a" osisRef="Gen.1.1" type="crossReference"><reference osisRef="Job.38.4-Job.38.7">Job 38:4-7</reference>; <reference osisRef="Ps.33.6">Ps. 33:6</reference>; <reference osisRef="Ps.136.5">136:5</reference>; <reference osisRef="Isa.42.5">Isa. 42:5</reference>; <reference osisRef="Isa.45.18">45:18</reference>; <reference osisRef="John.1.1-John.1.3">John 1:1-3</reference>; <reference osisRef="Acts.14.15">Acts 14:15</reference>; <reference osisRef="Acts.17.24">17:24</reference>; <reference osisRef="Col.1.16-Col.1.17">Col. 1:16, 17</reference>; <reference osisRef="Heb.1.10">Heb. 1:10</reference>; <reference osisRef="Heb.11.3">11:3</reference>; <reference osisRef="Rev.4.11">Rev. 4:11</reference></note>beginning
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class NoteAndReferenceHandler {

    private OsisToHtmlParameters parameters;

    private int noteCount = 0;

    //todo temporarily use a string but later switch to Map<int,String> of verse->note
    private List<Note> notesList = new ArrayList<Note>();
    private boolean isInNote = false;
    private String currentNoteRef;
    private String currentRefOsisRef;

    private HtmlTextWriter writer;
    
    private static final Logger log = new Logger("NoteAndReferenceHandler");
    
    public NoteAndReferenceHandler(OsisToHtmlParameters osisToHtmlParameters, HtmlTextWriter theWriter) {
        this.parameters = osisToHtmlParameters;
        this.writer = theWriter;
    }

    public void startNote(Attributes attrs) {
		isInNote = true;
		currentNoteRef = getNoteRef(attrs);
		writeNoteRef(currentNoteRef);

		// prepare to fetch the actual note into the notes repo
		writer.writeToTempStore();
    }

    public void startReference(Attributes attrs) {
		// don't need to do anything until closing reference tag except..
		// delete separators like ';' that sometimes occur between reference tags
		writer.clearTempStore();
		writer.writeToTempStore();
		// store the osisRef attribute for use with the note
		this.currentRefOsisRef = attrs.getValue(OSISUtil.OSIS_ATTR_REF);
	}
    
    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
    public void endNote(int currentVerseNo) {
		String noteText = writer.getTempStoreString();
		if (noteText.length()>0) {
			if (!StringUtils.containsOnly(noteText, "[];().,")) {
				Note note = new Note(currentVerseNo, currentNoteRef, noteText, NoteType.TYPE_GENERAL, null);
				notesList.add(note);
			}
			// and clear the buffer
			writer.clearTempStore();
		}
		isInNote = false;
		writer.finishWritingToTempStore();
    }
    
    public void endReference(int currentVerseNo) {
			if (parameters.isBibleStyleNotesAndRefs()) {
				// a few modules like HunUj have refs in the text but not surrounded by a Note tag (like esv) so need to add  Note here
				if (!isInNote) {
					currentNoteRef = createNoteRef();
					writeNoteRef(currentNoteRef);
				}
				Note note = new Note(currentVerseNo, currentNoteRef, writer.getTempStoreString(), NoteType.TYPE_REFERENCE, currentRefOsisRef);
				notesList.add(note);
				// and clear the buffer
				writer.clearTempStore();
				currentRefOsisRef = null;
			} else {
				writer.write(getReferenceTag(currentRefOsisRef, writer.getTempStoreString()));
				writer.clearTempStore();
			}
			writer.finishWritingToTempStore();
	}
    
    /** either use the 'n' attribute for the note ref or just get the next character in a list a-z
     * 
     * @return a single char to use as a note ref
     */
    private String getNoteRef(Attributes attrs) {
    	// if the ref is specified as an attribute then use that
    	String noteRef = attrs.getValue("n");
		if (StringUtils.isEmpty(noteRef)) {
			noteRef = createNoteRef();
		}
    	return noteRef;
    }
    /** either use the character passed in or get the next character in a list a-z
     * 
     * @return a single char to use as a note ref
     */
	private String createNoteRef() {
		// else just get the next char
    	int inta = (int)'a';
    	char nextNoteChar = (char)(inta+(noteCount++ % 26));
    	return String.valueOf(nextNoteChar);
	}

	/** write noteref html to outputstream
	 */
	private void writeNoteRef(String noteRef) {
		if (parameters.isShowNotes()) {
			writer.write("<span class='noteRef'>" + noteRef + "</span> ");
		}
	}

    /** create a link tag from an OSISref and the content of the tag
     */
    private String getReferenceTag(String reference, String content) {
    	
    	StringBuilder result = new StringBuilder();
    	try {
    		
    		//JSword does not know the basis (default book) so prepend it if it looks like JSword failed to work it out
    		//We only need to worry about the first ref because JSword uses the first ref as the basis for the subsequent refs
    		// if content starts with a number and is not followed directly by an alpha char e.g. 1Sa
    		if (reference==null && content!=null && content.length()>0 && StringUtils.isNumeric(content.subSequence(0,1)) &&
   				(content.length()<2 || !StringUtils.isAlphaSpace(content.subSequence(1,2)))) {
    			
        		// maybe should use VerseRangeFactory.fromstring(orig, basis)
    			// this check for a colon to see if the first ref is verse:chap is not perfect but it will do until JSword adds a fix
    			int firstColonPos = content.indexOf(":");
    			boolean isVerseAndChapter = firstColonPos>0 && firstColonPos<4;
    			if (isVerseAndChapter) {
        			reference = parameters.getBasisRef().getBook().getOSIS()+" "+content;
    			} else {
    				reference = parameters.getBasisRef().getBook().getOSIS()+" "+parameters.getBasisRef().getChapter()+":"+content;
    			}
    			log.debug("Patched reference:"+reference);
    		} else if (reference==null) {
    			reference = content;
    		}
    		
	        Passage ref = (Passage) PassageKeyFactory.instance().getKey(reference);
	        boolean isSingleVerse = ref.countVerses()==1;
	        boolean isSimpleContent = content.length()<3 && content.length()>0;
	        Iterator<Key> it = ref.rangeIterator(RestrictionType.CHAPTER);
	        
	        if (isSingleVerse && isSimpleContent) {
		        // simple verse no e.g. 1 or 2 preceding the actual verse in TSK
				result.append("<a href='").append(Constants.BIBLE_PROTOCOL).append(":").append(it.next().getOsisRef()).append("'>");
				result.append(content);
				result.append("</a>");
	        } else {
	        	// multiple complex references
	        	boolean isFirst = true;
				while (it.hasNext()) {
					Key key = it.next();
					if (!isFirst) {
						result.append(" ");
					}
					result.append("<a href='").append(Constants.BIBLE_PROTOCOL).append(":").append(key.iterator().next().getOsisRef()).append("'>");
					result.append(key);
					result.append("</a>&nbsp; ");
					isFirst = false;
				}
	        }
    	} catch (Exception e) {
    		log.error("Error parsing OSIS reference:"+reference, e);
    		// just return the content with no html markup
    		result.append(content);
    	}
    	return result.toString();
    }
    
	public List<Note> getNotesList() {
		return notesList;
	}
}

