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
package net.bible.android.view.activity.search.searchresultsactionbar

import net.bible.service.common.CommonUtils.getResourceString
import net.bible.android.control.ApplicationScope
import javax.inject.Inject
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.actionbar.ToggleActionBarButton
import net.bible.android.activity.R

/** Toggle between 66 Bible books and deuterocanonical books
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class ScriptureToggleActionBarButton @Inject constructor(private val searchControl: SearchControl) :
    ToggleActionBarButton(R.drawable.ic_action_new, R.drawable.ic_baseline_undo_24) {
    override val title: String get() = if (isOn) {
        getResourceString(R.string.deuterocanonical)
    } else {
        getResourceString(R.string.bible)
    }

    override val canShow: Boolean get() = searchControl.currentDocumentContainsNonScripture()
}
