package org.crosswire.jsword.book.sword;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.RestrictionType;
import org.crosswire.jsword.passage.VerseRange;

public class SwordBookLite {

	private ZVerseBackendLite backend;
	
	public SwordBookLite (SwordBookMetaData sbmd) {
//todo ************ mustn't create this all the time - whole book loaded per chapter
		backend = new ZVerseBackendLite(sbmd);
	}

	public String getOsisText(Key key) throws BookException {
		StringBuffer buff = new StringBuffer();
		Iterator<String> verses = getOsisIterator(key, true);
		buff.append("<div>");
		while (verses.hasNext()) {
			buff.append(verses.next());
		}
		buff.append("</div>");

		return buff.toString();
	}
	
	/*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.Book#getOsisIterator(org.crosswire.jsword.passage
     * .Key, boolean)
     */
    public Iterator<String> getOsisIterator(Key key, boolean allowEmpty) throws BookException {
        // Note: allowEmpty indicates parallel view
        // TODO(DMS): make the iterator be demand driven
        List<String> content = new ArrayList<String>();

        // For all the ranges in this Passage
        Passage ref = KeyUtil.getPassage(key);
        boolean showTitles = ref.hasRanges(RestrictionType.CHAPTER) || !allowEmpty;
        Iterator<Key> rit = ref.rangeIterator(RestrictionType.CHAPTER);

        StringBuffer buff = new StringBuffer();
        while (rit.hasNext()) {
            VerseRange range = (VerseRange) rit.next();

            if (showTitles) {
                buff.append("<title>").append(range.getName()).append("</title>");
            }

            // For all the verses in this range
            Iterator<Key> vit = range.iterator();
            while (vit.hasNext()) {
                Key verse = vit.next();
                String txt = backend.getRawText(verse);

                // If the verse is empty then we shouldn't add the verse tag
                if (allowEmpty || txt.length() > 0) {
                    String osisText = addVerseTag(verse, txt);
                    content.add(osisText);
               }
            }
        }

        return content.iterator();
    }
        
    private static final String VERSE_TAG_START = "<"+OSISUtil.OSIS_ELEMENT_VERSE+" "+OSISUtil.OSIS_ATTR_OSISID+"='";
    private static final String VERSE_TAG_END = "'>";
    private static final String VERSE_TAG_CLOSE = "</"+OSISUtil.OSIS_ELEMENT_VERSE+">";
    private String addVerseTag(Key verse, String plain) {
    	String ret = plain;
    	if (!plain.contains("<"+OSISUtil.OSIS_ELEMENT_VERSE)) {
    		StringBuffer buff = new StringBuffer();
    		buff.append(VERSE_TAG_START).append(verse.getOsisID()).append(VERSE_TAG_END).append(plain).append(VERSE_TAG_CLOSE);
    		ret = buff.toString();
    	}
    	return ret;
    }

}
