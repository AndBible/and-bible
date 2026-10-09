package net.bible.android.platform

import android.content.Intent
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.base.AndBibleActivity
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryPlatform
import net.bible.service.history.IntentHistoryItem
import net.bible.sharedcore.reading.ReadingViewVisibility

/** Android side of [HistoryPlatform]: the Activity / Intent decisions of the history list. */
class AndroidHistoryPlatform : HistoryPlatform {
    override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? {
        if (screenToken != null) {
            val intent = screenToken as Intent
            return IntentHistoryItem(intent.getStringExtra("description") ?: "-", intent, window)
        }
        val currentActivity = CurrentActivityHolder.currentActivity
        if (currentActivity is AndBibleActivity && currentActivity.isIntegrateWithHistoryManager) {
            return IntentHistoryItem(currentActivity.title, currentActivity.intentForHistoryList, window)
        }
        return null
    }

    override fun isOnReadingScreen(): Boolean = ReadingViewVisibility.isVisible

    override fun leaveCurrentScreen() {
        CurrentActivityHolder.currentActivity?.leaveCurrentScreen()
    }
}
