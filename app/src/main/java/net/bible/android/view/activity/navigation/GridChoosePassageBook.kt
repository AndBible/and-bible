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

package net.bible.android.view.activity.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View.OnClickListener

import net.bible.android.activity.R
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.navigation.biblebookactionbar.BibleBookActionBarManager
import net.bible.android.view.util.buttongrid.ButtonGrid
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener
import net.bible.service.common.CommonUtils

import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

import java.util.ArrayList

import javax.inject.Inject

/**
 * Choose a bible book e.g. Psalms
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class GridChoosePassageBook : CustomTitlebarActivityBase(R.menu.choose_passage_book_menu), OnButtonGridActionListener {

    private lateinit var buttonGrid: ButtonGrid

    private var isCurrentlyShowingScripture = true

    @Inject lateinit var navigationControl: NavigationControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    @Inject lateinit var bibleBookActionBarManager: BibleBookActionBarManager

    private// this is used for preview
    val bibleBookButtonInfo: List<ButtonInfo>
        get() {
            val isShortBookNamesAvailable = isShortBookNames
            val currentBibleBook = KeyUtil.getVerse(activeWindowPageManagerProvider.activeWindowPageManager.currentBible.key).book

            val bibleBookList = navigationControl.getBibleBooks(isCurrentlyShowingScripture)
            val keys = ArrayList<ButtonInfo>(bibleBookList.size)
            for (book in bibleBookList) {
                val buttonInfo = ButtonInfo()
                try {
                    buttonInfo.id = book.ordinal
                    buttonInfo.name = getShortBookName(book, isShortBookNamesAvailable)
                    buttonInfo.textColor = getBookTextColor(book.ordinal)
                    buttonInfo.highlight = book == currentBibleBook
                } catch (nsve: NoSuchVerseException) {
                    buttonInfo.name = "ERR"
                }

                keys.add(buttonInfo)
            }
            return keys
        }

    private// should never get here
    val isShortBookNames: Boolean
        get() {
            return try {
                versification.getShortName(BibleBook.GEN) != versification.getLongName(BibleBook.GEN)
            } catch (nsve: Exception) {
                Log.e(TAG, "No such bible book no: 1", nsve)
                false
            }

        }

    private val versification: Versification
        get() = navigationControl.versification

    /**
     * Handle scripture/Appendix toggle
     */
    private val scriptureToggleClickListener = OnClickListener {
        isCurrentlyShowingScripture = !isCurrentlyShowingScripture

        buttonGrid.clear()
        buttonGrid.addButtons(bibleBookButtonInfo)

        bibleBookActionBarManager.setScriptureShown(isCurrentlyShowingScripture)
    }

    /**
     * Handle scripture/Appendix toggle
     */
    private val sortOrderClickListener = OnClickListener {
        navigationControl.changeBibleBookSortOrder()

        buttonGrid.clear()
        buttonGrid.addButtons(bibleBookButtonInfo)
    }

    private var navigateToVerse: Boolean = false

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        // background goes white in some circumstances if theme changes so prevent theme change
        setAllowThemeChange(false)
        super.onCreate(savedInstanceState)

        buildActivityComponent().inject(this)

        val customTitle = intent?.extras?.getCharSequence("title")
        if(customTitle != null)
            title = customTitle

        val workspaceName = SharedActivityState.currentWorkspaceName
        title = "${title} (${workspaceName})"

        val navigateToVerseDefault = CommonUtils.sharedPreferences.getBoolean("navigate_to_verse_pref", false)
        navigateToVerse = intent?.extras?.getBoolean("navigateToVerse", navigateToVerseDefault)?:navigateToVerseDefault

        bibleBookActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener)
        bibleBookActionBarManager.sortButton.registerClickListener(sortOrderClickListener)

        setActionBarManager(bibleBookActionBarManager)

        isCurrentlyShowingScripture = navigationControl.isCurrentDefaultScripture
        bibleBookActionBarManager.setScriptureShown(isCurrentlyShowingScripture)

        buttonGrid = ButtonGrid(this)

        buttonGrid.setOnButtonGridActionListener(this)

        buttonGrid.addButtons(bibleBookButtonInfo)

        setContentView(buttonGrid)
    }

    override fun buttonPressed(buttonInfo: ButtonInfo) {
        Log.d(TAG, "Book:" + buttonInfo.id + " " + buttonInfo.name)
        bookSelected(buttonInfo.id)
    }

    private fun bookSelected(bibleBookNo: Int) {
        Log.d(TAG, "Book selected:$bibleBookNo")
        try {
            val book = BibleBook.values()[bibleBookNo]
            val v11n = versification
            // if there is only 1 chapter then no need to select chapter, but may need to select verse still
            if (!navigationControl.hasChapters(book)) {
                if (!navigateToVerse) {
                    val verse = Verse(v11n, book, 1, 1)

                    val resultIntent = Intent(this, GridChoosePassageBook::class.java)
                    resultIntent.putExtra("verse", verse.osisID)
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                    return
                } else {
                    // select verse (only 1 chapter)
                    val myIntent = Intent(this, GridChoosePassageVerse::class.java)
                    myIntent.putExtra(GridChoosePassageBook.BOOK_NO, bibleBookNo)
                    myIntent.putExtra(GridChoosePassageBook.CHAPTER_NO, 1)
                    myIntent.putExtra("navigateToVerse", navigateToVerse)
                    startActivityForResult(myIntent, 1)
                }
            } else {
                // select chapter
                val myIntent = Intent(this, GridChoosePassageChapter::class.java)
                myIntent.putExtra(GridChoosePassageBook.BOOK_NO, bibleBookNo)
                myIntent.putExtra("navigateToVerse", navigateToVerse)
                startActivityForResult(myIntent, bibleBookNo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "error on select of bible book", e)
        }

    }

    @SuppressLint("MissingSuperCall")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    @Throws(NoSuchVerseException::class)
    private fun getShortBookName(book: BibleBook, isShortBookNamesAvailable: Boolean): String {
        // shortened names exist so use them
        if (isShortBookNamesAvailable) {
            return versification.getShortName(book)
        }

        // getShortName will return the long name in place of the short name
        val bookName = versification.getLongName(book)

        // so now we shorten the name programatically
        val shortenedName = StringBuilder(4)
        var i = 0
        while (shortenedName.length < 4 && i < bookName.length) {
            val ch = bookName[i]
            if (ch != ' ' && ch != '.') {
                shortenedName.append(ch)
            }
            i++
        }

        return shortenedName.toString()
    }

    private fun getBookTextColor(bookNo: Int): Int {
        // colour and grouping taken from http://en.wikipedia.org/wiki/Books_of_the_Bible
        return when {
            bookNo <= BibleBook.DEUT.ordinal -> // Pentateuch - books of Moses
                PENTATEUCH_COLOR
            bookNo <= BibleBook.ESTH.ordinal -> // History
                HISTORY_COLOR
            bookNo <= BibleBook.SONG.ordinal -> // Wisdom
                WISDOM_COLOR
            bookNo <= BibleBook.DAN.ordinal -> // Major prophets
                MAJOR_PROPHETS_COLOR
            bookNo <= BibleBook.MAL.ordinal -> // Minor prophets
                MINOR_PROPHETS_COLOR
            bookNo <= BibleBook.JOHN.ordinal -> // Gospels
                GOSPEL_COLOR
            bookNo <= BibleBook.ACTS.ordinal -> // Acts
                ACTS_COLOR
            bookNo <= BibleBook.PHLM.ordinal -> // Pauline epistles
                PAULINE_COLOR
            bookNo <= BibleBook.JUDE.ordinal -> // General epistles
                GENERAL_EPISTLES_COLOR
            bookNo <= BibleBook.JUDE.ordinal -> // Revelation
                REVELATION_COLOR
            else -> OTHER_COLOR
        }
    }

    companion object {

        const val BOOK_NO = "BOOK_NO"
        const val CHAPTER_NO = "CHAPTER_NO"

        // colour and grouping taken from http://en.wikipedia.org/wiki/Books_of_the_Bible
        private val PENTATEUCH_COLOR = Color.rgb(0xCC, 0xCC, 0xFE)
        private val HISTORY_COLOR = Color.rgb(0xFE, 0xCC, 0x9B)
        private val WISDOM_COLOR = Color.rgb(0x99, 0xFF, 0x99)
        private val MAJOR_PROPHETS_COLOR = Color.rgb(0xFF, 0x99, 0xFF)
        private val MINOR_PROPHETS_COLOR = Color.rgb(0xFF, 0xFE, 0xCD)
        private val GOSPEL_COLOR = Color.rgb(0xFF, 0x97, 0x03)
        private val ACTS_COLOR = Color.rgb(0x00, 0x99, 0xFF)
        private val PAULINE_COLOR = Color.rgb(0xFF, 0xFF, 0x31)
        private val GENERAL_EPISTLES_COLOR = Color.rgb(0x67, 0xCC, 0x66) // changed 99 to CC to make a little clearer on dark background
        private val REVELATION_COLOR = Color.rgb(0xFE, 0x33, 0xFF)
        private val OTHER_COLOR = ACTS_COLOR

        private const val TAG = "GridChoosePassageBook"
    }
}
