package net.bible.service.format;


import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.bible.service.common.Constants;
import net.bible.service.format.Note.NoteType;
import net.bible.service.sword.Logger;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
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
public class OsisToHtmlSaxHandler extends OsisSaxHandler {

    // properties
    private boolean isLeftToRight = true;
    private boolean isShowHeadings = true;
    private boolean isShowVerseNumbers = false;
    private boolean isShowNotes = false;
    private String extraStylesheet;
    
    // internal logic
    private boolean isDelayVerse = false;
    private boolean isCurrentVerseNoWritten = false;
    private int currentVerseNo;
    private int noteCount = 0;

    private boolean isWriteContent = true;
    private boolean isWriteNote = false;
    private boolean isStartedValidStrongsTag = false;
    
    //todo temporarily use a string but later switch to Map<int,String> of verse->note
    private List<Note> notesList = new ArrayList<Note>();
    private String currentNoteRef;
    private StringBuffer currentNote = new StringBuffer();
    private String currentRefOsisRef;

    private static final String NBSP = "&#160;";
    
    private static final Logger log = new Logger("OsisToHtmlSaxHandler");
    
    public OsisToHtmlSaxHandler() {
        super(null);
    }
    public OsisToHtmlSaxHandler(Writer theWriter) {
        super(theWriter);
    }

