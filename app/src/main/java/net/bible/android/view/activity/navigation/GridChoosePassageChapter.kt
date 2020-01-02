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
import android.view.View

import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.util.buttongrid.ButtonGrid
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener
import net.bible.service.common.CommonUtils

import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook

import java.util.ArrayList

import javax.inject.Inject

import net.bible.android.view.activity.navigation.GridChoosePassageBook.Companion.BOOK_NO

/**
 * Choose a chapter to view
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class GridChoosePassageChapter : CustomTitlebarActivityBase(), OnButtonGridActionListener {

    private var mBibleBook = BibleBook.GEN

    @Inject lateinit var navigationControl: NavigationControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    private var navigateToVerse = false

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        // background goes white in some circumstances if theme changes so prevent theme change
        setAllowThemeChange(false)
        super.onCreate(savedInstanceState)

        buildActivityComponent().inject(this)
        val bibleBookNo = intent.getIntExtra(BOOK_NO, navigationControl.defaultBibleBookNo)
        //TODO av11n - this is done now
        mBibleBook = BibleBook.values()[bibleBookNo]

        // show chosen book in page title to confirm user choice
        try {
            //TODO av11n - probably should use same v11n as used in GridChoosePassageBook
            title = navigationControl.versification.getLongName(mBibleBook)
        } catch (nsve: Exception) {
            Log.e(TAG, "Error in selected book no", nsve)
        }

        val navigateToVerseDefault = CommonUtils.sharedPreferences.getBoolean("navigate_to_verse_pref", false)
        navigateToVerse = intent?.extras?.getBoolean("navigateToVerse", navigateToVerseDefault)?:navigateToVerseDefault

        val grid = ButtonGrid(this)
        grid.setOnButtonGridActionListener(this)

        grid.addButtons(getBibleChaptersButtonInfo(mBibleBook))
        setContentView(grid)
    }

    private fun getBibleChaptersButtonInfo(book: BibleBook): List<ButtonInfo> {
        val chapters: Int = try {
            navigationControl.versification.getLastChapter(book)
        } catch (nsve: Exception) {
            -1
        }
        val currentVerse = KeyUtil.getVerse(activeWindowPageManagerProvider.activeWindowPageManager.currentBible.key)
        val currentBibleBook = currentVerse.book
        val currentBibleChapter = currentVerse.chapter

        val keys = ArrayList<ButtonInfo>()
        for (i in 1..chapters) {
            val buttonInfo = ButtonInfo()
            // this is used for preview
            buttonInfo.id = i
            buttonInfo.name = Integer.toString(i)
            if (currentBibleBook == book && i == currentBibleChapter) {
                buttonInfo.textColor = Color.YELLOW
                buttonInfo.highlight = true
            }
            keys.add(buttonInfo)
        }
        return keys
    }

    override fun buttonPressed(buttonInfo: ButtonInfo) {
        val chapter = buttonInfo.id
        Log.d(TAG, "Chapter selected:$chapter")
        try {
            val currentPageControl = activeWindowPageManagerProvider.activeWindowPageManager
            if (!navigateToVerse && !currentPageControl.currentPage.isSingleKey) {
                val verse = Verse(navigationControl.versification, mBibleBook, chapter, 1)
                val resultIntent = Intent(this, GridChoosePassageBook::class.java)
                resultIntent.putExtra("verse", verse.osisID)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()


                onSave(null)
            } else {
                // select verse
                val myIntent = Intent(this, GridChoosePassageVerse::class.java)
                myIntent.putExtra(BOOK_NO, mBibleBook.ordinal)
                myIntent.putExtra(GridChoosePassageBook.CHAPTER_NO, chapter)
                startActivityForResult(myIntent, chapter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "error on select of bible book", e)
        }

    }

    private fun onSave(v: View?) {
        Log.i(TAG, "CLICKED")
        val resultIntent = Intent(this, GridChoosePassageBook::class.java)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    @SuppressLint("MissingSuperCall")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    companion object {

        private const val TAG = "GridChoosePassageChaptr"
    }
}
