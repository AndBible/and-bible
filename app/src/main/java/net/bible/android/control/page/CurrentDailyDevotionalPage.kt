/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.page

import org.crosswire.jsword.passage.PreferredKey

/** Daily devotional page — navigates by date key (e.g. MM.DD).
 *  Extends CurrentDictionaryPage since SWORD uses the same zLD driver.
 */
class CurrentDailyDevotionalPage internal constructor(
    pageManager: CurrentPageManager
) : CurrentDictionaryPage(pageManager) {

    override val documentCategory = DocumentCategory.DAILY_DEVOTIONS

    /** Set key to today's date using JSword's PreferredKey interface */
    fun setToToday() {
        currentDocument?.let { doc ->
            try {
                if (doc is PreferredKey) {
                    setKey(doc.preferred)
                } else {
                    cachedGlobalKeyList?.firstOrNull()?.let { setKey(it) }
                }
            } catch (e: Exception) {
                cachedGlobalKeyList?.firstOrNull()?.let { setKey(it) }
            }
        }
    }

    companion object {
        private const val TAG = "CurrentDailyDevotionalPage"
    }
}
