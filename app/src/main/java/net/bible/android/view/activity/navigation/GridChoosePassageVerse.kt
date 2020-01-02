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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.util.buttongrid.ButtonGrid
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener

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
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        // background goes white in some circumstances if theme changes so prevent theme change
        setAllowThemeChange(false)
        super.onCreate(savedInstanceState)

        buildActivityComponent().inject(this)

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

        grid.addButtons(getBibleVersesButtonInfo(mBibleBook, mBibleChapterNo))
        setContentView(grid)
    }

    private fun getBibleVersesButtonInfo(book: BibleBook, chapterNo: Int): List<ButtonInfo> {
        val verses = try {
            navigationControl.versification.getLastVerse(book, chapterNo)
        } catch (nsve: Exception) {
            Log.e(TAG, "Error getting number of verses", nsve)
            -1
        }

        val keys = ArrayList<ButtonInfo>()
        for (i in 1..verses) {
            val buttonInfo = ButtonInfo()
            // this is used for preview
            buttonInfo.id = i
            buttonInfo.name = Integer.toString(i)
            keys.add(buttonInfo)
        }
        return keys
    }

    override fun buttonPressed(buttonInfo: ButtonInfo) {
        val verse = Verse(navigationControl.versification, mBibleBook, mBibleChapterNo, buttonInfo.id)
        Log.d(TAG, "Verse selected:$verse")
        val resultIntent = Intent(this, GridChoosePassageBook::class.java)
        resultIntent.putExtra("verse", verse.osisID)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {

        private const val TAG = "GridChoosePassageChaptr"
    }
}
