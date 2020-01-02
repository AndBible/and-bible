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

package net.bible.service.css;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.bible.android.SharedConstants;
import net.bible.service.device.ScreenSettings;

/**
 * Control CSS Stylesheet use.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class CssControl {

	private static final String DEFAULT_ASSET_FOLDER = "android_asset/web/";
	private static final String DEFAULT_STYLESHEET = "/"+DEFAULT_ASSET_FOLDER+SharedConstants.DEFAULT_STYLESHEET;
	private static final String NIGHT_MODE_STYLESHEET = "/"+DEFAULT_ASSET_FOLDER+SharedConstants.NIGHT_MODE_STYLESHEET;
	
	// User overrides
	private static final File MANUAL_DEFAULT_STYLESHEET_FILE = SharedConstants.MANUAL_CSS_STYLESHEET;
	private static final File MANUAL_NIGHT_MODE_STYLESHEET_FILE = SharedConstants.MANUAL_CSS_NIGHT_MODE_STYLESHEET;
	
	public List<String> getAllStylesheetLinks() {
		List<String> styleLinks = new ArrayList<>();
		// always used default stylesheet
		styleLinks.add(getLink(DEFAULT_STYLESHEET));
		
		// is there a user specific stylesheet provided by the user
		if (isManualCssOverride(MANUAL_DEFAULT_STYLESHEET_FILE)) {
			styleLinks.add(getLink(MANUAL_DEFAULT_STYLESHEET_FILE.getAbsolutePath()));
		}
		
		// if it is in night mode show the nightmode stylesheet
		if (ScreenSettings.INSTANCE.getNightMode()) {
			styleLinks.add(getLink(NIGHT_MODE_STYLESHEET));

			// is there a user specific night mode stylesheet provided by the user
			if (isManualCssOverride(MANUAL_NIGHT_MODE_STYLESHEET_FILE)) {
				styleLinks.add(getLink(MANUAL_NIGHT_MODE_STYLESHEET_FILE.getAbsolutePath()));
			}
		}
		
		return styleLinks;
	}

	private String getLink(String stylesheetName) {
		return "<link href='file://"+stylesheetName+"' rel='stylesheet' type='text/css'/>";
	}
	
	private boolean isManualCssOverride(File manualCssFile) {
		return manualCssFile.exists();
	}
}
