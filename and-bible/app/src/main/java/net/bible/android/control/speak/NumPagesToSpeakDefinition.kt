package net.bible.android.control.speak

import net.bible.android.BibleApplication

/**
 * Support the Number of pages to Speak prompt
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * *
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
class NumPagesToSpeakDefinition(var numPages: Int, private val resourceId: Int, private val isPlural: Boolean, val radioButtonId: Int) {

    fun getPrompt() =
            if (isPlural) {
                BibleApplication.getApplication().resources.getQuantityString(resourceId, numPages, numPages)
            } else {
                BibleApplication.getApplication().resources.getString(resourceId)
            }
}
