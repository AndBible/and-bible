package net.bible.android.control.versification;

import net.bible.android.control.versification.mapping.VersificationMapping;
import net.bible.android.control.versification.mapping.VersificationMappingFactory;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

/** Manage conversion of verses to a specific versification
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VersificationConverter {
	private VersificationMappingFactory versificationMappingFactory = VersificationMappingFactory.getInstance();

	/** Return the verse in the required versification, mapping if necessary
	 */
	public Verse convert(Verse verse, Versification toVersification) {
		//TODO: After Tyndale merge, this will be 
		//     Key key = VersificationsMapper.instance().mapVerse(verse, toVersification)
		// then need to return first verse in key
		// 
		if (toVersification.equals(verse.getVersification())) {
			return verse;
		} else {
			VersificationMapping versificationMapping = versificationMappingFactory.getVersificationMapping(verse.getVersification(), toVersification);
			return versificationMapping.getMappedVerse(verse, toVersification);
		}
	}
}
