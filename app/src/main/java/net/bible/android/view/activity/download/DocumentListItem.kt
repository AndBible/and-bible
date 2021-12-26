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
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import net.bible.android.activity.R
import net.bible.android.activity.databinding.DocumentListItemBinding
import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.download.repo
import net.bible.android.control.download.repoIdentity
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent
import net.bible.android.view.activity.base.RecommendedDocuments
import net.bible.service.common.CommonUtils
import net.bible.service.download.DownloadManager
import net.bible.service.download.isPseudoBook
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBookMetaData

val Book.imageResource: Int
    get() = when(bookCategory) {
        BookCategory.BIBLE -> R.drawable.ic_bible_24dp
        BookCategory.COMMENTARY -> R.drawable.ic_commentary
        BookCategory.DICTIONARY -> R.drawable.ic_dictionary_24dp
        BookCategory.MAPS -> R.drawable.ic_map_black_24dp
        BookCategory.GENERAL_BOOK -> R.drawable.ic_book_24dp
        BookCategory.AND_BIBLE -> R.drawable.ic_addon_24dp
        else -> R.drawable.ic_book_24dp
    }

fun Book.isRecommended(recommendedDocuments: RecommendedDocuments?): Boolean =
    recommendedDocuments?.getForBookCategory(bookCategory)?.get(language.code)?.find {
        if(it.contains("::")) {
            val (initials, repository) = it.split("::")
            initials == this.initials && repository == this.repo
        } else {
            it == initials
        }
    } != null

/** Add an image to the normal 2 line list item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentListItem(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    lateinit var binding: DocumentListItemBinding // Injected from adapter!

    var recommendedDocuments: RecommendedDocuments? = null

    lateinit var document: Book
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureRegisteredForDownloadEvents()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ABEventBus.getDefault().unregister(this)
    }

    fun onEventMainThread(event: DocumentDownloadEvent) {
        if (event.id == document.repoIdentity) {
            updateControlState(event.documentStatus)
        }
    }

    fun setIcons(downloadScreen: Boolean = false) = binding.apply {
        val docImage = document.imageResource
        documentTypeIcon.setImageResource(docImage)

        if(document.isPseudoBook) {
            item.setBackgroundColor(CommonUtils.getResourceColor(R.color.disabled_background))
        } else {
            item.setBackgroundResource(R.drawable.selectable_background)
        }

        val isRecommended = document.isRecommended(recommendedDocuments)
        recommendedIcon.visibility = if(isRecommended) View.VISIBLE else View.INVISIBLE
        lockedIcon.visibility = if(document.isEnciphered) View.VISIBLE else View.INVISIBLE
        lockedIcon.setImageResource(if(document.isLocked) R.drawable.ic_baseline_lock_24 else  R.drawable.ic_baseline_lock_open_24)
        lockedIcon.setColorFilter(if(document.isLocked) Color.rgb(255, 0, 0) else Color.rgb(0, 255, 0))
        recommendedString.visibility = if(isRecommended) View.VISIBLE else View.GONE
        documentLanguage.text = document.language.name
        documentSource.text = document.getProperty(DownloadManager.REPOSITORY_KEY)

        val moduleSize = document.bookMetaData.getProperty(SwordBookMetaData.KEY_INSTALL_SIZE)

        if(downloadScreen && moduleSize != null) {
            val moduleSizeMb = (moduleSize.toDoubleOrNull() ?: 0.0) / 1e6
            installSize.text = context.getString(R.string.module_size_megabytes).format(moduleSizeMb)
        } else {
            installSize.visibility = View.GONE
        }
    }

    fun updateControlState(documentStatus: DocumentStatus): Nothing? = binding.run {
        undoButton.visibility = View.GONE
        progressBar.visibility = View.INVISIBLE
        aboutButton.visibility = View.VISIBLE
        when (documentStatus.documentInstallStatus) {
            DocumentInstallStatus.INSTALLED -> {
                downloadStatusIcon.setImageResource(R.drawable.ic_check_green_24dp)
            }
            DocumentInstallStatus.NOT_INSTALLED -> {
                downloadStatusIcon.setImageDrawable(null)
            }
            DocumentInstallStatus.BEING_INSTALLED -> {
                downloadStatusIcon.setImageResource(R.drawable.ic_arrow_downward_green_24dp)
                setProgressPercent(documentStatus.percentDone)
                progressBar.visibility = View.VISIBLE
                undoButton.visibility = View.VISIBLE
                aboutButton.visibility = View.GONE
            }
            DocumentInstallStatus.UPGRADE_AVAILABLE -> {
                downloadStatusIcon.setImageResource(R.drawable.ic_arrow_upward_amber_24dp)
            }
            DocumentInstallStatus.ERROR_DOWNLOADING -> {
                downloadStatusIcon.setImageResource(R.drawable.ic_warning_red_24dp)
            }
            DocumentInstallStatus.INSTALL_CANCELLED -> {
                val newStatus = DocumentStatus(documentStatus.id, if(document.isInstalled) DocumentInstallStatus.INSTALLED else DocumentInstallStatus.NOT_INSTALLED, 0)
                updateControlState(newStatus)
            }
        }
        null
    }

    /**
     * Should not need to check the initials but other items were being updated and I don't know why
     */
    private fun setProgressPercent(percentDone: Int) {
        binding.progressBar.progress = percentDone
    }

    /**
     * Items are detached more often than inflated so always have to check the item is registered for download events.
     * https://code.google.com/p/android/issues/detail?id=65617
     */
    private fun ensureRegisteredForDownloadEvents() {
        ABEventBus.getDefault().safelyRegister(this)
    }
}
