package net.bible.android.control.versification.mapping;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

/**
 * Versification Mapping that does nothing, used if a mapping does not exist but versifications differ
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DefaultVersificationMapping implements VersificationMapping {

	private static final String TAG = "DefaultVersificationMapping";
	
	@Override
	public Verse getMappedVerse(Verse verse, Versification toVersification) {
		if (toVersification.equals(verse.getVersification())) {
			return verse;
		} else {
			return new Verse(toVersification, verse.getBook(), verse.getChapter(), verse.getVerse());
		}
	}

	@Override
	public boolean canConvert(Versification from, Versification to) {
		return true;
	}

	@Override
	public String toString() {
		return TAG;
	}
}
