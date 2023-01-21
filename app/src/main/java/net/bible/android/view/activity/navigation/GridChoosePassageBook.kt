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


package net.bible.android.view.activity.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem

import net.bible.android.activity.R
import net.bible.android.control.navigation.BibleBookSortOrder
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.util.buttongrid.ButtonGrid
import net.bible.android.view.util.buttongrid.ButtonInfo
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

    private var isCurrentlyShowingScripture = false

    @Inject lateinit var navigationControl: NavigationControl
    @Inject lateinit var windowControl: WindowControl

    data class ExtraBookInfo(val Color: Int, val GroupA: String, val GroupB: String)

    // this is used for preview
    private val bibleBookButtonInfo: List<ButtonInfo>
        get() {
            val isShortBookNamesAvailable = isShortBookNames
            val currentBibleBook = KeyUtil.getVerse(windowControl.activeWindowPageManager.currentBible.key).book

            val bibleBookList = navigationControl.getBibleBooks(isCurrentlyShowingScripture)
            val keys = ArrayList<ButtonInfo>(bibleBookList.size)
            for (book in bibleBookList) {
                val buttonInfo = ButtonInfo()
                try {
                    buttonInfo.id = book.ordinal
                    buttonInfo.name = getShortBookName(book, isShortBookNamesAvailable)
                    buttonInfo.description = versification.getLongName(book)
                    val BookColorAndGroup = getBookColorAndGroup(book.ordinal)
                    buttonInfo.GroupA = BookColorAndGroup.GroupA
                    buttonInfo.GroupB = BookColorAndGroup.GroupB
                    buttonInfo.showLongBookName = buttonGrid.isShowLongBookName
                    buttonInfo.type = ButtonInfo.GridButtonTypes.BOOK
                    if (book == currentBibleBook) {
                        buttonInfo.tintColor = BookColorAndGroup.Color
                        buttonInfo.textColor = Color.DKGRAY
                    } else {
                        buttonInfo.textColor = BookColorAndGroup.Color
                        buttonInfo.tintColor = if (book.ordinal < BibleBook.MATT.ordinal) Color.DKGRAY else NEW_TESTAMENT_TINT
                    }
//                    buttonInfo.highlight = book == currentBibleBook  // Highlighting the button adds an underline which is no longer need and looks a little ugly.
                } catch (nsve: NoSuchVerseException) {
                    buttonInfo.name = "ERR"
                }
                keys.add(buttonInfo)
            }
            return keys
        }

    // should never get here
    private val isShortBookNames: Boolean
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

    private var navigateToVerse: Boolean = false

    // background goes white in some circumstances if theme changes so prevent theme change
    override val allowThemeChange = false

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildActivityComponent().inject(this)

        val customTitle = intent?.extras?.getCharSequence("title")
        if(customTitle != null)
            title = customTitle

        val workspaceName = SharedActivityState.currentWorkspaceName
        title = "$title (${workspaceName})"
        navigateToVerse = intent.getBooleanExtra("navigateToVerse", CommonUtils.settings.getBoolean("navigate_to_verse_pref", false))
        isCurrentlyShowingScripture = intent?.getBooleanExtra("isScripture", false)!!
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        buttonGrid = ButtonGrid(this)
        buttonGrid.isCurrentlyShowingScripture = isCurrentlyShowingScripture
        buttonGrid.setOnButtonGridActionListener(this)
        buttonGrid.isLeftToRightEnabled = CommonUtils.settings.getBoolean(BOOK_GRID_FLOW_PREFS, false)
        buttonGrid.isGroupByCategoryEnabled = CommonUtils.settings.getBoolean(BOOK_GRID_FLOW_PREFS_GROUP_BY_CATEGORY, false)
        buttonGrid.isShowLongBookName = CommonUtils.settings.getBoolean(BOOK_GRID_SHOW_LONG_NAME, false)
        buttonGrid.isAlphaSorted = navigationControl.bibleBookSortOrder == BibleBookSortOrder.ALPHABETICAL
        buttonGrid.addBookButtons(bibleBookButtonInfo)

        setContentView(buttonGrid)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.alphabetical_order_opt).run {
            isChecked = navigationControl.bibleBookSortOrder == BibleBookSortOrder.ALPHABETICAL
            buttonGrid.isAlphaSorted = isChecked
        }

        buttonGrid.isGroupByCategoryEnabled = CommonUtils.settings.getBoolean(BOOK_GRID_FLOW_PREFS_GROUP_BY_CATEGORY, false)
        menu.findItem(R.id.group_by_category).isChecked  = buttonGrid.isGroupByCategoryEnabled

        buttonGrid.isLeftToRightEnabled = CommonUtils.settings.getBoolean(BOOK_GRID_FLOW_PREFS, false)
        menu.findItem(R.id.row_order_opt).isChecked  = buttonGrid.isLeftToRightEnabled

        buttonGrid.isShowLongBookName = CommonUtils.settings.getBoolean(BOOK_GRID_SHOW_LONG_NAME, false)
        menu.findItem(R.id.show_long_book_name).isChecked  = buttonGrid.isShowLongBookName

        val deutToggle = menu.findItem(R.id.deut_toggle)
        deutToggle.setTitle(if(isCurrentlyShowingScripture) R.string.bible else R.string.deuterocanonical)
        deutToggle.isVisible = navigationControl.getBibleBooks(false).isNotEmpty()

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.alphabetical_order_opt -> {
            buttonGrid.isGroupByCategoryEnabled = false
            navigationControl.changeBibleBookSortOrder()
            buttonGrid.clear()
            buttonGrid.isAlphaSorted = !item.isChecked
            buttonGrid.addBookButtons(bibleBookButtonInfo)
            buttonGrid.isGroupByCategoryEnabled = false
            saveOptions()
            invalidateOptionsMenu()
            true
        }
        R.id.row_order_opt -> {
            buttonGrid.isGroupByCategoryEnabled = false
            buttonGrid.toggleLeftToRight()
            item.isChecked = buttonGrid.isLeftToRightEnabled
            buttonGrid.clear()
            buttonGrid.addBookButtons(bibleBookButtonInfo)
            buttonGrid.isGroupByCategoryEnabled = false
            saveOptions()
            invalidateOptionsMenu()
            true
        }
        R.id.group_by_category -> {
            navigationControl.bibleBookSortOrder = BibleBookSortOrder.BIBLE_BOOK
            buttonGrid.isAlphaSorted = false
            buttonGrid.isLeftToRightEnabled = true
            buttonGrid.toggleGroupByCategory()
            item.isChecked = buttonGrid.isGroupByCategoryEnabled
            buttonGrid.clear()
            buttonGrid.addBookButtons(bibleBookButtonInfo)
            saveOptions()
            invalidateOptionsMenu()
            true
        }
        R.id.show_long_book_name -> {
            buttonGrid.toggleShowLongName()
            item.isChecked = buttonGrid.isShowLongBookName
            buttonGrid.clear()
            buttonGrid.addBookButtons(bibleBookButtonInfo)
            saveOptions()
            invalidateOptionsMenu()
            true
        }
        R.id.deut_toggle -> {
            isCurrentlyShowingScripture = !isCurrentlyShowingScripture
            buttonGrid.isCurrentlyShowingScripture = isCurrentlyShowingScripture
            buttonGrid.clear()
            buttonGrid.addBookButtons(bibleBookButtonInfo)
            invalidateOptionsMenu()
            true
        }
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun saveOptions() {
        CommonUtils.settings.setBoolean(BOOK_GRID_FLOW_PREFS, buttonGrid.isLeftToRightEnabled)
        CommonUtils.settings.setBoolean(BOOK_GRID_FLOW_PREFS_GROUP_BY_CATEGORY, buttonGrid.isGroupByCategoryEnabled)
        CommonUtils.settings.setBoolean(BOOK_GRID_SHOW_LONG_NAME, buttonGrid.isShowLongBookName)

    }

    override fun buttonPressed(buttonInfo: ButtonInfo) {
        Log.i(TAG, "Book:" + buttonInfo.id + " " + buttonInfo.name)
        bookSelected(buttonInfo.id)
    }

    private fun bookSelected(bibleBookNo: Int) {
        Log.i(TAG, "Book selected:$bibleBookNo")
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


    companion object {

        const val BOOK_NO = "BOOK_NO"
        const val CHAPTER_NO = "CHAPTER_NO"

        private val NEW_TESTAMENT_TINT = Color.argb(0xFF,0x50,0x50,0x50)

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

        public const val BOOK_GRID_FLOW_PREFS = "book_grid_ltr"
        public const val BOOK_GRID_FLOW_PREFS_GROUP_BY_CATEGORY = "book_grid_group_by_category"
        public const val BOOK_GRID_SHOW_LONG_NAME = "book_grid_show_long_name"
        private const val TAG = "GridChoosePassageBook"

        fun getBookColorAndGroup(bookNo: Int):  ExtraBookInfo {
            // Colour and Grouping taken from http://en.wikipedia.org/wiki/Books_of_the_Bible
            return when {
                bookNo <= BibleBook.DEUT.ordinal -> // Pentateuch - books of Moses
                    ExtraBookInfo(PENTATEUCH_COLOR, "PENTATEUCH", "PENTATEUCH")
                bookNo <= BibleBook.ESTH.ordinal -> // History
                    ExtraBookInfo(HISTORY_COLOR, "HISTORY", "HISTORY")
                bookNo <= BibleBook.SONG.ordinal -> // Wisdom
                    ExtraBookInfo(WISDOM_COLOR, "WISDOM", "WISDOM")
                bookNo <= BibleBook.DAN.ordinal -> // Major prophets
                    ExtraBookInfo(MAJOR_PROPHETS_COLOR, "MAJOR", "MAJOR")
                bookNo <= BibleBook.MAL.ordinal -> // Minor prophets
                    ExtraBookInfo(MINOR_PROPHETS_COLOR, "MINOR", "MINOR")
                bookNo <= BibleBook.JOHN.ordinal -> // Gospels
                    ExtraBookInfo(GOSPEL_COLOR, "GOSPEL", "GOSPEL+ACTS")
                bookNo <= BibleBook.ACTS.ordinal -> // Acts
                    ExtraBookInfo(ACTS_COLOR, "ACTS", "GOSPEL+ACTS")
                bookNo <= BibleBook.PHLM.ordinal -> // Pauline epistles
                    ExtraBookInfo(PAULINE_COLOR, "PAULINE", "PAULINE")
                bookNo <= BibleBook.JUDE.ordinal -> // General epistles
                    ExtraBookInfo(GENERAL_EPISTLES_COLOR, "GENERAL", "GENERAL+REVELATION")
                bookNo <= BibleBook.REV.ordinal -> // Revelation
                    ExtraBookInfo(REVELATION_COLOR, "REVELATION", "GENERAL+REVELATION")
                else -> ExtraBookInfo(OTHER_COLOR,"", "")
            }
        }
    }
}
