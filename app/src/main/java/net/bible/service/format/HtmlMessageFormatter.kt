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

package net.bible.service.format

import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.service.device.ScreenSettings

/** prepare an error message for display in a WebView
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class HtmlMessageFormatter {


    companion object {

		private var jsTag = "\n<script type='text/javascript' src='file:///android_asset/web/loader.js'></script>\n"
		private val NIGHT_STYLESHEET = ("<link href='file:///android_asset/web/"
                + SharedConstants.NIGHT_MODE_STYLESHEET
                + "' rel='stylesheet' type='text/css'/>")
        private val NIGHT_HEADER = "<html><head>$NIGHT_STYLESHEET$jsTag</head><body><div id='start'></div>"
        private val NIGHT_FOOTER = "</body></html>"

        private val DAY_HEADER = "<html><head>$jsTag</head><body><div id='start'></div>"
        private val DAY_FOOTER = "</body></html>"

        private val TAG = "HtmlmessageFormatter"

        /** wrap text with nightmode css if required
         */
        @JvmOverloads
        fun format(msgId: Int, simpleHtmlOnly: Boolean = false): String {
            val errorMsg = BibleApplication.application.resources.getString(msgId)
            return if (simpleHtmlOnly) {
                errorMsg
            } else {
                format(errorMsg)
            }
        }

        /** wrap text with nightmode css if required
         */
        fun format(text: String): String {
            val isNightMode = ScreenSettings.nightMode

            val formattedText: String

            // only require special formatting for nightmode
            if (!isNightMode) {
                formattedText = DAY_HEADER + text + DAY_FOOTER
            } else {
                formattedText = NIGHT_HEADER + text + NIGHT_FOOTER
            }
            return formattedText
        }
    }
}
/** wrap text with nightmode css if required
 */
