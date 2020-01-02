/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.format.osistohtml;

import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

/**
 * Write characters out to a StringBuilder - used while creating html for display
 * 
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

    public void abortAnyUnterminatedInsertion() {
		if (insertionRequestCount > 0) {
			// force insertion to finish in the case a closing pre-verse tag was missing
			insertionRequestCount = 1;
			finishInserting();
		}
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
