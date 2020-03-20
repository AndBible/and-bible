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

package net.bible.android.control.search;

import java.util.ArrayList;
import java.util.List;

import org.crosswire.jsword.passage.Key;

public class SearchResultsDto {
	
    private List<Key> mainSearchResults = new ArrayList<Key>();
    
    private List<Key> otherSearchResults = new ArrayList<Key>();
    
    public void add(Key resultKey, boolean isMain) {
    	if (isMain) {
    		mainSearchResults.add(resultKey);
    	} else {
    		otherSearchResults.add(resultKey);
    	}
    }
    
	public List<Key> getMainSearchResults() {
		return mainSearchResults;
	}

	public List<Key> getOtherSearchResults() {
		return otherSearchResults;
	}
	
	public int size() {
		return mainSearchResults.size()+otherSearchResults.size();
	}
}
