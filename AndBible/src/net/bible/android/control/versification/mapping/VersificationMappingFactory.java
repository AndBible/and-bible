package net.bible.android.control.versification.mapping;

import java.util.HashMap;
import java.util.Map;

import org.crosswire.jsword.versification.Versification;

/**
 * Provide Versification Mappings
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VersificationMappingFactory {

	private static final NoVersificationMapping noVersificationMapping = new NoVersificationMapping();

	private Map<String, VersificationMapping> versificationMappingMap = new HashMap<String, VersificationMapping>();
	
	private static final VersificationMappingFactory singleton = new VersificationMappingFactory();
	
	public static VersificationMappingFactory getInstance() {
		return singleton;
	}
	
	/**
	 * Add all possible mappings to the HashMap
	 */
	private VersificationMappingFactory() {
		// Limited mappings supported at the moment
		addMapping(new KJVSynodalVersificationMapping());
		addMapping(new KJVGermanVersificationMapping());
		addMapping(new KJVLeningradVersificationMapping());
		addMapping(new KJVVulgVersificationMapping());
		addMapping(new GermanSynodalVersificationMapping());
		addMapping(new NRSVKJVVersificationMapping());
		addMapping(new NRSVSynodalVersificationMapping());
		addMapping(new NRSVGermanVersificationMapping());
		addMapping(new NRSVLeningradVersificationMapping());
		addMapping(new NRSVVulgVersificationMapping());
	}
	
	/** Return the correct v11n mapping provider for the versifications
	 *  
	 * @return	Mapping provider or default if no mapping provider exists or the v11ns are the same
	 */
	public VersificationMapping getVersificationMapping(Versification from, Versification to) {
		VersificationMapping versificationMapping = versificationMappingMap.get(getMappingIdentifier(from, to));
		if (versificationMapping==null) {
			versificationMapping = noVersificationMapping;
		}

		assert(versificationMapping.canConvert(from, to));
		return versificationMapping;
	}
	
	/** 
	 * add a mapping to the HashMap of mappings.  Each mapping handles both forward and backward mapping.
	 */
	private void addMapping(VersificationMapping mapping) {
		// forward mapping
		String lToRMappingId = getMappingIdentifier(mapping.getLeftVersification(), mapping.getRightVersification());
		versificationMappingMap.put(lToRMappingId, mapping);
		
		// reverse mapping
		String rToLMappingId = getMappingIdentifier(mapping.getRightVersification(), mapping.getLeftVersification());
		versificationMappingMap.put(rToLMappingId, mapping);
	}

	/** 
	 * get a key/identifier t represent a specific mapping from one v11n to another
	 */
	private String getMappingIdentifier(Versification from, Versification to) {
		StringBuilder builder = new StringBuilder();
		return builder.append(from.getName()).append("->").append(to.getName()).toString();
	}
}
