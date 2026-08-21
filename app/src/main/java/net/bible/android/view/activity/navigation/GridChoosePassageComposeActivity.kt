/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.navigation.GridChoosePassageScreen
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.koin.android.ext.android.inject

/** Single Compose host for the Book→Chapter→Verse passage grid — the new-path twin of the three
 *  classic [GridChoosePassageBook]/Chapter/Verse activities, collapsed into an internal step flow. */
class GridChoosePassageComposeActivity : ActivityBase() {
    private val navigationControl: NavigationControl by inject()
    private val windowControl: WindowControl by inject()

    private var navigateToVerse = false
    private var selectedBookNo = 0
    private var selectedChapter = 1

    private val v11n get() = navigationControl.versification

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val customTitle = intent?.extras?.getCharSequence("title")
        navigateToVerse = intent.getBooleanExtra("navigateToVerse", CommonUtils.settings.getBoolean("navigate_to_verse_pref", false))
        val isScripture = intent?.getBooleanExtra("isScripture", false) ?: false

        val baseTitle = (customTitle ?: getString(net.bible.android.activity.R.string.choosePassageBookName)).toString()
        val workspaceName = SharedActivityState.currentWorkspaceName

        // Round 13a: every seam below is `GridPassageHostSupport.kt` — the helpers that used to be
        // this activity's own private functions, now shared with the Speak sheet's verse-picker
        // page (`ComposeReadingViewHost.SpeakVersePickerPage`). One implementation, two hosts.
        val controller = GridChoosePassageController(
            initialOptions = initialGridOptions(navigationControl, isScripture),
            buildStep = { step, opts ->
                buildGridStep(step, opts, baseTitle, workspaceName, selectedBookNo, selectedChapter,
                    navigationControl, windowControl)
            },
            onPersistOptions = { persistGridOptions(it, navigationControl) },
            onPickBook = { bookNo ->
                selectedBookNo = bookNo
                pickGridBook(bookNo, navigateToVerse, navigationControl) { selectedChapter = it }
            },
            onPickChapter = { chapter ->
                selectedChapter = chapter
                pickGridChapter(chapter, selectedBookNo, navigateToVerse, navigationControl, windowControl)
            },
            onPickVerse = { verse -> Verse(v11n, BibleBook.values()[selectedBookNo], selectedChapter, verse).osisID },
            onFinish = { osisId -> finishWithVerse(osisId) },
        )

        onBackPressedDispatcher.addCallback(this) { if (!controller.back()) finish() }

        setContent {
            AbAppTheme {
                    val ui by controller.ui.collectAsState()
                    val options by controller.options.collectAsState()
                    GridChoosePassageScreen(
                        ui = ui,
                        options = options,
                        onPick = controller::pick,
                        onToggle = controller::toggle,
                        onNavigateUp = { if (!controller.back()) finish() },
                    )
            }
        }
    }

    private fun finishWithVerse(osisId: String) {
        val resultIntent = Intent(this, GridChoosePassageBook::class.java).putExtra("verse", osisId)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
