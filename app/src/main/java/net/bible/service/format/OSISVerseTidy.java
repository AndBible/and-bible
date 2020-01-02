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

package net.bible.service.format;


import net.bible.service.common.Logger;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;

/** Tidy up verses fetched from the raw input stream.  
 *  Ensure each verse has a verse tag and remove any extra tags that break the structure.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class OSISVerseTidy {

	private Book book;
	
    private static final String VERSE_OPENING_TAG_START = "<"+OSISUtil.OSIS_ELEMENT_VERSE+" "+OSISUtil.OSIS_ATTR_OSISID+"='";
    private static final String VERSE_OPENING_TAG_END = "'>";
    // WEB has <l> tags that span verses so avoid errors by using empty verse tags
    private static final String VERSE_CLOSING_TAG = "</"+OSISUtil.OSIS_ELEMENT_VERSE+">";
    
    private static final Logger log = new Logger(OSISVerseTidy.class.getName()); 

    /** Constructor
     * 
     * @param book The currently selected book
     */
    public OSISVerseTidy(Book book) {
    	this.book = book;
    }
	
    
	/** add verse number and do basic validation/fix of verse text
	 * @param key
	 * @param verseText
	 * @return
	 */
	public String tidy(Key key, String verseText) {
		verseText = checkVerseText(key, verseText);
		verseText = addVerseTag(key, verseText);
		return verseText;
	}
	
	/** This hack is based on a hack in JSword.  
	 * I suspect we need to start at the beginning of a chapter instead of verse 1 to fix this 
	 * because NET seems to have a <div> before the first verse and </div> at the end
	 * 
	 * @param key
	 * @return
	 */
	private String checkVerseText(Key key, String verseText) {
        // FIXME(dms): this is a major HACK handling a problem with a badly
        // encoded module.
		
        //TODO NET appears to open <div> before the verse start and the closing </div> is after the verse start - need to sort later 
        if (book.getAbbreviation().startsWith("NET") ) //$NON-NLS-1$ //$NON-NLS-2$
        {
        	if (verseText.contains("</div>") && !(verseText.contains("<div ") || verseText.contains("<div>")) ) {
        		log.debug("Fixing up NET div");
        		verseText = verseText.replaceAll("</div>", "");
        	}
//            verseText =  verseText.substring(0, verseText.length() - 6);
        }
        //TODO WEB appears to open <l> before the verse start and the closing </l> is after the verse start - need to sort later
        // as below you can see that <lg> and <l> start before the verse and close in the verse
        // I think teh default JSword module loader would probably strip all tags
//        <lg>
//        <l type="x-primary">
//         <verse sID="Ps.1.1" osisID="Ps.1.1" />Blessed is the man who doesnt walk in the counsel of the wicked,</l>
//        <l type="x-secondary">nor stand in the way of sinners,</l>
//        <l type="x-secondary">nor sit in the seat of scoffers;</l>
//       </lg>

        if (book.getAbbreviation().startsWith("WEB") && key instanceof Verse ) //$NON-NLS-1$ //$NON-NLS-2$
        {
        	if (((Verse)key).getVerse()==1) {
        		log.debug("start of WEB chapter");
        		if (verseText.indexOf("</l>") < verseText.indexOf("<l type=\"x-primary\">")) {
            		log.debug("adding <lg><l>");
        			verseText = "<lg><l type=\"x-primary\">"+verseText;
        		}
        	}
//        	if (StringUtils.countMatches(verseText, "<l>") < StringUtils.countMatches(verseText, "</l>") ) {
//        		log.debug("Fixing up WEB <l>");
//        		verseText = verseText.replaceFirst("</l>", "");
//        	}
//        	//TODO - really! how can a verse end with an opening tag
//        	if (verseText.endsWith("<l type=\"x-primary\"> ")) {
//        		verseText =  verseText.substring(0, verseText.length() - "<l type=\"x-primary\">".length());	
//        	}
        }
        return verseText;
	}
	
    /** Ensure each verse has the appropriate OSIS verse tag.
     * 
     * @param verse
     * @param plain
     * @return
     */
    private String addVerseTag(Key verse, String plain) {
    	String ret = plain;
    	if (!plain.contains("<"+OSISUtil.OSIS_ELEMENT_VERSE)) {
    		StringBuilder bldr = new StringBuilder();
    		bldr.append(VERSE_OPENING_TAG_START).append(verse.getOsisID()).append(VERSE_OPENING_TAG_END).append(plain).append(VERSE_CLOSING_TAG);
    		ret = bldr.toString();
    	}
    	return ret;
    }

}
