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
