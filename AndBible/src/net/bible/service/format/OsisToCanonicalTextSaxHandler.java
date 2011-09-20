package net.bible.service.format;


import java.util.Stack;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.TagHandlerHelper;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;
/**
 * Convert OSIS input into Canonical text (used when creating search index)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class OsisToCanonicalTextSaxHandler extends OsisSaxHandler {
    
    private int currentVerseNo;

    // debugging
    private boolean isDebugMode = false;

    private Stack<CONTENT_STATE> writeContentStack = new Stack<CONTENT_STATE>(); 
	private enum CONTENT_STATE {WRITE, IGNORE};
    
    private static final Logger log = new Logger("OsisToCanonicalTextSaxHandler");
    
    public OsisToCanonicalTextSaxHandler() {
        super();
    }

    @Override
    public void startDocument () {
    	reset();
    	// default mode is to write
    	writeContentStack.push(CONTENT_STATE.WRITE);
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
    @Override
    public void endDocument() {
    	// pop initial value
    	writeContentStack.pop();
    	assert(writeContentStack.isEmpty());
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
    {
		String name = getName(sName, qName); // element name

		debug(name, attrs, true);

		// if encountering either a verse tag or if the current tag is marked as being canonical then turn on writing
		if (isAttrValue(attrs, "canonical", "true")) {
			writeContentStack.push(CONTENT_STATE.WRITE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			if (attrs!=null) {
				currentVerseNo = TagHandlerHelper.osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID));
			}
			writeContentStack.push(CONTENT_STATE.WRITE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);

		} else if (	name.equals(OSISUtil.OSIS_ELEMENT_L) ||
					name.equals(OSISUtil.OSIS_ELEMENT_LB) ||
					name.equals(OSISUtil.OSIS_ELEMENT_P) ) {
			// these occur in Psalms to separate different paragraphs.  
			// A space is needed for TTS not to be confused by punctuation with a missing space like 'toward us,and the'
			write(" ");
			//if writing then continue.  Also if ignoring then continue
			writeContentStack.push(writeContentStack.peek());
		} else {
			// unknown tags rely on parent tag to determine if content is canonical e.g. the italic tag in the middle of canonical text
			writeContentStack.push(writeContentStack.peek());
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
    {
		String name = getName(sName, qName);
		debug(name, null, false);
		if (name.equals(OSISUtil.OSIS_ELEMENT_VERSE)) {
			// A space is needed to separate one verse from the next, otherwise the 2 verses butt up against each other
			// which looks bad and confuses TTS
			write(" ");
		}
		
		// now this tag has ended pop the write/ignore state for the parent tag
		writeContentStack.pop();
	}
    
    /*
     * Handle characters encountered in tags
    */
    @Override
    public void characters (char buf[], int offset, int len) {
        if (CONTENT_STATE.WRITE.equals(writeContentStack.peek())) {
        	String s = new String(buf, offset, len);

        	// NetText often uses single quote where esv uses double quote and TTS says open single quote e.g. Matt 4
        	// so replace all single quotes with double quotes but only if they are used for quoting text as in e.g. Ps 117
        	// it is tricky to distinguish single quotes from apostrophes and this won't work all the time
        	if (s.contains(" \'")) {
        		s = s.replace("\'", "\"");
        	}
        	// Finney Gospel Sermons contains to many '--'s which are pronounced as hyphen hyphen
        	if (s.contains(" --")) {
        		s = s.replace(" --", ";");
        	}
        	
            write(s);
        }
    }
    
    private void replace(char buf[], int offset, int len, char find, char replacement) {
        for (int i=offset; i<len; i++) {
        	if (buf[i]=='\'') {
        		buf[i]='\"';
        	}
        }
    }
}

