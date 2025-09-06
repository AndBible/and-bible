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
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.view.activity.navigation.GridChoosePassageVerse
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.Key

/** show a list of keys and allow to select an item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChooseGeneralBookKey : ChooseKeyBase() {
    override val currentKey: Key?
        get() = currentGeneralBookPage.key


    override val keyList: List<Key> get() {
        val doc = currentGeneralBookPage.currentDocument!!
        return if(doc.isEpub) {
            val backend = (doc as SwordGenBook).backend as EpubBackend
            backend.tocKeys
        } else {
            currentGeneralBookPage.cachedGlobalKeyList!!
        }
    }

    override fun itemSelected(key: Key?) {
        val myIntent = Intent(this, ChooseGeneralBookKey::class.java)
        if(key is BookAndKey) {
            myIntent.putExtra("bookAndKey", key.serialized)
        } else {
            myIntent.putExtra("key", key?.osisRef?: currentGeneralBookPage.currentDocument!!.globalKeyList.first().osisRef)
            myIntent.putExtra("book", currentGeneralBookPage.currentDocument?.initials)
        }

        setResult(Activity.RESULT_OK, myIntent)
    }

    private val currentGeneralBookPage: CurrentGeneralBookPage
        get() = windowControl.activeWindowPageManager.currentGeneralBook

    companion object {
        private const val TAG = "ChooseGeneralBookKey"
    }
}
