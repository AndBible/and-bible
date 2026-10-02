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

package net.bible.android.view

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.MINIMUM_WEBVIEW_MAJOR_VERSION
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.noticeLogoRes
import net.bible.android.view.activity.page.shouldShowBetaNotice
import net.bible.android.view.activity.page.stableNoticeAppName
import net.bible.android.view.activity.settings.syncSummaryAppName
import net.bible.service.common.incompleteTranslationAppName
import net.bible.service.sword.shareAdvertAllowed
import net.bible.android.view.activity.webViewTooOldRequest
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRangeFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix batch 6 A6 (spec §1.6): ten surfaces that named AndBible in discrete mode. Every one follows
 * [CommonUtils.isDiscrete] (flavor OR the `discrete_mode` preference), so each test turns discrete
 * mode on THROUGH THE PREFERENCE ONLY -- the test build is the standard flavor -- which is also
 * Review Focus 5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DiscreteIdentityTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun discrete(on: Boolean) {
        CommonUtils.realSharedPreferences.edit().apply {
            if (on) putBoolean("discrete_mode", true) else remove("discrete_mode")
        }.commit()
    }

    @After
    fun tearDown() {
        discrete(false)
        DatabaseResetter.resetDatabase()
    }

    private fun drawerIds() = DrawerMenuStateBuilder.build(
        showSearch = true, showSpeak = true, isCloudSyncAvailable = true, isRateVisible = true,
    ).groups.flatMap { it.items }.map { it.id }

    private fun shareSelection(): Selection {
        val book = Books.installed().getBook("ESV2011") as SwordBook
        val range = VerseRangeFactory.fromString(book.versification, "Ps.83.1")
        return Selection("ESV2011", range.start.ordinal, 7, range.end.ordinal, 30, emptyList())
    }

    private fun selectionText() = SwordContentFacade.getSelectionText(
        shareSelection(), showVerseNumbers = true, advertiseApp = true,
    )

    private fun buildTextWithAdvert() = ShareVersesOptions(
        showVerseNumbers = true, advertiseApp = true, showReference = true, abbreviateReference = true,
        showVersion = true, showNotes = true, showSelectionOnly = true, showEllipsis = true,
        showReferenceAtFront = false, showQuotes = true, separateVersesWithNewlines = false,
    ).buildText(SwordContentFacade.buildShareVersesInput(shareSelection()))

    // 5
    @Test fun drawerHidesTellAFriendWhenDiscrete() {
        discrete(true)
        assertFalse(drawerIds().contains("tellFriend"))
        assertTrue("the neighbours stay", drawerIds().contains("bugReport"))
    }

    @Test fun drawerShowsTellAFriendOtherwise() {
        assertTrue(drawerIds().contains("tellFriend"))
    }

    // 7
    @Test fun theShareTextNeverCarriesTheAdvertWhenDiscrete() {
        discrete(true)
        assertFalse(selectionText(), selectionText().contains("andbible.github.io"))
        assertFalse(buildTextWithAdvert(), buildTextWithAdvert().contains("andbible.github.io"))
    }

    @Test fun theShareTextKeepsTheAdvertOtherwise() {
        assertTrue(selectionText().contains("andbible.github.io"))
        assertTrue(buildTextWithAdvert().contains("andbible.github.io"))
    }

    // 9
    @Test fun theOldWebViewTextUsesTheCalculatorNameWhenDiscrete() {
        discrete(true)
        val msg = webViewTooOldRequest(
            context, "com.google.android.webview", "${MINIMUM_WEBVIEW_MAJOR_VERSION - 1}.0.1.0",
        )!!.message!!
        assertTrue(msg, msg.contains(context.getString(R.string.app_name_calculator)))
        assertFalse(msg, msg.contains("AndBible"))
    }

    @Test fun theOldWebViewTextKeepsTheRealNameOtherwise() {
        val msg = webViewTooOldRequest(
            context, "com.google.android.webview", "${MINIMUM_WEBVIEW_MAJOR_VERSION - 1}.0.1.0",
        )!!.message!!
        assertTrue(msg, msg.contains(context.getString(R.string.app_name_medium)))
    }

    // 6
    @Test fun theWelcomeStateHidesBothHomepageButtonsWhenDiscrete() {
        discrete(true)
        assertFalse(welcomeState().homepageButtonsVisible)
    }

    @Test fun theWelcomeStateShowsBothHomepageButtonsOtherwise() {
        assertTrue(welcomeState().homepageButtonsVisible)
    }

    private fun welcomeState(): net.bible.sharedcore.startup.StartupWelcomeState {
        net.bible.android.view.activity.base.firstTime = false
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(context, NavRoutes.WELCOME),
        )
        try {
            return controller.create().get().welcomeFlow.controller.state.value
        } finally {
            controller.close()
        }
    }

    // 1, 2, 4, 7, 8, 10: the pure decisions
    @Test fun theStableNoticeBodyNamesTheCalculatorWhenDiscrete() {
        assertEquals(R.string.app_name_calculator, stableNoticeAppName(true))
        assertEquals(R.string.app_name_long, stableNoticeAppName(false))
    }

    @Test fun theNoticeTitleLogoIsTheCalculatorsWhenDiscrete() {
        assertEquals(R.drawable.ic_calculator_color, noticeLogoRes(true))
        assertEquals(R.drawable.ic_logo, noticeLogoRes(false))
    }

    @Test fun theBetaNoticeIsShownOnlyToNonDiscreteBetas() {
        assertTrue(shouldShowBetaNotice(isBeta = true, discrete = false))
        assertFalse(shouldShowBetaNotice(isBeta = true, discrete = true))
        assertFalse(shouldShowBetaNotice(isBeta = false, discrete = false))
        assertFalse(shouldShowBetaNotice(isBeta = false, discrete = true))
    }

    @Test fun theAdvertIsNeverAllowedWhenDiscrete() {
        assertTrue(shareAdvertAllowed(userWantsAdvert = true, discrete = false))
        assertFalse(shareAdvertAllowed(userWantsAdvert = true, discrete = true))
        assertFalse(shareAdvertAllowed(userWantsAdvert = false, discrete = false))
    }

    @Test fun thePoorTranslationDialogNamesTheCalculatorWhenDiscrete() {
        assertEquals(R.string.app_name_calculator, incompleteTranslationAppName(true))
        assertEquals(R.string.app_name_long, incompleteTranslationAppName(false))
    }

    @Test fun theSyncSummaryNamesTheCalculatorWhenDiscrete() {
        assertEquals(R.string.app_name_calculator, syncSummaryAppName(true))
        assertEquals(R.string.app_name_medium, syncSummaryAppName(false))
    }

    /** Review Focus 5: standard flavor (the test build), discrete by preference only. */
    @Test fun prefOnlyDiscreteHidesTheSameSurfaces() {
        assertFalse("sanity: the flavor is not discrete", net.bible.service.common.BuildVariant.Appearance.isDiscrete)
        discrete(true)
        assertTrue(CommonUtils.isDiscrete)
        // 5
        assertFalse(drawerIds().contains("tellFriend"))
        // 6
        assertFalse(welcomeState().homepageButtonsVisible)
        // 7
        assertFalse(selectionText().contains("andbible.github.io"))
        assertFalse(buildTextWithAdvert().contains("andbible.github.io"))
        // 9
        val msg = webViewTooOldRequest(
            context, "com.google.android.webview", "${MINIMUM_WEBVIEW_MAJOR_VERSION - 1}.0.1.0",
        )!!.message!!
        assertFalse(msg, msg.contains("AndBible"))
        // 3 and 4 (the notice blocks and the beta skip) run through the real bootstrap in
        // ReadingAppBootstrapTest.showStableNoticeFollowsTheDiscretePreferenceNotTheFlavor and
        // showBetaNoticeIsSkippedWhenDiscrete, also with the preference alone.
    }
}
