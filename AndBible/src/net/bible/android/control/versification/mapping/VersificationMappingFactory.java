package net.bible.android.control.versification.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.versification.Versification;

import android.util.Log;

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
	
	private static final String TAG = "VersificationMappingFactory";
	
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
		addMapping(new NRSVKJVVersificationMapping());
		addMapping(new NRSVSynodalVersificationMapping());
		addMapping(new NRSVGermanVersificationMapping());
		addMapping(new NRSVLeningradVersificationMapping());
		addMapping(new NRSVVulgVersificationMapping());
		addMapping(new SynodalLeningradVersificationMapping());
		addMapping(new SynodalVulgVersificationMapping());
		addMapping(new GermanSynodalVersificationMapping());
		addMapping(new GermanLeningradVersificationMapping());
		addMapping(new GermanVulgVersificationMapping());
		addMapping(new LeningradVulgVersificationMapping());
		
		initialiseRequiredMappings();
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
	 *  pre-initialise mappings to prevent pauses during interaction
	 */
	private void initialiseRequiredMappings() {
		// Find all versifications used by currently installed bibles
		final Set<Versification> versifications = new HashSet<Versification>();
		List<Book> bibles = SwordDocumentFacade.getInstance().getBibles();
		for (Book bible : bibles ) {
			if (bible instanceof SwordBook) {
				versifications.add(((SwordBook)bible).getVersification());
			}
		}
		
		// initialise in a background thread to allow normal startup to continue
		new Thread(	new Runnable() {
				public void run() {
					Log.d(TAG, "Initialise all required versification mappings");
					// Find all pairs of existing versifications and initialise mapping
					List<Versification> versificationList = new ArrayList<Versification>(versifications);
					for (int i=0; i<versificationList.size()-1; i++) {
						Versification v11n1 = versificationList.get(i);
						for (int j=i+1; j<versifications.size(); j++) {
							Versification v11n2 = versificationList.get(j);
							getVersificationMapping(v11n1, v11n2).initialiseOnce();
						}
					}
				}
			}).start();
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
