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

package net.bible.android.activity

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.cleanstatusbar.CleanStatusBar
import tools.fastlane.screengrab.locale.LocaleTestRule
import java.util.*

/*
    HOWTO:
    - run tests by command `fastlane screengrab`

    TODO:
    - We need to have zip of modules in each Play Store language
    - We need to have db with certain bookmarks, notes and workspaces
    - We we might need to have certain settings (those can be set runtime too)
    - In each locale, we need to automatically choose the local bible module, or the first local bible module

    Screenshots must be captured on emulator that DOES NOT have Google Play additions in it (must allow root).

 */

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainBibleActivityTests {
    @get:Rule var activityRule = ActivityTestRule(MainBibleActivity::class.java)
    @get:Rule var localeTestRule = LocaleTestRule()

    @Test
    fun testMainActivity() {
        val doc = mainBibleActivity.swordDocumentFacade.bibles.find { it.language.code == Locale.getDefault().language }

        mainBibleActivity.documentControl.currentPage.setCurrentDocumentAndKey(doc,
            VerseFactory.fromString(Versifications.instance().getVersification("KJV"), "John.3.16"))

        Thread.sleep(1000)
        Screengrab.screenshot("main_bible_view")
    }

    companion object {
        @BeforeClass @JvmStatic
        fun beforeAll() {
            CleanStatusBar.enableWithDefaults()
        }

        @AfterClass @JvmStatic
        fun afterAll() {
            CleanStatusBar.disable()
        }
    }
}
