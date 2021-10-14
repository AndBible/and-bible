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
package net.bible.android.control.page

import android.content.Intent
import android.view.Menu
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.passage.Key

/** Reference to current Map shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentMapPage internal constructor(
    swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
    ) : CachedKeyPage(false, swordDocumentFacade, pageManager),
    CurrentPage
{

    override val documentCategory = DocumentCategory.MAPS

    override fun startKeyChooser(context: ActivityBase) = context.startActivityForResult(Intent(context, ChooseMapKey::class.java), STD_REQUEST_CODE)

    /** set key without notification
     *
     * @param key
     */
    override fun doSetKey(key: Key?) {
        _key = key
    }

    override fun next() {
        getKeyPlus(1).let {
			setKey(it)
		}
    }

    override fun previous() {
        getKeyPlus(-1).let {
			setKey(it)
		}
    }

    override val isSingleKey = true
	override val key: Key? get() = _key //To change initializer of created properties use File | Settings | File Templates.

	/** can we enable the main menu search button
     */
    override val isSearchable: Boolean
        get() = false

    companion object {
        private const val TAG = "CurrentMapPage"
    }
}
