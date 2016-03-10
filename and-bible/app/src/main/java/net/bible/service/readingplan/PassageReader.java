package net.bible.service.readingplan;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.OsisParser;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.versification.Versification;

import android.util.Log;

/**
 * Get a Key from either a simple reference or an OSIS reference
 */
public class PassageReader {

	private Versification v11n;
	
	private OsisParser osisParser = new OsisParser();
	
	private static final String TAG = "PassageReader";
	
	PassageReader(Versification v11n) {
		this.v11n = v11n;
	}

	/**
	 * Return a Key representing the passage passed in or an empty passage if it can't be parsed.
	 * @param passage Textual ref
	 * @return
	 */
	public Key getKey(String passage) {
		Key key = null;
		try {
			// spaces confuse the osis parser
			passage = passage.trim();
			
			// If expecting OSIS then use OSIS parser
			key = osisParser.parseOsisRef(v11n, passage);
			
			// OSIS parser is strict so try treating as normal ref if osis parser fails
			if (key==null) {
				Log.d(TAG, "Non OSIS Reading plan passage:"+passage);
				key = PassageKeyFactory.instance().getKey(v11n, passage);
			}
		} catch (Exception e) {
			Log.e(TAG, "Invalid passage reference in reading plan:"+passage);
		}
		
		// If all else fails return an empty passage to prevent NPE
		if (key==null) {
			key = PassageKeyFactory.instance().createEmptyKeyList(v11n);
		}
		return key;
	}
}
