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
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
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
		if (ScreenSettings.isNightMode()) {
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
