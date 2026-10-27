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

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.common.BuildVariant
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.AdditionalAnswers
import org.mockito.Mockito
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Task 19 Step 2: `MenuCommandHandler`'s rate (class B, HTML with links incl. `mailto:`) and app
 * licence (class A, HTML long body) dialogs move onto the app-wide [AppDialogController].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MenuCommandHandlerDialogTest {
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: CalculatorComposeActivity
    private lateinit var handler: MenuCommandHandler
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    @Before
    fun setUp() {
        val windowControl: WindowControl = CommonUtils.windowControl
        windowRepository = net.bible.test.testWindowRepository()
        windowControl.windowRepository = windowRepository
        windowRepository.initializeAtEdge()

        activity = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup().get()
        CurrentActivityHolder.activate(activity)
        handler = MenuCommandHandler(
            hostActivity = activity,
            composeReadingViewHost = { null },
            composeSearchIfHosted = { false },
        )
    }

    @After
    fun tearDown() {
        CurrentActivityHolder.deactivate(activity)
        dialogs.cancelAll()
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test(timeout = 30000)
    fun rateButtonPostsAnHtmlMessageWithCancelAndOpensThePlayStoreOnlyOnOk() {
        handler.handleMenuRequest(R.id.rateButton)
        idle()

        val request = dialogs.pending.value!!.request as AppDialogRequest.Message
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.rate_title), request.title)
        assertTrue("body is HTML, not spanned/plain text", request.message.contains("<br>"))
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.proceed_google_play), request.confirmText)
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.cancel), request.dismissText)
        assertTrue(request.cancellable)
        assertNull("no third (report) button", request.neutralText)

        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.Cancel)
        idle()
        assertNull("Cancel must not open anything", shadowOf(activity).nextStartedActivityForResult)
    }

    @Test(timeout = 30000)
    fun rateButtonOpensThePlayStoreOnOk() {
        handler.handleMenuRequest(R.id.rateButton)
        idle()

        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.Ok)
        idle()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("Ok must start the Play Store / Samsung Apps intent, as today", started)
    }

    private fun withPackageName(packageName: String, block: () -> Unit) {
        val original = BibleApplication.application
        try {
            val application = Mockito.mock(BibleApplication::class.java, AdditionalAnswers.delegatesTo<BibleApplication>(original)).also {
                doReturn(packageName).whenever(it).packageName
            }
            ReflectionHelpers.setStaticField(BibleApplication::class.java, "application", application)
            block()
        } finally {
            ReflectionHelpers.setStaticField(BibleApplication::class.java, "application", original)
        }
    }

    private fun assertRecommendationUsesPublishedPlayListing(packageName: String) = withPackageName(packageName) {
        handler.handleMenuRequest(R.id.tellFriend)
        val chooser = shadowOf(activity).nextStartedActivityForResult.intent
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        val message = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertTrue(message, message.contains("https://andbible.org"))
        val playLinks = Regex("https://play\\.google\\.com/store/apps/details\\?id=\\S+")
            .findAll(message).map { it.value }.toList()
        assertEquals(listOf("https://play.google.com/store/apps/details?id=net.bible.android.activity"), playLinks)
    }

    @Test fun standardPlayRecommendationUsesPublishedListing() =
        assertRecommendationUsesPublishedPlayListing("net.bible.android.activity")

    @Test fun githubRecommendationUsesPublishedListing() =
        assertRecommendationUsesPublishedPlayListing("net.bible.android.activity.debug")

    @Test fun fdroidRecommendationUsesPublishedListing() =
        assertRecommendationUsesPublishedPlayListing("net.bible.android.activity")

    @Test fun accrescentRecommendationUsesPublishedListing() =
        assertRecommendationUsesPublishedPlayListing("org.andbible.andbible")

    @Test fun discretePackageIsNeverUsedAsAPlayListing() =
        assertRecommendationUsesPublishedPlayListing("com.app.calculator")

    @Test fun discreteDebugPackageIsNeverUsedAsAPlayListing() =
        assertRecommendationUsesPublishedPlayListing("com.app.calculator.debug")

    @Test fun ratingNeverUsesTheRunningPackageAsThePlayListing() = withPackageName("org.andbible.andbible.debug") {
        handler.handleMenuRequest(R.id.rateButton)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        idle()
        assertEquals("market://details?id=net.bible.android.activity",
            shadowOf(activity).nextStartedActivityForResult.intent.dataString)
    }

    @Test fun ratingBrowserFallbackUsesThePublishedPlayListing() = withPackageName("org.andbible.andbible.debug") {
        val application = RuntimeEnvironment.getApplication()
        val fallback = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
            "https://play.google.com/store/apps/details?id=net.bible.android.activity"))
        shadowOf(application.packageManager).addResolveInfoForIntent(fallback, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "test.browser"; name = "BrowserActivity" }
        })
        shadowOf(application).checkActivities(true)
        try {
            handler.handleMenuRequest(R.id.rateButton)
            dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
            idle()
            assertEquals(fallback.dataString, shadowOf(activity).nextStartedActivityForResult.intent.dataString)
        } finally {
            shadowOf(application).checkActivities(false)
        }
    }

    @Test fun samsungRecommendationKeepsOmittingThePlayStoreLink() {
        org.junit.Assume.assumeTrue(BuildVariant.DistributionChannel.isSamsung)
        withPackageName("net.bible.android.activity.debug") {
            handler.handleMenuRequest(R.id.tellFriend)
            val chooser = shadowOf(activity).nextStartedActivityForResult.intent
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            val message = send.getStringExtra(Intent.EXTRA_TEXT)!!
            assertTrue(message, message.contains("https://andbible.org"))
            org.junit.Assert.assertFalse(message, message.contains("play.google.com"))
        }
    }

    @Test fun samsungRatingKeepsUsingTheSamsungListing() {
        org.junit.Assume.assumeTrue(BuildVariant.DistributionChannel.isSamsung)
        withPackageName("net.bible.android.activity") {
            handler.handleMenuRequest(R.id.rateButton)
            dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
            idle()
            assertEquals("samsungapps://AppRating/net.bible.android.activity",
                shadowOf(activity).nextStartedActivityForResult.intent.dataString)
        }
    }

    @Test fun discreteFlavorDoesNotShareOrRateEvenWithoutThePreference() {
        org.junit.Assume.assumeTrue(BuildVariant.Appearance.isDiscrete)
        handler.handleMenuRequest(R.id.tellFriend)
        handler.handleMenuRequest(R.id.rateButton)
        assertNull(dialogs.pending.value)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test fun discreteModeDoesNotShareTheAppIdentityEvenIfDispatchedDirectly() {
        val prefs = CommonUtils.realSharedPreferences
        val original = prefs.getBoolean("discrete_mode", false)
        try {
            prefs.edit().putBoolean("discrete_mode", true).commit()
            handler.handleMenuRequest(R.id.tellFriend)
            assertNull(shadowOf(activity).nextStartedActivityForResult)
        } finally {
            prefs.edit().putBoolean("discrete_mode", original).commit()
        }
    }

    @Test fun discreteModeDoesNotOpenARatingDialogEvenIfDispatchedDirectly() {
        val prefs = CommonUtils.realSharedPreferences
        val original = prefs.getBoolean("discrete_mode", false)
        try {
            prefs.edit().putBoolean("discrete_mode", true).commit()
            handler.handleMenuRequest(R.id.rateButton)
            assertNull(dialogs.pending.value)
            assertNull(shadowOf(activity).nextStartedActivityForResult)
        } finally {
            prefs.edit().putBoolean("discrete_mode", original).commit()
        }
    }

    @Test(timeout = 30000)
    fun appLicencePostsTheRawLicenseTextWithOneOkButton() {
        val handled = handler.handleMenuRequest(R.id.appLicence)
        idle()

        assertTrue("appLicence is handled synchronously", handled)
        val request = dialogs.pending.value!!.request as AppDialogRequest.Message
        val expectedLicense = net.bible.android.BibleApplication.application.resources
            .openRawResource(R.raw.license).readBytes().decodeToString()
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.app_licence_title), request.title)
        assertEquals(expectedLicense, request.message)
        assertEquals(RuntimeEnvironment.getApplication().getString(android.R.string.ok), request.confirmText)
        assertNull("licence dialog has only the one OK button", request.dismissText)
        assertNull(request.neutralText)
        assertTrue(request.cancellable)
    }
}
