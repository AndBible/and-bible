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

import android.content.Intent
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.navigation.ChooseDictionaryWord
import org.crosswire.jsword.passage.Key
import java.util.Calendar

/** Reference to current daily devotional page
 */
class CurrentDailyDevotionalPage internal constructor(
    pageManager: CurrentPageManager
) : CachedKeyPage(false, pageManager),
    CurrentPage
{
    override val documentCategory = DocumentCategory.DAILY_DEVOTIONS

    override fun startKeyChooser(context: ActivityBase) =
        context.startActivityForResult(Intent(context, ChooseDictionaryWord::class.java), STD_REQUEST_CODE)

    override fun doSetKey(key: Key?) {
        this._key = key
    }

    override fun next() {
        setKey(getKeyPlus(1))
    }

    override fun previous() {
        setKey(getKeyPlus(-1))
    }

    override val isSingleKey = true
    override val key: Key? get() = _key

    override val isSearchable = false

    /** Set key to today's date in MM.DD format */
    fun setToToday() {
        val cal = Calendar.getInstance()
        val month = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        val todayKey = "$month.$day"
        currentDocument?.let { doc ->
            try {
                setKey(doc.getKey(todayKey))
            } catch (e: Exception) {
                // key not found, leave as is
            }
        }
    }

    companion object {
        private const val TAG = "CurrentDailyDevotionalPage"
    }
}
