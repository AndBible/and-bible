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

package net.bible.service.format.osistohtml.taghandler;


import net.bible.service.common.Constants;
import net.bible.service.common.Logger;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.RestrictionType;
import org.crosswire.jsword.passage.VerseRange;
import org.xml.sax.Attributes;

import java.util.Iterator;

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
 */
public class ReferenceHandler implements OsisTagHandler {

    private OsisToHtmlParameters parameters;

    private String currentRefOsisRef;
    
    private NoteHandler noteHandler;

    private HtmlTextWriter writer;

    private static final Logger log = new Logger("ReferenceHandler");
    
    public ReferenceHandler(OsisToHtmlParameters osisToHtmlParameters, NoteHandler noteHandler, HtmlTextWriter theWriter) {
        this.parameters = osisToHtmlParameters;
        this.noteHandler = noteHandler;
        this.writer = theWriter;
    }

	@Override
	public String getTagName() {
		return OSISUtil.OSIS_ELEMENT_REFERENCE;
	}

    public void start(Attributes attrs) {
		// store the osisRef attribute for use with the note
		String target = attrs.getValue(OSISUtil.OSIS_ATTR_REF);
		start(target);
	}
    
    protected void start(String target) {
		// don't need to do anything until closing reference tag except..
		// delete separators like ';' that sometimes occur between reference tags
		writer.clearTempStore();
		writer.writeToTempStore();
		// store the osisRef attribute for use with the note
		this.currentRefOsisRef = target;
	}
    
    public void end() {
		writer.finishWritingToTempStore();

		if (noteHandler.isInNote() || parameters.isAutoWrapUnwrappedRefsInNote()) {
			noteHandler.addNoteForReference(writer.getTempStoreString(), currentRefOsisRef);
		} else {
			String refText = writer.getTempStoreString();
			writer.write(getReferenceTag(currentRefOsisRef, refText));
		}

		// and clear the buffer
		writer.clearTempStore();
		currentRefOsisRef = null;
	}

	private String getReferenceFromContent(String content) {
		// JSword does not know the basis (default book) so prepend it if it looks like JSword failed to work it out
		// We only need to worry about the first ref because JSword uses the first ref as the basis for the subsequent refs
		// if content starts with a number and is not followed directly by an alpha char e.g. 1Sa
		String reference = null;

		if (content != null && content.length() > 0 && StringUtils.isNumeric(content.subSequence(0, 1)) &&
				(content.length() < 2 || !StringUtils.isAlphaSpace(content.subSequence(1, 2)))) {

			// maybe should use VerseRangeFactory.fromstring(orig, basis)
			// this check for a colon to see if the first ref is verse:chap is not perfect but it will do until JSword adds a fix
			int firstColonPos = content.indexOf(":");
			boolean isVerseAndChapter = firstColonPos > 0 && firstColonPos < 4;
			if (isVerseAndChapter) {
				reference = parameters.getBasisRef().getBook().getOSIS() + " " + content;
			} else {
				reference = parameters.getBasisRef().getBook().getOSIS() + " " + parameters.getBasisRef().getChapter() + ":" + content;
			}
			log.debug("Patched reference:" + reference);
		} else if (content != null){
			// Avoid urls of type 'matt 3:14' by excluding urns with a space
			if (content.contains(" ")) {
				reference = content.replace(":", "/");
			}
			else
				reference = content;
		}
		return reference;
	}
    
    /** create a link tag from an OSISref and the content of the tag
     */
    private String getReferenceTag(String reference, String content) {
    	log.debug("Ref:"+reference+" Content:"+content);
    	StringBuilder result = new StringBuilder();
    	boolean isFullSwordUrn;
    	try {

    		if(reference==null) {
    			reference = getReferenceFromContent(content);
    			isFullSwordUrn = false;
			}
			else {
				isFullSwordUrn = reference.contains("/") && reference.contains(":");

				// convert urns of type book:key to sword://book/key to simplify urn parsing (1 fewer case to check for).
				if (reference.contains(":") && !reference.startsWith("sword://")) {
					reference = "sword://" + reference.replace(":", "/");
					isFullSwordUrn = true;
				}
			}

    		if (isFullSwordUrn) {
    			// e.g. sword://StrongsRealGreek/01909 or Genbook reference
    			// don't play with the reference - just assume it is correct
				result.append("<a href='").append(reference).append("'>");
				result.append(content);
				result.append("</a>");
    		} else {
		        Passage ref = (Passage) PassageKeyFactory.instance().getKey(parameters.getDocumentVersification(), reference);
		        boolean isSingleVerse = ref.countVerses()==1;
		        boolean hasContent = content.length()>0;
				boolean hasSeparateRefs = reference.contains(" ");
				boolean isSingleRange = !isSingleVerse && !hasSeparateRefs;


		        if ((isSingleVerse || isSingleRange) && hasContent) {
					// simple verse no e.g. 1 or 2 preceding the actual verse in TSK
					Iterator<VerseRange> it = ref.rangeIterator(RestrictionType.NONE);
					result.append("<a href='").append(Constants.BIBLE_PROTOCOL).append(":").append(it.next().getOsisRef()).append("'>");
					result.append(content);
					result.append("</a>");
				} else {
		        	// multiple complex references
					Iterator<VerseRange> it = ref.rangeIterator(RestrictionType.CHAPTER);
		        	boolean isFirst = true;
					while (it.hasNext()) {
						Key key = it.next();
						if (!isFirst) {
							result.append(" ");
						}
						// we just references the first verse in each range because that is the verse you would jump to.  It might be more correct to reference the while range i.e. key.getOsisRef() 
						result.append("<a href='").append(Constants.BIBLE_PROTOCOL).append(":").append(key.iterator().next().getOsisRef()).append("'>");
						result.append(key);
						result.append("</a>");
						isFirst = false;
					}
		        }
    		}
    	} catch (Exception e) {
    		log.error("Error parsing OSIS reference:"+reference);
    		// just return the content with no html markup
    		result.append(content);
    	}
    	return result.toString();
    }
}

