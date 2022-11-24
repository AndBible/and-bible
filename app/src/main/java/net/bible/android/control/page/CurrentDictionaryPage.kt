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
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.passage.Key

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentDictionaryPage internal constructor(
    swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : CachedKeyPage(false, swordDocumentFacade, pageManager),
    CurrentPage
{
    override val documentCategory = DocumentCategory.DICTIONARY

    override fun startKeyChooser(context: ActivityBase) = context.startActivityForResult(Intent(context, ChooseDictionaryWord::class.java), STD_REQUEST_CODE)

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

	/** can we enable the main menu search button
     */
    override val isSearchable = false

    companion object {
        private const val TAG = "CurrentDictionaryPage"
    }
}
