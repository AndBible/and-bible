package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.VersificationsMapper;

/** Manage conversion of verses to a specific versification
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VersificationConverter {
	
	private VersificationsMapper versificationsMapper = VersificationsMapper.instance();
//	private VersificationsMappingFactory versificationMappingFactory = VersificationMappingFactory.getInstance();

	/** Return the verse in the required versification, mapping if necessary
	 */
	public Verse convert(Verse verse, Versification toVersification) {
		Key key = versificationsMapper.mapVerse(verse, toVersification);
		return KeyUtil.getVerse(key);
//		if (toVersification.equals(verse.getVersification())) {
//			return verse;
//		} else {
//			VersificationMapping versificationMapping = versificationMappingFactory.getVersificationMapping(verse.getVersification(), toVersification);
//			return versificationMapping.getMappedVerse(verse, toVersification);
//		}
	}
}
