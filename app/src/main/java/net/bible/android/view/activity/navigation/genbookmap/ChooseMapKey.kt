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

import android.app.Activity
import android.content.Intent
import android.util.Log
import net.bible.android.control.page.CurrentMapPage
import org.crosswire.jsword.passage.Key

/** show a key list and allow to select item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChooseMapKey : ChooseKeyBase() {
    override val currentKey: Key?
        get() = currentMapPage.key

    override val keyList: List<Key>?
        get() = currentMapPage.cachedGlobalKeyList

    override fun itemSelected(key: Key) {
        val myIntent = Intent(this, ChooseMapKey::class.java)
        myIntent.putExtra("key", key.osisRef)
        myIntent.putExtra("book", currentMapPage.currentDocument?.initials)
        setResult(Activity.RESULT_OK, myIntent)
    }

    private val currentMapPage: CurrentMapPage
        get() = windowControl.activeWindowPageManager.currentMap

    companion object {
        private const val TAG = "ChooseMapKey"
    }
}
