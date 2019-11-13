/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.page.window

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.event.passage.PassageChangedEvent
import net.bible.android.control.event.window.ScrollSecondaryWindowEvent
import net.bible.android.control.event.window.UpdateSecondaryWindowEvent
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.UpdateTextTask
import net.bible.service.device.ScreenSettings

import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.Versification

class WindowSync(private val windowRepository: WindowRepository) {

    private var isFirstTimeInit = true
    private var resynchRequired = false
    private var screenPreferencesChanged = false

    private var lastSynchdInactiveWindowKey: Key? = null
    private var lastSynchWasInNightMode: Boolean = false

    init {
        ABEventBus.getDefault().register(this)
    }

    fun onEvent(event: PassageChangedEvent) {
        synchronizeScreens()
    }

    /**
     * Save/restore dynamic state that is not automatically saved as Preferences
     */
    fun onEvent(event: AppToBackgroundEvent) {
        // ensure nonactive screen is initialised when returning from background
        lastSynchdInactiveWindowKey = null
    }

    fun synchronizeAllScreens() {
        for (window in windowRepository.visibleWindows) {
            UpdateInactiveScreenTextTask().execute(window)
        }
    }

    /** Synchronise the inactive key and inactive screen with the active key and screen if required
     */
    @JvmOverloads
    fun synchronizeScreens(sourceWindow_: Window? = null) {
        val sourceWindow = sourceWindow_?: windowRepository.activeWindow
        val activePage = sourceWindow.pageManager.currentPage
        var targetActiveWindowKey = activePage.singleKey

        val inactiveWindowList = windowRepository.getWindowsToSynchronise(sourceWindow)
        for (inactiveWindow in inactiveWindowList) {
            val inactivePage = inactiveWindow.pageManager.currentPage
            val inactiveWindowKey = inactivePage.singleKey
            var inactiveUpdated = false
            val isTotalRefreshRequired = isFirstTimeInit
                    || lastSynchWasInNightMode != ScreenSettings.isNightMode
                    || screenPreferencesChanged || resynchRequired

            if (isSynchronizableVerseKey(activePage) && sourceWindow.isSynchronised
                    && inactiveWindow.isSynchronised) {
                // inactive screen may not be displayed (e.g. if viewing a dict) but if switched to the key must be correct
                // Only Bible and cmtry are synch'd and they share a Verse key
                updateInactiveBibleKey(inactiveWindow, targetActiveWindowKey)

                if (isSynchronizableVerseKey(inactivePage) && inactiveWindow.isVisible) {
                    // re-get as it may have been mapped to the correct v11n
                    // this looks odd but the inactivePage key has already been updated to the activeScreenKey
                    targetActiveWindowKey = inactivePage.singleKey

                    // prevent infinite loop as each screen update causes a synchronise by comparing last key
                    // only update pages if empty or synchronised
                    if (isFirstTimeInit || resynchRequired ||
                            targetActiveWindowKey != lastSynchdInactiveWindowKey) {
                        updateInactiveWindow(inactiveWindow, inactivePage, targetActiveWindowKey,
                                lastSynchdInactiveWindowKey, isTotalRefreshRequired)
                        inactiveUpdated = true
                    }
                }
            }

            // force inactive screen to display something otherwise it may be initially blank
            // or if nightMode has changed then force an update
            if (!inactiveUpdated && isTotalRefreshRequired) {
                // force an update of the inactive page to prevent blank screen
                updateInactiveWindow(inactiveWindow, inactivePage, inactiveWindowKey, inactiveWindowKey,
                        isTotalRefreshRequired)
                inactiveUpdated = true
            }

        }
        lastSynchdInactiveWindowKey = targetActiveWindowKey
        lastSynchWasInNightMode = ScreenSettings.isNightMode
        screenPreferencesChanged = false
        resynchRequired = false
        isFirstTimeInit = false
    }

    /** Only call if screens are synchronised.  Update synch'd keys even if inactive page not
     * shown so if it is shown then it is correct
     */
    private fun updateInactiveBibleKey(inactiveWindow: Window, activeWindowKey: Key) {
        inactiveWindow.pageManager.currentBible.doSetKey(activeWindowKey)
    }

    /** refresh/synch inactive screen if required
     */
    private fun updateInactiveWindow(inactiveWindow: Window, inactivePage: CurrentPage?,
                                     targetKey: Key?, inactiveWindowKey: Key?, forceRefresh: Boolean) {
        // standard null checks
        if (targetKey != null && inactivePage != null) {
            // Not just bibles and commentaries get this far so NOT always fine to convert key to verse
            var targetVerse: Verse? = null
            var targetV11n: Versification? = null
            if (targetKey is Verse) {
                targetVerse = KeyUtil.getVerse(targetKey)
                targetV11n = targetVerse!!.versification
            }

            var currentVerse: Verse? = null
            val isGeneralBook = BookCategory.GENERAL_BOOK == inactivePage.currentDocument.bookCategory
            val isUnsynchronizedCommentary = !inactiveWindow.isSynchronised
                    && BookCategory.COMMENTARY == inactivePage.currentDocument.bookCategory

            if (inactiveWindowKey != null && inactiveWindowKey is Verse) {
                currentVerse = KeyUtil.getVerse(inactiveWindowKey)
            }

            // update inactive screens as smoothly as possible i.e. just jump/scroll if verse is on current page
            if (!forceRefresh &&
                    BookCategory.BIBLE == inactivePage.currentDocument.bookCategory &&
                    currentVerse != null && targetVerse != null && targetV11n!!.isSameChapter(targetVerse, currentVerse)) {
                ABEventBus.getDefault().post(
                        ScrollSecondaryWindowEvent(inactiveWindow, ChapterVerse.fromVerse(targetVerse))
                )
            } else if ((isGeneralBook || isUnsynchronizedCommentary) && inactiveWindow.initialized) {
                //UpdateInactiveScreenTextTask().execute(inactiveWindow)
                // Do not update! Updating would reset page position.
            } else {
                UpdateInactiveScreenTextTask().execute(inactiveWindow)
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


    fun setResynchRequired(resynchRequired: Boolean) {
        this.resynchRequired = resynchRequired
    }

    fun setScreenPreferencesChanged(screenPreferencesChanged: Boolean) {
        this.screenPreferencesChanged = screenPreferencesChanged
    }
}

private class UpdateInactiveScreenTextTask : UpdateTextTask() {
    /** callback from base class when result is ready  */
    override fun showText(text: String, window: Window, yOffsetRatio: Float) {
        ABEventBus.getDefault().post(UpdateSecondaryWindowEvent(window, text));
    }
}

