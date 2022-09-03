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
package net.bible.android.view.activity.navigation.genbookmap

import android.util.Log
import net.bible.android.control.page.CurrentGeneralBookPage
import org.crosswire.jsword.passage.Key

/** show a list of keys and allow to select an item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChooseGeneralBookKey : ChooseKeyBase() {
    override val currentKey: Key?
        get() = currentGeneralBookPage.key


    override val keyList: List<Key> get() = currentGeneralBookPage.cachedGlobalKeyList!!

    override fun itemSelected(key: Key) {
        try {
            currentGeneralBookPage.setKey(key)
        } catch (e: Exception) {
            Log.e(TAG, "error on select of gen book key", e)
        }
    }

    private val currentGeneralBookPage: CurrentGeneralBookPage
        get() = activeWindowPageManagerProvider.activeWindowPageManager.currentGeneralBook

    companion object {
        private const val TAG = "ChooseGeneralBookKey"
    }
}
