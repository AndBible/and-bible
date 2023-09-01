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

package net.bible.android.control.page.window

import android.util.Log
import debounce
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.ScrollSecondaryWindowEvent
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.DocumentCategory
import net.bible.service.device.ScreenSettings

import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import kotlin.math.max

class WindowSync(private val windowRepository: WindowRepository) {
    private var lastSyncWasInNightMode: Boolean = false
    var lastForceSyncAll: Long = System.currentTimeMillis()
        private set

    fun setResyncRequired() {
        lastForceSyncAll = System.currentTimeMillis()
    }

    fun reloadAllWindows(force: Boolean = false) {
        if(force)
            setResyncRequired()

        for (window in windowRepository.visibleWindows) {
            window.updateTextIfNeeded()
        }
        lastSyncWasInNightMode = ScreenSettings.nightMode
    }

    /** Synchronise the inactive key and inactive screen with the active key and screen if required */
    fun synchronizeWindows(sourceWindow_: Window? = null, noDelay: Boolean = false) {
        Log.i(TAG, "synchronizeWindows ${sourceWindow_}...")

        // if maximized mode and current active window is not in sync, then get previous window that was in sync
        val lastSyncWindow = windowRepository.lastSyncWindow
        val sourceWindow: Window =
            sourceWindow_ ?: if (lastSyncWindow != null && !windowRepository.activeWindow.isSynchronised)
                lastSyncWindow
            else windowRepository.activeWindow

        if(noDelay)
            immediateSynchronizeWindows(sourceWindow)
        else
            delayedSynchronizeWindows(sourceWindow)
    }

    val scope get() = windowRepository.scope
    private val delayedSynchronizeWindows: (sourceWindow: Window) -> Unit
        = debounce(200, scope) { sourceWindow -> immediateSynchronizeWindows(sourceWindow)}

    private fun immediateSynchronizeWindows(sourceWindow: Window) = synchronized(this) {
        Log.i(TAG, "...delayedSynchronizeWindows $sourceWindow")

        val activePage = sourceWindow.pageManager.currentPage
        var targetActiveWindowKey = activePage.singleKey

        val inactiveWindowList = windowRepository.getWindowsToSynchronise(sourceWindow)

        if (lastSyncWasInNightMode != ScreenSettings.nightMode) {
            setResyncRequired()
        }

        if (isSynchronizableVerseKey(activePage) && sourceWindow.isSynchronised) {
            for (inactiveWindow in inactiveWindowList.filter { it.syncGroup == sourceWindow.syncGroup }) {
                val inactivePage = inactiveWindow.pageManager.currentPage
                val inactiveWindowKey = inactivePage.singleKey
                var inactiveUpdated = false

                if (inactiveWindow.isSynchronised) {
                    // inactive screen may not be displayed (e.g. if viewing a dict) but if switched to the key must be correct
                    // Only Bible and cmtry are synch'd and they share a Verse key
                    updateInactiveBibleKey(inactiveWindow, targetActiveWindowKey)

                    if (isSynchronizableVerseKey(inactivePage) && inactiveWindow.isVisible) {
                        // re-get as it may have been mapped to the correct v11n
                        // this looks odd but the inactivePage key has already been updated to the activeScreenKey
                        targetActiveWindowKey = inactivePage.singleKey

                        // prevent infinite loop as each screen update causes a synchronise by comparing last key
                        // only update pages if empty or synchronised
                        if (inactiveWindow.lastUpdated < lastForceSyncAll
                            || targetActiveWindowKey != inactiveWindowKey
                        ) {
                            updateInactiveWindow(
                                inactiveWindow,
                                inactivePage,
                                targetActiveWindowKey,
                                inactiveWindowKey
                            )
                            inactiveUpdated = true
                        }
                    }
                }

                // force inactive screen to display something otherwise it may be initially blank
                // or if nightMode has changed then force an update
                if (!inactiveUpdated && inactiveWindow.lastUpdated < lastForceSyncAll) {
                    // force an update of the inactive page to prevent blank screen
                    updateInactiveWindow(inactiveWindow, inactivePage, inactiveWindowKey, inactiveWindowKey)
                }

            }
        }
    }

    /** Only call if screens are synchronised.  Update synch'd keys even if inactive page not
     * shown so if it is shown then it is correct
     */
    private fun updateInactiveBibleKey(inactiveWindow: Window, activeWindowKey: Key?) {
        inactiveWindow.pageManager.currentBible.doSetKey(activeWindowKey)
    }

    /** refresh/synch inactive screen if required
     */
    private fun updateInactiveWindow(inactiveWindow: Window, inactivePage: CurrentPage?, targetKey: Key?, inactiveWindowKey: Key?) {
        // standard null checks
        if (targetKey != null && inactivePage != null) {
            // Not just bibles and commentaries get this far so NOT always fine to convert key to verse
            val targetVerse = if (targetKey is Verse) KeyUtil.getVerse(targetKey) else null
            val bookCategory = inactivePage.documentCategory
            val isGeneralBook = DocumentCategory.GENERAL_BOOK == bookCategory
            val isBible = DocumentCategory.BIBLE == bookCategory
            val isMyNotes = DocumentCategory.MYNOTE == bookCategory
            val isCommentary = DocumentCategory.COMMENTARY == bookCategory
            val isUnsynchronizedCommentary = !inactiveWindow.isSynchronised && isCommentary
            val isSynchronizedCommentary = inactiveWindow.isSynchronised && isCommentary
            val currentVerse = if (inactiveWindowKey is Verse) {KeyUtil.getVerse(inactiveWindowKey)} else null

            // update inactive screens as smoothly as possible i.e. just jump/scroll if verse is on current page
            if(lastForceSyncAll > inactiveWindow.lastUpdated) {
                inactiveWindow.loadText()

            } else {
                if ((isBible||isMyNotes) && currentVerse != null && targetVerse != null) {
                    if(targetVerse.book == currentVerse.book && inactiveWindow.hasChapterLoaded(targetVerse.chapter)) {
                        ABEventBus
                            .post(ScrollSecondaryWindowEvent(inactiveWindow, targetVerse))
                    } else if(targetVerse != currentVerse) {
                        inactiveWindow.loadText()
                    }
                } else if ((isGeneralBook || isUnsynchronizedCommentary) && inactiveWindow.initialized) {
                    //UpdateInactiveScreenTextTask().execute(inactiveWindow)
                    // Do not update! Updating would reset page position.
                } else if ( isSynchronizedCommentary && targetVerse != currentVerse ) {
                    // synchronized commentary
                    inactiveWindow.loadText()
                }
            }
        }
    }

    /** Only Bibles and commentaries have the same sort of key and can be synchronized
     */
    private fun isSynchronizableVerseKey(page: CurrentPage?): Boolean {
        var result = false
        // various null checks then the test
        if (page != null) {
            val document = page.currentDocument
            if (document != null) {
                val bookCategory = document.bookCategory
                // The important part
                result = BookCategory.BIBLE == bookCategory || BookCategory.COMMENTARY == bookCategory
            }
        }
        return result
    }

    companion object {
        const val TAG = "WindowSync"
    }
}
