package net.bible.service.readingplan;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.SimpleOsisParser;
import org.crosswire.jsword.versification.Versification;

/**
 * Get a Key from either a simple reference or an OSIS reference
 */
public class PassageReader {
	public enum PassageReferenceType {TEXT, OSIS};
	
	private PassageReferenceType passageReferenceType;
	private Versification v11n;
	
	PassageReader(Versification v11n, PassageReferenceType passageReferenceType) {
		this.v11n = v11n;
		this.passageReferenceType = passageReferenceType;
	}

	public Key getKey(String passage) throws NoSuchKeyException {
		if (PassageReferenceType.OSIS.equals(passageReferenceType)) {
			return SimpleOsisParser.parseOsisRef(v11n, passage); 
		} else {
			return PassageKeyFactory.instance().getKey(v11n, passage);
		}
		
	}
}
