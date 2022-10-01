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
package net.bible.android.control

import android.util.Log
import kotlin.jvm.JvmOverloads
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.passage.PassageChangeStartedEvent
import net.bible.android.control.event.passage.PassageChangedEvent
import net.bible.android.control.page.window.Window

/** when a bible passage is changed there are lots o things to update and they should be done in a helpful order
 * This helps to control screen updates after a passage change
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
private const val TAG = "PassageChangeMediator"
object PassageChangeMediator {
    private var mBibleContentManager: BibleContentManager? = null

    /** first time we know a page or doc will imminently change
     */
    @JvmOverloads
    fun onBeforeCurrentPageChanged(updateHistory: Boolean = true) {
        ABEventBus.getDefault().post(BeforeCurrentPageChangeEvent(updateHistory))
    }

    /** the document has changed so ask the view to refresh itself
     */
    @JvmOverloads
    fun onCurrentPageChanged(window: Window? = null) {
        if (mBibleContentManager != null) {
            mBibleContentManager!!.updateText(window)
        } else {
            Log.w(TAG, "BibleContentManager not yet registered")
        }
        ABEventBus.getDefault().post(CurrentVerseChangedEvent(window))
    }

    /** this is triggered on scroll
     */
    fun onCurrentVerseChanged(window: Window) {
        ABEventBus.getDefault().post(CurrentVerseChangedEvent(window))
    }

    /** The thread which fetches the new page html has started
     */
    fun contentChangeStarted() {
        ABEventBus.getDefault().post(PassageChangeStartedEvent())
    }

    /** finished fetching html so should hide hourglass
     */
    fun contentChangeFinished() {
        ABEventBus.getDefault().post(PassageChangedEvent())
    }

    fun setBibleContentManager(bibleContentManager: BibleContentManager?) {
        mBibleContentManager = bibleContentManager
    }
}
