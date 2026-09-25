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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

private const val NOTICE_PREF = "new-sync-targets-notice-displayed"

/**
 * Task 19 Step 3: `ReadingAppBootstrap.showNewSyncTargetsNotice` (class B, "open settings") moves
 * onto the app-wide [AppDialogController]. Tests [ReadingAppBootstrap.showNewSyncTargetsNotice]
 * directly — it is `internal` for exactly this reason (see its KDoc) — rather than through
 * `showFirstRunNotices()`, which also runs `checkCrash`/`checkPoorTranslations` (can `exitProcess`)
 * and two other notices first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingAppBootstrapSyncNoticeTest {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private val prefs get() = CommonUtils.settings
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @Before
    fun setUp() {
        prefs.setInt(NOTICE_PREF, 0)
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = false
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.close() } }
        controllers.clear()
        prefs.setInt(NOTICE_PREF, 0)
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = false
        dialogs.cancelAll()
    }

    private fun buildActivity(): NavHostComposeActivity =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().get()

    @Test(timeout = 30000)
    fun syncDisabledMarksTheNoticeSeenAndPostsNothing() {
        val activity = buildActivity()
        activity.readingAppBootstrap.showNewSyncTargetsNotice()

        assertNull("irrelevant to users who don't sync -- suppressed, not shown", dialogs.pending.value)
        assertEquals(1, prefs.getInt(NOTICE_PREF, 0))
    }

    @Test(timeout = 30000)
    fun syncEnabledPostsAConfirmWithBothButtonTexts() {
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = true
        val activity = buildActivity()
        activity.readingAppBootstrap.showNewSyncTargetsNotice()

        val request = dialogs.pending.value!!.request as AppDialogRequest.Confirm
        assertEquals(activity.getString(R.string.new_sync_targets_notice_title), request.title)
        assertEquals(activity.getString(R.string.new_sync_targets_notice_message), request.message)
        assertEquals(activity.getString(R.string.open_settings), request.confirmText)
        assertEquals(activity.getString(R.string.dismiss), request.dismissText)
        assertEquals("setCancelable(false), as today", false, request.cancellable)
    }

    @Test(timeout = 30000)
    fun dismissMarksItSeenWithoutOpeningSettings() {
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = true
        val activity = buildActivity()
        activity.readingAppBootstrap.showNewSyncTargetsNotice()

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)

        assertEquals(1, prefs.getInt(NOTICE_PREF, 0))
        assertNull("dismiss must not open Sync settings", shadowOf(activity).nextStartedActivity)
    }

    @Test(timeout = 30000)
    fun openSettingsMarksItSeenAndOpensSyncSettings() {
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = true
        val activity = buildActivity()
        activity.readingAppBootstrap.showNewSyncTargetsNotice()

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)

        assertEquals(1, prefs.getInt(NOTICE_PREF, 0))
        assertNotNull("Ok must open Sync settings, as today", shadowOf(activity).nextStartedActivity)
    }
}
