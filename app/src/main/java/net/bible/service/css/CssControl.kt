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
package net.bible.service.css

import net.bible.android.SharedConstants
import net.bible.service.device.ScreenSettings.nightMode
import java.io.File
import java.util.*

/**
 * Control CSS Stylesheet use.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CssControl {// is there a user specific night mode stylesheet provided by the user
    // always used default stylesheet

    // is there a user specific stylesheet provided by the user

    // if it is in night mode show the nightmode stylesheet
    val allStylesheetLinks: List<String>
        get() {
            val styleLinks: MutableList<String> = ArrayList()
            // always used default stylesheet
            styleLinks.add(getLink(DEFAULT_STYLESHEET))

            // is there a user specific stylesheet provided by the user
            if (isManualCssOverride(MANUAL_DEFAULT_STYLESHEET_FILE)) {
                styleLinks.add(getLink(MANUAL_DEFAULT_STYLESHEET_FILE.absolutePath))
            }

            // if it is in night mode show the nightmode stylesheet
            if (nightMode) {
                styleLinks.add(getLink(NIGHT_MODE_STYLESHEET))

                // is there a user specific night mode stylesheet provided by the user
                if (isManualCssOverride(MANUAL_NIGHT_MODE_STYLESHEET_FILE)) {
                    styleLinks.add(getLink(MANUAL_NIGHT_MODE_STYLESHEET_FILE.absolutePath))
                }
            }
            return styleLinks
        }

    private fun getLink(stylesheetName: String): String {
        return "<link href='file://$stylesheetName' rel='stylesheet' type='text/css'/>"
    }

    private fun isManualCssOverride(manualCssFile: File): Boolean {
        return manualCssFile.exists()
    }

    companion object {
        private const val DEFAULT_ASSET_FOLDER = "android_asset/web/"
        private const val DEFAULT_STYLESHEET = "/" + DEFAULT_ASSET_FOLDER + SharedConstants.DEFAULT_STYLESHEET
        private const val NIGHT_MODE_STYLESHEET = "/" + DEFAULT_ASSET_FOLDER + SharedConstants.NIGHT_MODE_STYLESHEET

        // User overrides
        private val MANUAL_DEFAULT_STYLESHEET_FILE = SharedConstants.MANUAL_CSS_STYLESHEET
        private val MANUAL_NIGHT_MODE_STYLESHEET_FILE = SharedConstants.MANUAL_CSS_NIGHT_MODE_STYLESHEET
    }
}
