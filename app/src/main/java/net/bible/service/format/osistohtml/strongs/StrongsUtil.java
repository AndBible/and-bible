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

package net.bible.service.format.osistohtml.strongs;

import net.bible.service.common.Constants;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class StrongsUtil {

	private static final String DEFAULT_CSS_CLASS = "strongs";
	
	/**
	 * create an html link for teh passed in strongs number and protocol
	 * @param protocol = G or H
	 * @param strongsNumber
	 * @return
	 */
	public static String createStrongsLink(String protocol, String strongsNumber) {
		return createStrongsLink(protocol, strongsNumber, strongsNumber, DEFAULT_CSS_CLASS);
	}
	
	public static String createStrongsLink(String protocol, String strongsNumber, String content, String cssClass) {
		// pad with leading zeros to 5 characters
		String paddedRef = StringUtils.leftPad(strongsNumber, 5, "0");

		StringBuilder tag = new StringBuilder();
		// create opening tag for Strong's link
		tag.append("<a href='");

		// calculate uri e.g. H:01234
		tag.append(protocol).append(":").append(paddedRef);

		// set css class
		tag.append("' class='"+cssClass+"'>");

		// descriptive string
		tag.append(content);

		// link closing tag
		tag.append("</a>");
		
		String strTag = tag.toString();
		return strTag;
	}

	public static String getStrongsProtocol(String ref) {
		if (ref.startsWith("H")) {
			return Constants.HEBREW_DEF_PROTOCOL;
		} else if (ref.startsWith("G")) {
			return Constants.GREEK_DEF_PROTOCOL;
		}
		return null;
	}
}
