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

package net.bible.android.view.compose

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.sharedui.nav.NavResultChannel
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * F118 (fix batch 6): a synchronous (STD) self-launch of a chooser must navigate the live graph on every
 * API level. Below API 31 the platform builds a SECOND host instance for a `startActivityForResult`
 * self-launch, in which the chooser has no parent entry and `NavResultChannel.deliver` crashes in
 * `exitWithResult`. The REAL chooser destination is composed and the REAL delivery is used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class StdSelfLaunchInGraphTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @Before
    fun firstTimeIsPinnedFalse() {
        firstTime = false
    }

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    /** See `ReadingChooserInGraphResultTest.compose`: makes the host's setContent recompose under Robolectric. */
    @get:Rule val compose = createEmptyComposeRule()

    private fun host() = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
    ).also { controllers += it }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitForIdle()
    }

    private fun composedReadingHost(): NavHostComposeActivity {
        // Pre-create the special labels (see ReadingChooserInGraphResultTest.composedReadingHost).
        GlobalContext.get().get<BookmarkControl>().apply { labelUnlabelled; speakLabel; paragraphBreakLabel }
        return host().create().start().resume().visible().get()
    }

    private fun nav(activity: NavHostComposeActivity): NavHostController =
        NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController

    @Suppress("UNCHECKED_CAST")
    private fun <T> channel(activity: NavHostComposeActivity, field: String): NavResultChannel<T> =
        NavHostComposeActivity::class.java.getDeclaredField(field)
            .apply { isAccessible = true }.get(activity) as NavResultChannel<T>

    @Test
    fun aStdChooseDocumentSelfLaunchNavigatesTheLiveGraphAndTheChoiceApplies() {
        val activity = composedReadingHost()
        val kjv = Books.installed().getBook("KJV")
        CommonUtils.windowControl.activeWindowPageManager.setCurrentDocument(kjv)
        idle()

        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.chooseDocument()), ActivityBase.STD_REQUEST_CODE,
        )
        idle()

        assertNull("F118: the STD self-launch must not reach the platform (a second host on API < 31)",
            shadowOf(activity).nextStartedActivity)
        assertEquals(NavRoutes.chooseDocument().substringBefore('?'), activity.currentRouteForTest()?.substringBefore('?'))

        // The REAL delivery: the chooser is on top, `reading` below it -- the shape that crashed in the second host.
        channel<DocumentResult>(activity, "documentResults").deliver(nav(activity), DocumentResult("ESV2011"))
        idle()
        assertEquals("ESV2011", CommonUtils.windowControl.activeWindowPageManager.currentPage.currentDocument?.initials)
    }

    @Test
    @Config(sdk = [28])
    fun onApi28TooTheChoiceApplies() = aStdChooseDocumentSelfLaunchNavigatesTheLiveGraphAndTheChoiceApplies()

    @Test
    fun aStdPassageGridSelfLaunchNavigatesAndMovesTheWindow() {
        val activity = composedReadingHost()
        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.gridChoosePassage(isScripture = true)),
            ActivityBase.STD_REQUEST_CODE,
        )
        idle()
        assertNull(shadowOf(activity).nextStartedActivity)
        channel<PassageResult>(activity, "passageResults").deliver(nav(activity), PassageResult("Ps.23.1"))
        idle()
        assertEquals("Ps.23.1", (CommonUtils.windowControl.activeWindowPageManager.currentPage.singleKey as Verse).getOsisID())
    }

    /** Review Focus 1: no graph yet (first frame) -- the launch must still reach the platform. */
    @Test
    fun withNoGraphYetAStdSelfLaunchStillGoesToThePlatform() {
        val activity = host().create().get() // nothing composed: navController null
        assertNull(activity.currentRouteForTest())
        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.chooseDocument()), ActivityBase.STD_REQUEST_CODE,
        )
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }
}
