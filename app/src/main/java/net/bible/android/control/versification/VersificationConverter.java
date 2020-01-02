/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.versification;

import androidx.annotation.Nullable;
import android.util.Log;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.RangedPassage;
import org.crosswire.jsword.passage.RestrictionType;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.VersificationsMapper;

import java.util.Iterator;

/** Manage conversion of verses to a specific versification
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class VersificationConverter {
	
	private static final String TAG = "VersificationConverter";
	
	private VersificationsMapper versificationsMapper = VersificationsMapper.instance();

	/**
	 * Flexible converter for the generic Key base class.  
	 * Return the key in the required versification, mapping if necessary
	 * Currently only handles Passage, RangedPassage, and Verse
	 */
	public Key convert(Key key, Versification toVersification) {
		try {
			if (key instanceof RangedPassage) {
				return convert((RangedPassage)key, toVersification);
			} else if (key instanceof VerseRange) {
				return convert((VerseRange)key, toVersification);
			} else if (key instanceof Passage) {
				return convert((Passage)key, toVersification);
			} else if (key instanceof Verse) {
				return convert((Verse)key, toVersification);
			}
		} catch (Exception e) {
			// unexpected problem during mapping
			Log.e(TAG, "JSword Versification mapper failed to map "+key.getOsisID()+" to "+toVersification.getName(), e);
		}
		return PassageKeyFactory.instance().createEmptyKeyList(toVersification);
	}

	public Passage convert(Passage passage, Versification toVersification) {
		return versificationsMapper.map(passage, toVersification);
	} 

	public RangedPassage convert(RangedPassage rangedPassage, Versification toVersification) {
		RangedPassage result = new RangedPassage(toVersification);
		Iterator<VerseRange> iter = rangedPassage.rangeIterator(RestrictionType.NONE);
		while (iter.hasNext()) {
			result.add(convert(iter.next(), toVersification));
		}
		return result;
	} 

	public VerseRange convert(VerseRange verseRange, Versification toVersification) {
		Verse startVerse = verseRange.getStart();
		Verse endVerse = verseRange.getEnd();
		
		Verse convertedStartVerse = convert(startVerse, toVersification);
		Verse convertedEndVerse = convert(endVerse, toVersification);

		return new VerseRange(toVersification, convertedStartVerse, convertedEndVerse);
	}

	/** Return the verse in the required versification, mapping if necessary
	 */
	public Verse convert(Verse verse, Versification toVersification) {
		Verse mappedVerse = convertVerseStrictly(verse, toVersification);
		if (mappedVerse != null) return mappedVerse;
		// just try to retain information by forcing creation of a similar verse with the new v11n
		return new Verse(toVersification, verse.getBook(), verse.getChapter(), verse.getVerse());
	}

	public boolean isConvertibleTo(Verse verse, Versification v11n) {
		return convertVerseStrictly(verse, v11n)!=null;
	}

	/**
	 * Convert the verse correctly to the v11n or return null
	 */
	@Nullable
	private Verse convertVerseStrictly(Verse verse, Versification toVersification) {
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
		return null;
	}
}
