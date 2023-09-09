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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MenuItem

import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.util.buttongrid.ButtonGrid
import net.bible.android.view.util.buttongrid.ButtonInfo
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener
import net.bible.service.common.CommonUtils
import net.bible.service.common.myGetLastVerse

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook

import java.util.ArrayList

import javax.inject.Inject

/**
 * Choose a chapter to view
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class GridChoosePassageVerse : CustomTitlebarActivityBase(), OnButtonGridActionListener {

    private var mBibleBook = BibleBook.GEN
    private var mBibleChapterNo = 1

    @Inject lateinit var navigationControl: NavigationControl
    @Inject lateinit var windowControl: WindowControl

    // background goes white in some circumstances if theme changes so prevent theme change
    override val allowThemeChange = false

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildActivityComponent().inject(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val bibleBookNo = intent.getIntExtra(GridChoosePassageBook.BOOK_NO, navigationControl.defaultBibleBookNo)
        mBibleBook = BibleBook.values()[bibleBookNo]

        mBibleChapterNo = intent.getIntExtra(GridChoosePassageBook.CHAPTER_NO, navigationControl.defaultBibleChapterNo)

        // show chosen book in page title to confirm user choice
        try {
            title = navigationControl.versification.getLongName(mBibleBook) + " " + mBibleChapterNo
        } catch (nsve: Exception) {
            Log.e(TAG, "Error in selected book no or chapter no", nsve)
        }

        val grid = ButtonGrid(this)
        grid.setOnButtonGridActionListener(this)
        grid.isLeftToRightEnabled = CommonUtils.settings.getBoolean(GridChoosePassageBook.BOOK_GRID_FLOW_PREFS, false)
        grid.addButtons(getBibleVersesButtonInfo(mBibleBook, mBibleChapterNo))
        setContentView(grid)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getBibleVersesButtonInfo(book: BibleBook, chapterNo: Int): List<ButtonInfo> {
        val verses = try {
            navigationControl.versification.myGetLastVerse(navigationControl.currentPassageDocument, book, chapterNo)
        } catch (nsve: Exception) {
            Log.e(TAG, "Error getting number of verses", nsve)
            -1
        }

        val bookColorAndGroup = GridChoosePassageBook.getBookColorAndGroup(book.ordinal)
        val currentVerse = windowControl.activeWindowPageManager.currentPage.singleKey as Verse

        val keys = ArrayList<ButtonInfo>()
        for (i in 1..verses) {
            val buttonInfo = ButtonInfo()
            // this is used for preview
            buttonInfo.id = i
            buttonInfo.name = i.toString()
            buttonInfo.type = ButtonInfo.GridButtonTypes.VERSE
            if (i == currentVerse.verse && chapterNo == currentVerse.chapter && book == currentVerse.book) {
                buttonInfo.tintColor = bookColorAndGroup.Color
                buttonInfo.textColor = Color.DKGRAY
            }

            keys.add(buttonInfo)
        }
        return keys
    }

    override fun buttonPressed(buttonInfo: ButtonInfo) {
        val verse = Verse(navigationControl.versification, mBibleBook, mBibleChapterNo, buttonInfo.id)
        Log.i(TAG, "Verse selected:$verse")
        val resultIntent = Intent(this, GridChoosePassageBook::class.java)
        resultIntent.putExtra("verse", verse.osisID)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {

        private const val TAG = "GridChoosePassageChaptr"
    }
}
