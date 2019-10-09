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

package net.bible.android.view.util.widget

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.speak_transport_widget.view.*
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.speak.SpeakSettingsChangedEvent
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.speak.GeneralSpeakActivity
import net.bible.service.common.CommonUtils.buildActivityComponent
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import org.crosswire.jsword.book.BookCategory
import javax.inject.Inject

class SpeakTransportWidget(context: Context, attributeSet: AttributeSet): LinearLayout(context, attributeSet) {
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var windowControl: WindowControl
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.speak_transport_widget, this, true)
        ABEventBus.getDefault().register(this)

        buildActivityComponent().inject(this)

        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
        speakPauseButton.setOnClickListener { onButtonClick(it) }
        prevButton.setOnClickListener { onButtonClick(it) }
        nextButton.setOnClickListener { onButtonClick(it) }
        stopButton.setOnClickListener { onButtonClick(it) }
        configButton.setOnClickListener {
            val isBible = windowControl.activeWindowPageManager.currentPage.bookCategory == BookCategory.BIBLE
            val intent = Intent(context, if (isBible) BibleSpeakActivity::class.java else GeneralSpeakActivity::class.java)
            context.startActivity(intent)
        }
        rewindButton.setOnClickListener { onButtonClick(it) }
        forwardButton.setOnClickListener { onButtonClick(it) }
        bookmarkButton.setOnClickListener { onBookmarkButtonClick() }
        bookmarkButton.visibility = if(SpeakSettings.load().autoBookmark) View.VISIBLE else View.GONE
        if(context.theme.obtainStyledAttributes(attributeSet, R.styleable.SpeakTransportWidget, 0, 0)
                        .getBoolean(R.styleable.SpeakTransportWidget_hideStatus, false)) {
            statusText.visibility = View.GONE
        }
        if(!context.theme.obtainStyledAttributes(attributeSet, R.styleable.SpeakTransportWidget, 0, 0)
                        .getBoolean(R.styleable.SpeakTransportWidget_showConfig, false)) {
            configButton.visibility = View.GONE
        }

        resetView(SpeakSettings.load())
    }


    private fun onButtonClick(button: View) {
        try {
            when (button) {
                prevButton -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                nextButton -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                rewindButton -> speakControl.rewind()
                stopButton -> speakControl.stop()
                speakPauseButton ->
                    when {
                        speakControl.isPaused -> speakControl.continueAfterPause()
                        speakControl.isSpeaking -> speakControl.pause()
                        else -> {
                            speakControl.speakBible()
                            if(SpeakSettings.load().synchronize) {
                                context.startActivity(Intent(context, MainBibleActivity::class.java))
                            }
                        }
                    }
                forwardButton -> speakControl.forward()
            }
        } catch (e: Exception) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
            Log.e(TAG, "Error: ", e)
        }
    }

    private fun onBookmarkButtonClick() {
        val bookmarkTitles = ArrayList<String>()
        val bookmarkDtos = ArrayList<BookmarkDto>()
        val labelDto = bookmarkControl.orCreateSpeakLabel
        for (b in bookmarkControl.getBookmarksWithLabel(labelDto).sortedWith(
                Comparator<BookmarkDto> { o1, o2 -> o1.verseRange.start.compareTo(o2.verseRange.start) })) {

            bookmarkTitles.add("${b.verseRange.start.name} (${b.playbackSettings?.bookId?:"?"})")
            bookmarkDtos.add(b)
        }

        val adapter = ArrayAdapter<String>(context, android.R.layout.select_dialog_item, bookmarkTitles)
        AlertDialog.Builder(context)
                .setTitle(R.string.speak_bookmarks_menu_title)
                .setAdapter(adapter) { _, which ->
                    speakControl.speakFromBookmark(bookmarkDtos[which])
                    if(SpeakSettings.load().synchronize) {
                        context.startActivity(Intent(context, MainBibleActivity::class.java))
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun resetView(speakSettings: SpeakSettings? = null) {
        speakPauseButton.setImageResource(
                if(speakControl.isSpeaking)
                    R.drawable.ic_pause_black_24dp
                else
                    R.drawable.ic_play_arrow_black_24dp
        )
        if(speakSettings != null) {
            bookmarkButton.visibility = if (speakSettings.autoBookmark) View.VISIBLE else View.GONE
        }
    }

    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        resetView(ev.speakSettings)
    }

    fun onEventMainThread(ev: SpeakEvent) {
        resetView()
    }

    fun onEventMainThread(ev: SpeakProgressEvent) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
    }

    companion object {
        const val TAG = "SpeakTransportWidget"
    }
}
