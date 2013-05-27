package net.bible.android.control.versification.mapping;

import net.bible.service.common.TwoWayHashmap;

import org.crosswire.jsword.passage.Verse;

public class TwoWayVerseMapping extends TwoWayHashmap<Verse, Verse> {
	
	/**
	 * If a mapping contains mappings to a verse with 'a', 'b' postfix there may be multiple mappings, 
	 * in which case use the lowest mapping because a/b postfix not supported
	 */
	public synchronized void addUsingLowestMappingIfMutiple(Verse leftVerse, Verse rightVerse) {
		Verse previousMappingForLeftVerse = getForward(leftVerse);
		if (previousMappingForLeftVerse==null || previousMappingForLeftVerse.compareTo(rightVerse)<0) {
			putForward(leftVerse, rightVerse);
		}
		
		Verse previousMappingForRightVerse = getBackward(rightVerse);
		if (previousMappingForRightVerse==null || previousMappingForRightVerse.compareTo(leftVerse)<0) {
			putForward(leftVerse, rightVerse);
		}
	}
}
