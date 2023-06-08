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

package net.bible.android

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import net.bible.android.activity.R

import net.bible.android.view.activity.page.MainBibleActivity
import org.junit.Ignore
import org.junit.Test

@Ignore("Let's see if one day we have some tests here")
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainBibleActivityTests {

    @Test
    fun testMainActivity() {
        launch(MainBibleActivity::class.java).use {
            //mainBibleActivity.windowControl.activeWindowPageManager.currentPage.key =
            onView(withId(R.id.speakButton))
        }
    }
}
