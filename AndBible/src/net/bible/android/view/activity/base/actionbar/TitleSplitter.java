package net.bible.android.view.activity.base.actionbar;

import org.apache.commons.lang.StringUtils;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class TitleSplitter {
	
	private static final int MAX_PART_LENGTH = 6;

	public String[] split(String text) {
		if (text==null) {
			return new String[0];
		}

		// this is normally used for verses e.g. '1Cor 2:1' -> '1Cor','2:1'
		// Explained: there must be at least 3 chars before a space to split
		String[] parts = text.split("(?<=... )");
		
		// this is normally used for module names e.g. 'GodsWord' -> 'Gods','Word'
		if (parts.length==1) {
			parts = text.split("(?<=[a-z])(?=[A-Z0-9])");
		}
		
		checkMaximumPartLength(parts, MAX_PART_LENGTH);

		return parts;
	}

	public String shorten(String text, int maxLength) {
		if (text==null) {
			return "";
		}
		if (text.length()<=maxLength) {
			return text;
		}
		// take characters from the end of each part until required length obtained
		String[] parts = text.split("(?<=[a-z])(?=[A-Z0-9 ])");
		int numParts = parts.length;
		if (numParts==1) {
			return text.substring(0, maxLength);
		}

		// basicLength will be a bit short if the length of all parts is not going to be the same
		int basicSplitLength = maxLength/numParts;
		int remaining = maxLength % numParts;
		// add remaining to end parts because they are more specific 
		int startToAddRemainingFrom = numParts-remaining;
		
		StringBuilder result = new StringBuilder();
		for (int i=0; i<parts.length; i++) {
			int partLen = basicSplitLength;
			if (i>=startToAddRemainingFrom) {
				partLen++;
			}
			result.append(StringUtils.left(parts[i], partLen));
		}
		
		return result.toString();
	}
	
	private String[] checkMaximumPartLength(String[] parts, int maximumLength) {
		for (int i=0; i<parts.length; i++) {
			if (parts[i].length()>maximumLength) {
				parts[i] = StringUtils.left(parts[i], maximumLength);
			}
		}
		return parts;
	}
}
