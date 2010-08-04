package net.bible.service.format;


import java.io.Writer;
import java.util.Stack;

import net.bible.service.sword.Logger;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
/**
 * Convert OSIS input into Canonical text (used when creating search index)
 * 
 * @author denha1m
 *
 */
public class OsisToCanonicalTextSaxHandler extends OsisSaxHandler {
    
    // internal logic
    private int currentVerseNo;

    // debugging
    private boolean isDebugMode = false;

    private Stack<CONTENT_STATE> writeContentStack = new Stack<CONTENT_STATE>(); 
	private enum CONTENT_STATE {WRITE, IGNORE};
	
    
    private static final Logger log = new Logger("OsisToHtmlSaxHandler");
    
    public OsisToCanonicalTextSaxHandler() {
        super(null);
    }
    public OsisToCanonicalTextSaxHandler(Writer theWriter) {
        super(theWriter);
    }

    @Override
    public void startDocument () throws SAXException
    {
    	reset();
    	// default mode is to write
    	writeContentStack.push(CONTENT_STATE.WRITE);
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
    @Override
    public void endDocument () throws SAXException
    {
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
    throws SAXException
    {
		String name = getName(sName, qName); // element name

		debug(name, attrs, true);

		if (isAttrValue(attrs, "canonical", "true")) {
			writeContentStack.push(CONTENT_STATE.WRITE);
		} else if (name.equals("verse")) {
			if (attrs!=null) {
				currentVerseNo = osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID));
			}
			writeContentStack.push(CONTENT_STATE.WRITE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_NOTE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_TITLE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);
		} else if (name.equals(OSISUtil.OSIS_ELEMENT_REFERENCE)) {
			writeContentStack.push(CONTENT_STATE.IGNORE);
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
    throws SAXException
    {
		String name = getName(sName, qName);
		debug(name, null, false);
		
		writeContentStack.pop();

	}
    
    /*
     * While Parsing the XML file, if extra characters like space or enter Character
     * are encountered then this method is called. If you don't want to do anything
     * special with these characters, then you can normally leave this method blank.
    */
    @Override
    public void characters (char buf [], int offset, int len) throws SAXException
    {
        if (CONTENT_STATE.WRITE.equals(writeContentStack.peek())) {
            String s = new String(buf, offset, len);
            write(s);
        }
    }
}

