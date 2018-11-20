/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.service.format;

import net.bible.android.BibleApplication;
import net.bible.android.SharedConstants;
import net.bible.service.device.ScreenSettings;

/** prepare an error message for display in a WebView
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class HtmlMessageFormatter {

	private static final String NIGHT_STYLESHEET = "<link href='file:///android_asset/web/"
			+ SharedConstants.NIGHT_MODE_STYLESHEET
			+ "' rel='stylesheet' type='text/css'/>";
	
	private static final String NIGHT_HEADER = "<html><head>"+NIGHT_STYLESHEET+"</head><body>";
	private static final String NIGHT_FOOTER = "</body></html>";
	
	@SuppressWarnings("unused")
	private static final String TAG = "HtmlmessageFormatter";
	
	/** wrap text with nightmode css if required
	 */
	public static String format(int msgId) {
    	return format(msgId, false);
	}

	/** wrap text with nightmode css if required
	 */
	public static String format(int msgId, boolean simpleHtmlOnly) {
		String errorMsg = BibleApplication.getApplication().getResources().getString(msgId);
		if (simpleHtmlOnly) {
			return errorMsg;
		} else {
			return format(errorMsg);
		}
	}

	/** wrap text with nightmode css if required
	 */
	public static String format(String text) {
		boolean isNightMode = ScreenSettings.isNightMode();
		
		String formattedText;
		
		// only require special formatting for nightmode
		if (!isNightMode) {
			formattedText = text;
		} else {
			formattedText = NIGHT_HEADER+text+NIGHT_FOOTER;
		}
		return formattedText;
	}
}
