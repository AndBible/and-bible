package net.bible.service.format;


import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import net.bible.service.sword.Logger;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class OsisToHtmlSaxHandler extends DefaultHandler {
    
    // properties
    private boolean isHeadings = true;
    private boolean isVerseNumbers = true;
    private boolean isDelayVerse = false;
    private String currentVerse;

    private boolean isLeftToRight = true;
    
    // debugging
    private boolean isDebugMode = false;

    private boolean isWriteContent = true;
    
    private Writer writer;
    
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

    public void startDocument () throws SAXException
    {
        write("<html dir='"+getDirection()+"'><head><meta charset='utf-8'/></head><body>");
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
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
    public void startElement(String namespaceURI,
            String sName, // simple name
            String qName, // qualified name
            Attributes attrs)
    throws SAXException
    {
      String name = getName(sName, qName); // element name

      debug(name, attrs, true);
      
      if (name.equals("title") && this.isHeadings) {
    	  isDelayVerse = true;
          write("<h3>");
      } else if (name.equals("verse")) {
          if (isVerseNumbers) {
        	  currentVerse = osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID));
          }
      } else if (name.equals("note")) {
          isWriteContent = false;
      } else if (name.equals("lb") || name.equals("p")) {
          write("<p />");
      }
    } 
    
    /** return verse from osos id of format book.chap.verse
     * 
     * @param s osis Id
     * @return verse number
     */
    private String osisIdToVerseNum(String s) {
       /* You have to use "\\.", the first backslash is interpreted as an escape by the
        Java compiler, so you have to use two to get a String that contains one
        backslash and a dot, which is what you want the regexp engine to see.*/
        String[] parts = s.split("\\.");
        if (parts.length>1) {
            return parts[parts.length-1];
        }
        return "";
    }
    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
    public void endElement(String namespaceURI,
            String sName, // simple name
            String qName  // qualified name
            )
    throws SAXException
    {
      String name = getName(sName, qName);
      
      debug(name, null, false);
      
      if (name.equals("title") && this.isHeadings) {
          write("</h3>");
          isDelayVerse = false;
      } else if (name.equals("verse")) {
      } else if (name.equals("note")) {
          isWriteContent = true;
      } else if (name.equals("l")) {
    	  write("<br />");
      }
    } 
    
    /*
     * While Parsing the XML file, if extra characters like space or enter Character
     * are encountered then this method is called. If you don't want to do anything
     * special with these characters, then you can normally leave this method blank.
    */
    public void characters (char buf [], int offset, int len) throws SAXException
    {
    	writeVerse();
        if (isWriteContent) {
            String s = new String(buf, offset, len);
            write(s);
        }
    }

	private void writeVerse() throws SAXException {
    	if (!isDelayVerse && currentVerse!=null) {
    		write("<small><span color='red'>"+currentVerse+"</span></small> ");
    		currentVerse = null;
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
    public void setVerseNumbers(boolean isVerseNumbers) {
        this.isVerseNumbers = isVerseNumbers;
    }
	public void setDebugMode(boolean isDebugMode) {
		this.isDebugMode = isDebugMode;
	}
}

