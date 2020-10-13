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

		// create opening tag for Strong's link
		// descriptive string
		// link closing tag
		return String.format("%s%s%s", createStrongsLinkOpenTag(protocol, strongsNumber, cssClass), content, createStrongsLinkCloseTag());
	}

	public static String createStrongsLinkOpenTag(String protocol, String strongsNumber) {
		return createStrongsLinkOpenTag(protocol, strongsNumber, DEFAULT_CSS_CLASS);
	}

	public static String createStrongsLinkOpenTag(String protocol, String strongsNumber, String cssClass) {
		String paddedRef = StringUtils.leftPad(strongsNumber, 5, "0");

		// calculate uri e.g. H:01234
		// set css class
		return String.format("<a href='%s:%s' class='%s'>", protocol, paddedRef, cssClass);
	}

	public static String createStrongsLinkCloseTag() {
		return "</a>";
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
