package net.bible.service.format.osistohtml;


import java.util.Iterator;

import net.bible.service.common.Constants;
import net.bible.service.common.Logger;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.RestrictionType;
import org.crosswire.jsword.passage.VerseRange;
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
public class ReferenceHandler {

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
    
    /** create a link tag from an OSISref and the content of the tag
     */
    private String getReferenceTag(String reference, String content) {
    	log.debug("Ref:"+reference+" Content:"+content);
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
    		
    		// convert urns of type book:key to sword://book/key to simplify urn parsing (1 fewer case to check for).  
    		// Avoid urls of type 'matt 3:14' by excludng urns with a space
    		if (reference.contains(":") && !reference.contains(" ") && !reference.startsWith("sword://")) {
    			reference = "sword://"+reference.replace(":", "/");
    		}

    		boolean isFullSwordUrn = reference.contains("/") && reference.contains(":");
    		if (isFullSwordUrn) {
    			// e.g. sword://StrongsRealGreek/01909
    			// don't play with the reference - just assume it is correct
				result.append("<a href='").append(reference).append("'>");
				result.append(content);
				result.append("</a>");
    		} else {
		        Passage ref = (Passage) PassageKeyFactory.instance().getKey(parameters.getDocumentVersification(), reference);
		        boolean isSingleVerse = ref.countVerses()==1;
		        boolean isSimpleContent = content.length()<3 && content.length()>0;
		        Iterator<VerseRange> it = ref.rangeIterator(RestrictionType.CHAPTER);
		        
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