    @Override
    public void startDocument () throws SAXException
    {
    	log.debug("Show verses:"+isShowVerseNumbers+" notes:"+isShowNotes);
    	String jsTag = "\n<script type='text/javascript' src='file:///android_asset/script.js'></script>\n";
    	String styleSheetTag = "<link href='file:///android_asset/style.css' rel='stylesheet' type='text/css'/>";
    	String extraStyleSheetTag = "";
    	if (extraStylesheet!=null) {
    		extraStyleSheetTag = "<link href='file:///android_asset/"+extraStylesheet+"' rel='stylesheet' type='text/css'/>";
    	}
        write(	"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"> "+
        		"<html xmlns='http://www.w3.org/1999/xhtml' dir='"+getDirection()+"'><head>"+
        		styleSheetTag+extraStyleSheetTag+jsTag+
        		"<meta charset='utf-8'/>"+
        		"</head>"+
        		"<body onscroll='jsonscroll()' onload='jsonload()' >");
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
    @Override
    public void endDocument () throws SAXException
    {
    	// add padding at bottom to allow last verse to scroll to top of page and become current verse
        write("<br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /></body></html>");
    }

    /*
     * Called when the starting of the Element is reached. For Example if we have Tag
     * called <Title> ... </Title>, then this method is called when <Title> tag is
     * Encountered while parsing the Current XML File. The AttributeList Parameter has
     * the list of all Attributes declared for the Current Element in the XML File.
    */
    @Override
    public void startElement(String namespaceURI,
            String sName, // simple name
            String qName, // qualified name
            Attributes attrs)
    throws SAXException
    {
		String name = getName(sName, qName); // element name

		debug(name, attrs, true);

		if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE) && this.isShowHeadings) {
			isDelayVerse = true;
			write("<h1>");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			if (attrs!=null) {
				currentVerseNo = osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID));
			}
			isCurrentVerseNoWritten = false;
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			String noteRef = getNoteRef(attrs);
			if (isShowNotes) {
				write("<span class='noteRef'>" + noteRef + "</span> ");
			}

			// prepare to fetch the actual note into the notes repo
			currentNoteRef = noteRef;
			isWriteNote = true;
			isWriteContent = false;
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE) && isWriteNote) {
			// don't need to do anything until closing reference tag except..
			// delete separators like ';' that sometimes occur between reference tags
			currentNote.delete(0, currentNote.length());
			// store the osisRef attribute for use with the note
			this.currentRefOsisRef = attrs.getValue(OSISUtil.OSIS_ATTR_REF);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_LB)) {
			write("<br />");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_L)) {
			// Refer to Gen 3:14 in ESV for example use of type=x-indent
			String type = attrs.getValue(OSISUtil.OSIS_ATTR_TYPE);
			if (StringUtils.isNotEmpty(type) && type.contains("indent")) {
				write(NBSP+NBSP);
			} else {
				write("<br />");
			}
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_P)) {
			write("<p />");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_Q) && !isAttr(OSISUtil.ATTRIBUTE_Q_WHO, attrs)) {
			// ensure 'who' attribute does not exist because esv uses q for red-letter and for quote mark
			// quotation, this could be beginning or end of quotation because it is an empty tag
			write("&quot;");
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_W) && isAttr(OSISUtil.ATTRIBUTE_W_LEMMA, attrs)) {
			// Strongs reference
			// example of strongs refs: <w lemma="strong:H0430">God</w> <w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w>
			String lemma = attrs.getValue(OSISUtil.ATTRIBUTE_W_LEMMA);
			if (lemma.startsWith(OSISUtil.LEMMA_STRONGS)) {
				
				String strongsUrl = getStrongsUrl(lemma);
	    		log.debug("Strongs url:"+strongsUrl);
				write("<a href='"+strongsUrl+"'/>");
				isStartedValidStrongsTag = true;
			}
		}
	}
    
    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
    @Override
    public void endElement(String namespaceURI,
            String sName, // simple name
            String qName  // qualified name
            )
    throws SAXException
    {
		String name = getName(sName, qName);

		debug(name, null, false);

		if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE) && this.isShowHeadings) {
			write("</h1>");
			isDelayVerse = false;
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
		} else if (name.equals("note")) {
			String noteText = currentNote.toString();
			if (noteText.length()>0) {
				isWriteNote = false;
				if (!StringUtils.containsOnly(noteText, "[];().,")) {
					Note note = new Note(currentVerseNo, currentNoteRef, noteText, NoteType.TYPE_GENERAL, null);
					notesList.add(note);
				}
				// and clear the buffer
				currentNote.delete(0, currentNote.length());
			}
			isWriteContent = true;
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE) && isWriteNote) {
			Note note = new Note(currentVerseNo, currentNoteRef, currentNote.toString(), NoteType.TYPE_REFERENCE, currentRefOsisRef);
			notesList.add(note);
			// and clear the buffer
			currentNote.delete(0, currentNote.length());
			currentRefOsisRef = null;
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_L)) {
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_Q)) {
			// end quotation, but <q /> tag is a marker and contains no content so <q /> will appear at beginning and end of speech
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_W) && isStartedValidStrongsTag) {
			write("</a>");
			isStartedValidStrongsTag = false;
		}
	}
    
    /*
     * While Parsing the XML file, if extra characters like space or enter Character
     * are encountered then this method is called. If you don't want to do anything
     * special with these characters, then you can normally leave this method blank.
    */
    @Override
    public void characters (char buf [], int offset, int len) throws SAXException
    {
    	writeVerse();
        if (isWriteContent) {
            String s = new String(buf, offset, len);
            write(s);
        }
        if (isWriteNote) {
        	currentNote.append(buf, offset, len); 
        }
    }

	private void writeVerse() throws SAXException {
    	if (!isDelayVerse && !isCurrentVerseNoWritten) {
    		// the id is used to 'jump to' the verse using javascript so always need the verse tag with an id
    		if (isShowVerseNumbers) {
    			write("<span class='verse' id='"+currentVerseNo+"'>"+currentVerseNo+"</span>"+NBSP);
    		} else {
    			// we realy want an empty span but that is illegal and causes problems such as incorrect verse calculation in Psalms 
    			// so use something that will hopefully interfere as little as possible - a zero-width-space
    			write("<span class='verse' id='"+currentVerseNo+"'/>&#x200b;</span>");
    		}
    		isCurrentVerseNoWritten = true;
    	}
    }

    /*
     * In the XML File if the parser encounters a Processing Instruction which is
     * declared like this  <?ProgramName:BooksLib QUERY="author, isbn, price"?> 
     * Then this method is called where Target parameter will have
     * "ProgramName:BooksLib" and data parameter will have  QUERY="author, isbn,
     *  price". You can invoke a External Program from this Method if required. 
    */
    public void processingInstruction (String target, String data) throws SAXException
    {
    }

    public String getDirection() {
        return isLeftToRight ? "ltr" : "rtl";
    }
    
    /** either use the 'n' attribute for the note ref or just get the next character in a list a-z
     * 
     * @return a single char to use as a note ref
     */
    private String getNoteRef(Attributes attrs) {
    	// if the ref is specified as an attribute then use that
    	String noteRef = attrs.getValue("n");
    	if (StringUtils.isEmpty(noteRef)) {
    		// else just get the next char
	    	int inta = (int)'a';
	    	char nextNoteChar = (char)(inta+(noteCount++ % 26));
	    	noteRef = String.valueOf(nextNoteChar);
    	}
    	return noteRef;
    }

    /** Convert a Strongs lemma into a url
     * E.g. lemmas "strong:H0430", "strong:H0853 strong:H01254"
     * 
     * @return a single char to use as a note ref
     */
    private String getStrongsUrl(String lemma) {
    	// there may occasionally be more than on ref so split them into a list of single refs
    	String[] refList = lemma.split(" ");
    	
    	String protocol = null;
    	String url="";
    	boolean isFirstRef = true;
    	for (String ref : refList) {
    		// ignore if string doesn't start with "strong;"
    		if (ref.startsWith(OSISUtil.LEMMA_STRONGS)) {
    			// reduce ref like "strong:H0430" to "H0430"
    			ref = ref.substring(OSISUtil.LEMMA_STRONGS.length());
    			
    			if (isFirstRef || protocol == null) {
	            	// select Hebrew or Greek protocol
	    			if (ref.startsWith("H")) {
	    				protocol = Constants.HEBREW_DEF_PROTOCOL;
	    			} else if (ref.startsWith("G")) {
	       				protocol = Constants.GREEK_DEF_PROTOCOL;
	       			}
    			}

    			if (ref.length()>1) {
    				if (!isFirstRef && url.length()>0) {
    					// separate each ref with a plus
    					url += "+";
    				}
    				ref = ref.substring(1);
    				url += StringUtils.leftPad(ref, 5, "0");
    			}
    		}
    		isFirstRef = false;
    	}
    	
    	if (protocol!=null && url.length()>0) {
    		return protocol+":"+url;
    	} else {
    		return "";
    	}
    	
    }

    /** see if an attribute exists and has a value
     * 
     * @param attributeName
     * @param attrs
     * @return
     */
    private boolean isAttr(String attributeName, Attributes attrs) {
    	String attrValue = attrs.getValue(attributeName);
    	return StringUtils.isNotEmpty(attrValue);
    }

    public void setLeftToRight(boolean isLeftToRight) {
        this.isLeftToRight = isLeftToRight;
    }
	public void setShowHeadings(boolean isShowHeadings) {
		this.isShowHeadings = isShowHeadings;
	}
	public void setShowVerseNumbers(boolean isShowVerseNumbers) {
		this.isShowVerseNumbers = isShowVerseNumbers;
	}
	public void setExtraStylesheet(String extraStylesheet) {
		this.extraStylesheet = extraStylesheet;
	}
	public void setShowNotes(boolean isShowNotes) {
		this.isShowNotes = isShowNotes;
	}
	public List<Note> getNotesList() {
		return notesList;
	}
}

