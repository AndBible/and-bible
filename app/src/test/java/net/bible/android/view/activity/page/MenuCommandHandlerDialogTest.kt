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

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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

    @Before
    fun setUp() {
        val windowControl: WindowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

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
