package net.bible.service.format.osistohtml;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

/**
 * Write characters out to a StringBuilder - used while creating html for display
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class HtmlTextWriter {

    private StringBuilder writer;
    
    private int dontWriteRequestCount = 0;
    
    private int writeTempStoreRequestCount = 0;
    private StringBuilder tempStore = new StringBuilder();

    // Prevent multiple conflicting preverse attempts
    private int insertionRequestCount = 0;
    
    // allow insert at a certain position
    private String overwrittenString = "";
    
    @SuppressWarnings("unused")
    private static final Logger log = new Logger("HtmlTextWriter");
    
    public HtmlTextWriter() {
        writer = new StringBuilder();
    }

    public void write(String htmlText) {
    	if (dontWriteRequestCount>0) {
    		// ignore all text
    	} else if (writeTempStoreRequestCount==0) {
			writer.append(htmlText);
        } else {
        	tempStore.append(htmlText); 
        }
    }
    
	/** allow line breaks and titles to be moved before verse number
	 */
	public void writeOptionallyBeforeVerse(String s, VerseInfo verseInfo) {
		boolean writeBeforeVerse = !verseInfo.isTextSinceVerse;
		if (writeBeforeVerse) {
			beginInsertAt(verseInfo.positionToInsertBeforeVerse);
		}
		write(s);
		if (writeBeforeVerse) {
			finishInserting();
		}
	}
    
    /** allow pre-verse headings
     */
    public void beginInsertAt(int insertOffset) {
		insertionRequestCount++;
    	if (insertionRequestCount==1) {
	    	overwrittenString = writer.substring(insertOffset);
	    	writer.delete(insertOffset, writer.length());
    	}
    }
    /** finish inserting and restore overwritten tail of string
     */
    public void finishInserting() {
    	if (insertionRequestCount==1) {
	    	writer.append(overwrittenString);
	    	overwrittenString = "";
    	}
    	insertionRequestCount--;
    }

    public int getPosition() {
    	return writer.length();
    }

    public void removeAfter(int position) {
    	writer.delete(position, writer.length());
    }
    
	public void reset() {
		writer.setLength(0);
	}
    
    public void writeToTempStore() {
    	writeTempStoreRequestCount++;
    }
    public void finishWritingToTempStore() {
    	writeTempStoreRequestCount--;
    }
    public void clearTempStore() {
    	tempStore.delete(0, tempStore.length());
    }
    public String getTempStoreString() {
    	return tempStore.toString();
    }
    public String getHtml() {
    	return writer.toString();
    }
    public void setDontWrite(boolean dontWrite) {
    	if (dontWrite) {
    		dontWriteRequestCount++;
    	} else {
    		dontWriteRequestCount--;
    	}
    }
}
