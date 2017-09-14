package net.bible.android.view.activity.page

import android.util.Log

import net.bible.android.control.page.CurrentPageManager
import net.bible.android.view.activity.base.Callback

import org.apache.commons.lang3.StringEscapeUtils
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.doAsync

/**
 * Get next or previous page for insertion at the top or bottom of the current webview.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
class BibleInfiniteScrollPopulator(private val bibleViewtextInserter: BibleViewTextInserter, private val currentPageManager: CurrentPageManager) : AnkoLogger {

    fun requestMoreTextAtTop(textId: String, callback: Callback) {
        debug("requestMoreTextAtTop")
        // do in background thread
        doAsync {
            // get page fragment for previous chapter
            var fragment = currentPageManager.currentPage.previousPageFragment
            fragment = StringEscapeUtils.escapeEcmaScript(fragment)
            bibleViewtextInserter.insertTextAtTop(textId, fragment)
            // tell js interface that insert is complete
            callback.okay()
        }
    }

    fun requestMoreTextAtEnd(textId: String) {
        debug("requestMoreTextAtEnd")
        // do in background thread
        doAsync {
            // get page fragment for previous chapter
            var fragment = currentPageManager.currentPage.nextPageFragment
            fragment = StringEscapeUtils.escapeEcmaScript(fragment)
            bibleViewtextInserter.insertTextAtEnd(textId, fragment)
        }
    }
}
