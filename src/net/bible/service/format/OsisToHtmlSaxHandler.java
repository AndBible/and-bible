package net.bible.service.format;


import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.bible.service.format.Note.NoteType;
import net.bible.service.sword.Logger;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.versification.OSISNames;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
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
 * @author denha1m
 *
 */
public class OsisToHtmlSaxHandler extends DefaultHandler {
    
    // properties
    private boolean isLeftToRight = true;
    private boolean isShowHeadings = true;
    private boolean isShowVerseNumbers = true;
    private boolean isShowNotes = false;
    
    // internal logic
    private boolean isDelayVerse = false;
    private boolean isCurrentVerseNoWritten = false;
    private int currentVerseNo;
    private int noteCount = 0;

    // debugging
    private boolean isDebugMode = false;

    private boolean isWriteContent = true;
    private boolean isWriteNote = false;
    
    private Writer writer;
    
    //todo temporarily use a string but later switch to Map<int,String> of verse->note
    private List<Note> notesList = new ArrayList<Note>();
    private String currentNoteRef;
    private StringBuffer currentNote = new StringBuffer();
    private String currentRefOsisRef;

    private static final String NBSP = "&#160;";
    
    private static final Logger log = new Logger("OsisToHtmlSaxHandler");
    
    public OsisToHtmlSaxHandler() {
        this(null);
    }
    public OsisToHtmlSaxHandler(Writer theWriter) {
        writer = theWriter == null ? new StringWriter() : theWriter;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    /* @Override */
    public String toString() {
        return writer.toString();
    }

    @Override
    public void startDocument () throws SAXException
    {
    	log.debug("Show verses:"+isShowVerseNumbers+" notes:"+isShowNotes);
        write("<html dir='"+getDirection()+"'><head><link href='file:///android_asset/style.css' rel='stylesheet' type='text/css'/><meta charset='utf-8'/></head><body>");
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
    @Override
    public void endDocument () throws SAXException
    {
        write("</body></html>");
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

		if (name.equals("title") && this.isShowHeadings) {
			isDelayVerse = true;
			write("<h1>");
		} else if (name.equals("verse")) {
			if (isShowVerseNumbers) {
				isCurrentVerseNoWritten = false;
				currentVerseNo = osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID));
			}
		} else if (name.equals("note")) {
			String noteRef = getNoteRef(attrs);
			if (isShowNotes) {
				write("<span class='noteRef'>" + noteRef + "</span> ");
			}

			// prepare to fetch the actual note into the notes repo
			currentNoteRef = noteRef;
			isWriteNote = true;
			isWriteContent = false;
		} else if (name.equals("reference") && isWriteNote) {
			// don't need to do anything until closing reference tag except..
			// delete separators like ';' that sometimes occur between reference tags
			currentNote.delete(0, currentNote.length());
			// store the osisRef attribute for use with the note
			this.currentRefOsisRef = attrs.getValue("osisRef");
		} else if (name.equals("lb")) {
			write("<br />");
		} else if (name.equals("l")) {
			// Refer to Gen 3:14 in ESV for example use of type=x-indent
			String type = attrs.getValue("type");
			if (StringUtils.isNotEmpty(type) && type.contains("indent")) {
				write(NBSP+NBSP);
			} else {
				write("<br />");
			}
		} else if (name.equals("p")) {
			write("<p />");
		} else if (name.equals("q")) {
			// quotation, this could be beginning or end of quotation because it is an empty tag
			write("&quot;");
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

		if (name.equals("title") && this.isShowHeadings) {
			write("</h1>");
			isDelayVerse = false;
		} else if (name.equals("verse")) {
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
		} else if (name.equals("reference") && isWriteNote) {
			if (isShowNotes) {
				Note note = new Note(currentVerseNo, currentNoteRef, currentNote.toString(), NoteType.TYPE_REFERENCE, currentRefOsisRef);
				notesList.add(note);
				// and clear the buffer
				currentNote.delete(0, currentNote.length());
				currentRefOsisRef = null;
			}
		} else if (name.equals("l")) {
		} else if (name.equals("q")) {
			// end quotation, but <q /> tag is a marker and contains no content so <q /> will appear at beginning and end of speech
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
    	if (isShowVerseNumbers && !isDelayVerse && !isCurrentVerseNoWritten) {
    		write("<span class='verse'>"+currentVerseNo+"</span>"+NBSP);
    		isCurrentVerseNoWritten = true;
    	}
    }
	
    /** return verse from osis id of format book.chap.verse
     * 
     * @param ososID osis Id
     * @return verse number
     */
    private int osisIdToVerseNum(String ososID) {
       /* You have to use "\\.", the first backslash is interpreted as an escape by the
        Java compiler, so you have to use two to get a String that contains one
        backslash and a dot, which is what you want the regexp engine to see.*/
        String[] parts = ososID.split("\\.");
        if (parts.length>1) {
            String verse =  parts[parts.length-1];
            return Integer.valueOf(verse);
        }
        return 0;
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

    private String getName(String eName, String qName) {
        if (eName!=null && eName.length()>0) {
            return eName;
        } else {
            return qName; // not namespace-aware
        }
    }
    private void write(String s) throws SAXException
    {
      try {
        writer.write(s);
      } catch (IOException e) {
        throw new SAXException("I/O error", e);
      }
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

    /** check the value of the specified attribute and return true if same as checkvalue
     * 
     * @param attrs
     * @param attrName
     * @param checkValue
     * @return
     */
    private boolean isAttrValue(Attributes attrs, String attrName, String checkValue) {
    	String value = attrs.getValue(attrName);
    	return checkValue.equals(value);
    }
    
    private void debug(String name, Attributes attrs, boolean isStartTag) throws SAXException {
	    if (isDebugMode) {
	        write("*"+name);
	        if (attrs != null) {
	          for (int i = 0; i < attrs.getLength(); i++) {
	            String aName = attrs.getLocalName(i); // Attr name
	            if ("".equals(aName)) aName = attrs.getQName(i);
	            write(" ");
	            write(aName+"=\""+attrs.getValue(i)+"\"");
	          }
	        }
	        write("*");
	    }
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
	public void setShowNotes(boolean isShowNotes) {
		this.isShowNotes = isShowNotes;
	}
	public void setDebugMode(boolean isDebugMode) {
		this.isDebugMode = isDebugMode;
	}
	public List<Note> getNotesList() {
		return notesList;
	}
}

