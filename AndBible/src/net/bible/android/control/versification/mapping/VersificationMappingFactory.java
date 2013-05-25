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
	private static final KJVLeningradVersificationMapping kjvLeningradVersificationMapping = new KJVLeningradVersificationMapping();
	private static final DefaultVersificationMapping defaultVersificationMapping = new DefaultVersificationMapping();

	private static final VersificationMappingFactory singleton = new VersificationMappingFactory();
	
	public static VersificationMappingFactory getInstance() {
		return singleton;
	}
	
	public VersificationMapping getVersificationMapping(Versification from, Versification to) {
		if (kjvSynodalVersificationMapping.canConvert(from, to)) {
			return kjvSynodalVersificationMapping;
		} else if (kjvLeningradVersificationMapping.canConvert(from, to)) {
			return kjvLeningradVersificationMapping;
		} else {
			return defaultVersificationMapping;
		}
	}
}
