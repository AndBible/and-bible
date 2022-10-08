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

package net.bible.android.view.activity.readingplan.actionbar

import net.bible.android.control.ApplicationScope
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton

import javax.inject.Inject

/**
 * Button to stop Speak when viewing Reading Plan.  Visible when speaking.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class ReadingPlanStopActionBarButton @Inject constructor() : SpeakStopActionBarButton() {

    /**
     * do not show if nothing is being said.  If speaking then allow pause and vice-versa
     */
    public override val canShow: Boolean get() = super.canShow && isSpeakMode
}
