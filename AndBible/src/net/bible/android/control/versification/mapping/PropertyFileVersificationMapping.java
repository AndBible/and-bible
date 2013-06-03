package net.bible.android.control.versification.mapping;

import java.util.Map.Entry;
import java.util.Properties;

import net.bible.service.common.FileManager;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseFactory;
import org.crosswire.jsword.versification.Versification;

import android.util.Log;

/** 
 * Map verse between 2 different versifications using mapping data in a properties file
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
abstract public class PropertyFileVersificationMapping extends AbstractVersificationMapping implements VersificationMapping {

	protected TwoWayVerseMapping verseMap;
	
	static final String TAG = "PropertyFileVersificationMapping";
	
	public PropertyFileVersificationMapping(String leftVersificationName, String rightVersificationName) {
		super(leftVersificationName, rightVersificationName);
	}

	@Override
	public Verse getMappedVerse(Verse verse, Versification toVersification) {
		// only load large properties file with mapping data if required
		lazyInitializationOfMappingData();
		
		boolean isForward = toVersification.equals(getRightVersification());
		return mapVerse(verse, isForward, toVersification);
	}
	
	private Verse mapVerse(Verse verse, boolean forward, Versification versification) {
		Verse mappedVerse;
		mappedVerse = getMappedVersefrom2WayMap(verse, forward);
		
		// Rule: If there is no mapping rule for verse 0 but there is for verse 1 then use that rule but it can be different for both directions
		// This prevents wrong chapter jump when a user scrolls above v1 and v1 is mapped to a different chapter but v0 is unmapped
		if (mappedVerse==null && verse.getVerse()==0) {
			Verse verse1 = new Verse(verse.getVersification(), verse.getBook(), verse.getChapter(), 1);
			mappedVerse = getMappedVersefrom2WayMap(verse1, forward);
		}
		
		// no mapping found so just create a new unmapped verse with the correct versification
		if (mappedVerse==null) {
			mappedVerse = new Verse(versification, verse.getBook(), verse.getChapter(), verse.getVerse());
		}
		return mappedVerse;
	}

	private Verse getMappedVersefrom2WayMap(Verse verse, boolean forward) {
		Verse mappedVerse;
		if (forward) {
			mappedVerse = this.verseMap.getForward(verse);
		} else {
			mappedVerse = this.verseMap.getBackward(verse);
		}
		return mappedVerse;
	}


	private void lazyInitializationOfMappingData() {
		if (verseMap==null) {
			synchronized(this) {
				if (verseMap==null) {
					initialiseMappingData();
				}
			}
		}
	}

	private void initialiseMappingData() {
		Log.d(TAG, "Loading KIV<->Synodal mapping data");
		verseMap = new TwoWayVerseMapping();
	
		// load properties that define the map
		Properties mappingProperties = FileManager.readPropertiesFile("versificationmaps", getPropertiesFileName());
		
		for (Entry<Object,Object> entry : mappingProperties.entrySet()) {
			try {
				// remove a,b,c,.. from end of verse
				String leftVerseString = tidyVerse((String)entry.getKey());
				String rightVerseString = tidyVerse((String)entry.getValue());
				
				Verse leftVerse = VerseFactory.fromString(getLeftVersification(), leftVerseString);
				Verse rightVerse = VerseFactory.fromString(getRightVersification(), rightVerseString);
				
				// add but allow for a/b extensions by using lowest mapping if multiple mappings
				verseMap.addUsingLowestMappingIfMutiple(leftVerse, rightVerse);
				
			} catch (NoSuchVerseException nsve) {
				Log.e(TAG, "Bad verse in mapping data:"+entry);
			}
		}
	}
	
	private String getPropertiesFileName() {
		return getLeftVersification().getName()+"To"+getRightVersification().getName()+".properties";
	}

	private String tidyVerse(String verse) {
		String tidied = verse;
		Character lastChar = verse.charAt(verse.length()-1);
		if (lastChar>='a' && lastChar<='e') {
			tidied = verse.substring(0, verse.length()-1);
		}
		return tidied;
	}
}