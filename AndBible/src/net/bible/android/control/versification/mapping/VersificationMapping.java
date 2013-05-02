package net.bible.android.control.versification.mapping;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

/**
 * Versification Mapping to map verses between different versifications
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface VersificationMapping {

	public Verse getMappedVerse(Verse verse, Versification toVersification);
	
	public boolean canConvert(Versification from, Versification to);
}
