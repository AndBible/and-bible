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
	public static final String PASSAGE_REFERENCE_TYPE_OSIS = "OSIS";
	public enum PassageReferenceType {TEXT, OSIS};
	
	private PassageReferenceType passageReferenceType;
	private Versification v11n;
	
	private OsisParser osisParser = new OsisParser();
	
	private static final String TAG = "PassageReader";
	
	PassageReader(Versification v11n, PassageReferenceType passageReferenceType) {
		this.v11n = v11n;
		this.passageReferenceType = passageReferenceType;
	}

	/**
	 * Return a Key representing the passage passed in or an empty passage if it can't be parsed.
	 * @param passage Textual ref
	 * @return
	 */
	public Key getKey(String passage) {
		Key key = null;
		try {
			// If expecting OSIS then use OSIS parser
			if (PassageReferenceType.OSIS.equals(passageReferenceType)) {
				key = osisParser.parseOsisRef(v11n, passage); 
			}
			
			// OSIS parser is strict so try treating as normal ref if osis parser fails or if not expecting OSIS
			if (key==null) {
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
