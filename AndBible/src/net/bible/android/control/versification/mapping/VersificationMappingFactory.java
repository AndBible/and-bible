package net.bible.android.control.versification.mapping;

import org.crosswire.jsword.versification.Versification;

/**
 * Provide Versification Mappings
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VersificationMappingFactory {

	// Limited mappings supported at the moment
	private static final KJVSynodalVersificationMapping kjvSynodalVersificationMapping = new KJVSynodalVersificationMapping();
	private static final KJVGermanVersificationMapping kjvGermanVersificationMapping = new KJVGermanVersificationMapping();
	private static final KJVLeningradVersificationMapping kjvLeningradVersificationMapping = new KJVLeningradVersificationMapping();
	private static final KJVVulgVersificationMapping kjvVulgVersificationMapping = new KJVVulgVersificationMapping();
	private static final DefaultVersificationMapping defaultVersificationMapping = new DefaultVersificationMapping();

	private static final VersificationMappingFactory singleton = new VersificationMappingFactory();
	
	public static VersificationMappingFactory getInstance() {
		return singleton;
	}
	
	/** Return the correct v11n mapping provider for the versifications
	 *  
	 * @return	Mapping provider or default if no mapping provider exists or the v11ns are the same
	 */
	public VersificationMapping getVersificationMapping(Versification from, Versification to) {
		if (kjvSynodalVersificationMapping.canConvert(from, to)) {
			return kjvSynodalVersificationMapping;
		} else if (kjvGermanVersificationMapping.canConvert(from, to)) {
			return kjvGermanVersificationMapping;
		} else if (kjvLeningradVersificationMapping.canConvert(from, to)) {
			return kjvLeningradVersificationMapping;
		} else if (kjvVulgVersificationMapping.canConvert(from, to)) {
			return kjvVulgVersificationMapping;
		} else {
			return defaultVersificationMapping;
		}
	}
}
