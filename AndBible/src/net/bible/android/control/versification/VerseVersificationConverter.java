package net.bible.android.control.versification;

import net.bible.android.control.versification.mapping.VersificationMapping;
import net.bible.android.control.versification.mapping.VersificationMappingFactory;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

/** Store a main verse and return it in requested versification after mapping (if map available)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseVersificationConverter implements Comparable<VerseVersificationConverter> {
	
	private Verse mainVerse;
	//todo implement cache to optimise verse creation time
//	private Map<Versification, Verse> versificationToVerse = new HashMap<Versification, Verse>();

	private VersificationMappingFactory versificationMappingFactory = VersificationMappingFactory.getInstance();

	public VerseVersificationConverter(Verse verse) {
		this(verse.getVersification(), verse.getBook(), verse.getChapter(), verse.getVerse());
	}

	public VerseVersificationConverter(BibleBook book, int chapter, int verseNo) {
		this(Versifications.instance().getVersification(Versifications.DEFAULT_V11N), book, chapter, verseNo);
	}

	public VerseVersificationConverter(Versification versification, BibleBook book, int chapter, int verseNo) {
		mainVerse = new Verse(versification, book, chapter, verseNo, true);
//		versificationToVerse.put(versification, mainVerse);
	}
	
	public void setVerseNo(int verseNo) {
//		versificationToVerse.clear();
		mainVerse = new Verse(mainVerse.getVersification(), mainVerse.getBook(), mainVerse.getChapter(), verseNo);
//		versificationToVerse.put(mainVerse.getVersification(), mainVerse);
	}
	public int getVerseNo() {
		return mainVerse.getVerse();
	}

	/** Set the verse, mapping to the required versification if necessary
	 */
	public void setVerse(Versification requiredVersification, Verse verse) {
		mainVerse = getVerse(requiredVersification, verse);
	}

	public Verse getVerse() {
		return mainVerse;
	}

	public Verse getVerse(Versification versification) {
		VersificationMapping versificationMapping = versificationMappingFactory.getVersificationMapping(mainVerse.getVersification(), versification);
		return versificationMapping.getMappedVerse(mainVerse, versification);
	}

	/** Return the verse in the required versification, mapping if necessary
	 */
	private Verse getVerse(Versification requiredVersification, Verse verse) {
		if (requiredVersification.equals(verse.getVersification())) {
			return verse;
		} else {
			VersificationMapping versificationMapping = versificationMappingFactory.getVersificationMapping(verse.getVersification(), requiredVersification);
			return versificationMapping.getMappedVerse(verse, requiredVersification);
		}
	}

	/** books should be the same as they are enums
	 */
	public BibleBook getBook() {
		return mainVerse.getBook();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((mainVerse == null) ? 0 : mainVerse.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VerseVersificationConverter other = (VerseVersificationConverter) obj;
		if (mainVerse == null) {
			if (other.mainVerse != null)
				return false;
		} else if (!mainVerse.equals(other.mainVerse))
			return false;
		return true;
	}
	
	@Override
	public int compareTo(VerseVersificationConverter another) {
		assert another!=null;
		return mainVerse.compareTo(another.getVerse(mainVerse.getVersification()));
	}
}
