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

package net.bible.service.common;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class TitleSplitter {
	
	private static final int MAX_PART_LENGTH = 6;

	/**
	 * Split text to enable 2 parts to be shown as title on left of action bar
	 */
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

	/**
	 * Shorten camel case words uniformly e.g. StrongsRealGreek -> StReGr
	 * Used to create short action bar text for document names
	 * 
	 * @param text		Text to shorten
	 * @param maxLength	Max length of final string
	 * @return			Shortened text
	 */
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
