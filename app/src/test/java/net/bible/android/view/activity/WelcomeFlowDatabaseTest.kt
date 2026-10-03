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

package net.bible.android.view.activity

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Fix batch 1 §2.6: a host restored straight onto WELCOME after process death never passes through
 * `StartupActivity.initializeDatabase`, yet `WelcomeFlow` reads the repo DB. Under `isRunningTests`
 * `DatabaseContainer.instance` never throws, so the crash cannot reproduce; this pins the precondition
 * the crash violates -- the DB is opened (`ready`) by the time Welcome reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WelcomeFlowDatabaseTest {
    @After fun restore() { DatabaseContainer.ready = true }

    @Test
    fun aHostStartedOnWelcomeWithTheDbNotYetReadyOpensItBeforeWelcomeReadsIt() {
        resetComposeUiDispatcher()
        DatabaseContainer.ready = false // a process restored straight onto WELCOME
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
        ).apply { create().start().resume().visible() }
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "WelcomeFlow read the repo DB without anyone opening it -- process-death crash",
            DatabaseContainer.ready,
        )
        controller.close()
    }
}
