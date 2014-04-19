package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.VersificationsMapper;

import android.util.Log;

/** Manage conversion of verses to a specific versification
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VersificationConverter {
	
	private static final String TAG = "VersificationConverter";
	
	private VersificationsMapper versificationsMapper = VersificationsMapper.instance();

	/** Return the verse in the required versification, mapping if necessary
	 */
	public Verse convert(Verse verse, Versification toVersification) {
		try {
			Key key = versificationsMapper.mapVerse(verse, toVersification);
			Verse mappedVerse = KeyUtil.getVerse(key);
			// If target v11n does not contain mapped verse then an exception normally occurs and the ordinal is set to 0 
			if (mappedVerse.getOrdinal()>0) {
				return mappedVerse;
			}
		} catch (Exception e) {
			// unexpected problem during mapping
			Log.e(TAG, "JSword Versification mapper failed to map "+verse.getOsisID()+" from "+verse.getVersification().getName()+" to "+toVersification.getName(), e);
		}
		// just try to retain information by forcing creation of a similar verse with the new v11n 
		return new Verse(toVersification, verse.getBook(), verse.getChapter(), verse.getVerse());
	}
}
