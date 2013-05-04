package net.bible.android.control.versification.mapping;

import java.util.Map.Entry;
import java.util.Properties;

import net.bible.service.common.FileManager;
import net.bible.service.common.TwoWayHashmap;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseFactory;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

import android.util.Log;

/**
 * Map verses between KJV and Synodal versification
 * 
 * @see: https://gitorious.org/~kalemas/sword-svn-mirrors/kalemas_at_mail_ru-trunk/blobs/35a3fc6bde1ccff945d51558d7e21ab1074a4152/include/canon_synodal.h
 * @see: https://code.google.com/p/xulsword/source/browse/trunk/Cpp/src/include/versemaps.h
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class KJVSynodalVersificationMapping implements VersificationMapping {

	private TwoWayHashmap<Verse, Verse> kjvSynodalVerseMap;
	
	private static final Versification kjvV11n = Versifications.instance().getVersification("KJV");
	private static final Versification synodalV11n = Versifications.instance().getVersification("Synodal");
	
	private static final String TAG = "KJVSynodalVersificationMapping";
	
	@Override
	public Verse getMappedVerse(Verse verse, Versification toVersification) {
		// only load large properties file with mapping data if required
		lazyInitializationOfMappingData();
		
		if (verse.getVersification().equals(kjvV11n)) {
			return toSynodal(verse);
		} else {
			return toKJV(verse);
		}
	}

	public Verse toSynodal(Verse verse) {
		Verse synodalVerse = this.kjvSynodalVerseMap.getForward(verse);
		if (synodalVerse==null) {
			synodalVerse = new Verse(synodalV11n, verse.getBook(), verse.getChapter(), verse.getVerse());
		}
		return synodalVerse;
	}

	private Verse toKJV(Verse verse) {
		Verse kjvVerse = this.kjvSynodalVerseMap.getBackward(verse);
		if (kjvVerse==null) {
			kjvVerse = new Verse(kjvV11n, verse.getBook(), verse.getChapter(), verse.getVerse());
		}
		return kjvVerse;
	}

	private void lazyInitializationOfMappingData() {
		if (kjvSynodalVerseMap==null) {
			synchronized(this) {
				if (kjvSynodalVerseMap==null) {
					initialiseMappingData();
				}
			}
		}
	}
	private void initialiseMappingData() {
		Log.d(TAG, "Loading KIV<->Synodal mapping data");
		kjvSynodalVerseMap = new TwoWayHashmap<Verse, Verse>();

		// load properties that define the map
		Properties mappingProperties = FileManager.readPropertiesFile("versificationmaps", "KJVToSynodal.properties");
		
		for (Entry<Object,Object> entry : mappingProperties.entrySet()) {
			try {
				Verse kjv = VerseFactory.fromString(kjvV11n, (String)entry.getKey());
				Verse synodal = VerseFactory.fromString(synodalV11n, (String)entry.getValue());
				kjvSynodalVerseMap.add(kjv, synodal);
			} catch (NoSuchVerseException nsve) {
				Log.e(TAG, "Bad verse in mapping data:"+entry);
			}
		}
	}

	@Override
	public boolean canConvert(Versification from, Versification to) {
		return (from.equals(kjvV11n) && to.equals(synodalV11n)) ||
			   (from.equals(synodalV11n) && to.equals(kjvV11n));
	}
	
	
}
