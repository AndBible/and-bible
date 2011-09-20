package net.bible.android.control.page;

import java.util.List;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.TreeKey;

public class TreeKeyHelper {
	
	public static int findIndexOf(Key key, List<Key> keyList) {
		int index = -1;
		if (key !=null) {
			boolean isKeyTree = key instanceof TreeKey;
			for (int i=0; i<keyList.size() && index==-1; i++) {
				if (isMatch(key, keyList.get(i), isKeyTree)) {
					index = i;
				}
			}
		}
		
		return index;
	}

	/** equality is tricky if comparing TreeKeys (as used by GenBooks) because some child keys can have the same name but different parents
	 */
	private static boolean isMatch(Key key1, Key key2, boolean checkParent) {
		boolean isMatch = false;
		if (key1==null && key2==null) {
			isMatch = true;
		} else if (key1!=null && key2!=null) {
			if (key1.getName().equals(key2.getName())) {
				// keys match so now default to match = true unless parents exist and are different
				isMatch = true;
				// KeyTrees nodes can have the same name but different parents
				if (checkParent) {
					isMatch = isMatch(key1.getParent(), key2.getParent(), true);
				}
			}
		}
		return isMatch;
	}
}
