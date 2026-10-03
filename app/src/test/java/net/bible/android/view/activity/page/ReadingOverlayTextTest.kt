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
package net.bible.android.view.activity.page

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.versification.BookName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The current-reference overlay's text — [ReadingCommands.bibleOverlayText], read by
 * `ComposeReadingViewHost.readOverlayText()`.
 *
 * Written in R3's review round, because the move of this member to [ReadingCommands] broke it and
 * the WHOLE suite stayed green: the moved body read `"$bookName:$activity.pageTitleText"`, where
 * `$activity` is the entire template expression and `.pageTitleText` is literal text, so the
 * overlay rendered `"KJV:net.bible…MainBibleActivity@1a2b3c.pageTitleText"`. It compiled, no test
 * referenced `bibleOverlayText` at all, and the string is only ever drawn on screen. This class is
 * the coverage that was missing.
 *
 * Deliberately a NEW class: the eight Robolectric classes that are the re-typing batch's safety net
 * are not edited in R1--R6.
 *
 * Built WITHOUT `.create()` for the reasons `OptionsMenuStateBuilderTest`'s kdoc gives — this
 * property reads `pageControl` (a Koin singleton) and `windowRepository`, never a view. Hosted on the
 * reading-route [NavHostComposeActivity] since slice 8 F2 (`MainBibleActivity` is deleted).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingOverlayTextTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    /** The page title as [ReadingCommands.bibleOverlayText] must render it: short book names, the
     *  same `BookName` bracketing the property itself applies. */
    private fun shortPageTitle(): String = synchronized(BookName::class.java) {
        val oldValue = BookName.isFullBookName()
        BookName.setFullBookName(false)
        try { activity.readingCommands.pageTitleText } finally { BookName.setFullBookName(oldValue) }
    }

    @Test
    fun theOverlayInterpolatesThePageTitlesVALUE() {
        activity.readingCommands.applyChosenVerse("Gen.1.1")
        val abbreviation = GlobalContext.get().get<PageControl>().currentPageManager.currentPage.currentDocument?.abbreviation
        assertEquals(
            "the overlay is \"<document abbreviation>:<page title>\"",
            "$abbreviation:${shortPageTitle()}",
            activity.readingCommands.bibleOverlayText,
        )
    }

    /**
     * The exact defect, named. The correct braced interpolation and the unbraced one differ by two
     * characters and both compile; only this assertion tells them apart without a human reading the
     * rendered overlay.
     */
    @Test
    fun theOverlayNeverLeaksTheTemplateItself() {
        activity.readingCommands.applyChosenVerse("Gen.1.1")
        val overlay = activity.readingCommands.bibleOverlayText
        assertFalse(
            "the page title must be INTERPOLATED, not spelled: got \"$overlay\"",
            overlay.contains(".pageTitleText"),
        )
        assertFalse(
            "…and the host's toString() must never reach the overlay: got \"$overlay\"",
            overlay.contains("NavHostComposeActivity@"),
        )
    }

    /** …and it really is the chosen verse's title, not some constant that happens to avoid both
     *  substrings above. */
    @Test
    fun theOverlayTracksTheCurrentKey() {
        activity.readingCommands.applyChosenVerse("Gen.1.1")
        val atGenesis = activity.readingCommands.bibleOverlayText
        activity.readingCommands.applyChosenVerse("Ps.23.1")
        val atPsalms = activity.readingCommands.bibleOverlayText

        assertTrue("the overlay must carry a page title, got \"$atGenesis\"", atGenesis.substringAfter(":").isNotBlank())
        assertTrue(
            "the overlay must change when the key changes: Genesis gave \"$atGenesis\", " +
                "Psalms gave \"$atPsalms\"",
            atGenesis != atPsalms,
        )
    }
}
