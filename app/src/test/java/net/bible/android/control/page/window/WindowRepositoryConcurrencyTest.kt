/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for OSTicket 3374: a ConcurrentModificationException was thrown in
 * [WindowRepository.saveIntoDb], which iterates [WindowRepository.windowList] on the
 * background cloud-sync thread while the main thread structurally modifies the same list
 * (opening/closing windows). A fail-fast [ArrayList] throws in that situation; windowList
 * must instead iterate over a snapshot so an in-progress iteration tolerates concurrent
 * structural modification.
 *
 * Kept separate from [WindowRepositoryTest] so it can disable the first-run default-verse
 * setup, which requires an installed Bible module (only present on CI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowRepositoryConcurrencyTest {
    private var windowControl: WindowControl? = null
    private val windowRepository get() = windowControl!!.windowRepository

    @Before
    fun setUp() {
        // Avoid setFirstUseDefaultVerse() -> getDefaultBible(), which needs an installed
        // Bible module (CI-only). This test only exercises the windowList collection.
        CommonUtils.settings.setBoolean("first-time", false)
        windowControl = CommonUtils.windowControl
        windowControl!!.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowRepository.initialize()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
        windowRepository.clear()
    }

    @Test
    fun iteratingWindowListToleratesStructuralModification() {
        windowRepository.addNewWindow()
        windowRepository.addNewWindow()

        val snapshotSize = windowRepository.windowList.size
        val iterator = windowRepository.windowList.iterator()
        assertThat(iterator.hasNext(), equalTo(true))
        iterator.next()

        // Structural modification mid-iteration mirrors a main-thread window add racing
        // with the background saveIntoDb iteration.
        windowRepository.addNewWindow()

        // Draining the pre-existing iterator must not throw ConcurrentModificationException.
        var count = 1
        while (iterator.hasNext()) {
            iterator.next()
            count++
        }
        // The snapshot iterator reflects the list as it was when created, not the later
        // addition.
        assertThat(count, equalTo(snapshotSize))
    }
}
