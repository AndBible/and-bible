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
package net.bible.android.view.activity.download

import android.content.Context
import android.util.AttributeSet
import android.view.View
import kotlinx.android.synthetic.main.document_download_list_item.view.*
import net.bible.android.activity.R
import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent
import net.bible.android.view.util.widget.TwoLineListItem
import org.crosswire.jsword.book.Book

/** Add an image to the normal 2 line list item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentDownloadListItem : TwoLineListItem {
    /** document being shown  */
    var document: Book? = null
        set(document: Book?) {
            field = document
            ensureRegisteredForDownloadEvents()
        }

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet?,
                defStyle: Int) : super(context, attrs, defStyle) {
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        ensureRegisteredForDownloadEvents()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // View is now detached, and about to be destroyed.
        // de-register from EventBus
        ABEventBus.getDefault().unregister(this)
    }

    /**
     * Download progress event
     */
    fun onEventMainThread(event: DocumentDownloadEvent) {
        if (document != null && event.initials == document!!.initials) {
            updateControlState(event.documentStatus)
        }
    }

    fun updateControlState(documentStatus: DocumentStatus) {
        if (icon != null && progressBar != null) {
            when (documentStatus.documentInstallStatus) {
                DocumentInstallStatus.INSTALLED -> {
                    icon.setImageResource(R.drawable.ic_check_green_24dp)
                    progressBar.visibility = View.INVISIBLE
                }
                DocumentInstallStatus.NOT_INSTALLED -> {
                    icon.setImageDrawable(null)
                    progressBar.visibility = View.INVISIBLE
                }
                DocumentInstallStatus.BEING_INSTALLED -> {
                    icon.setImageResource(R.drawable.ic_arrow_downward_green_24dp)
                    setProgressPercent(documentStatus.percentDone)
                    progressBar.visibility = View.VISIBLE
                }
                DocumentInstallStatus.UPGRADE_AVAILABLE -> {
                    icon.setImageResource(R.drawable.ic_arrow_upward_amber_24dp)
                    progressBar.visibility = View.INVISIBLE
                }
                DocumentInstallStatus.ERROR_DOWNLOADING -> {
                    icon.setImageResource(R.drawable.ic_warning_red_24dp)
                    progressBar.visibility = View.INVISIBLE
                }
            }
        }
    }

    /**
     * Should not need to check the initials but other items were being updated and I don't know why
     */
    private fun setProgressPercent(percentDone: Int) {
        progressBar.progress = percentDone
    }

    /**
     * Items are detached more often than inflated so always have to check the item is registered for download events.
     * https://code.google.com/p/android/issues/detail?id=65617
     */
    private fun ensureRegisteredForDownloadEvents() {
        ABEventBus.getDefault().safelyRegister(this)
    }
}
