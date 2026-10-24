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

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.nhaarman.mockitokotlin2.mock
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

/** [AndroidReadingNavigator] starts the same routes, with the same request codes, the bridge used to start inline. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class AndroidReadingNavigatorTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val storagePath get() = Environment.getExternalStorageDirectory().path

    @Before fun setUp() {
        // Robolectric reports no free space, which would stop openDownloads at its storage check.
        ShadowStatFs.registerStats(storagePath, 1_000_000, 1_000_000, 1_000_000)
    }

    @After fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        ShadowStatFs.unregisterStats(storagePath)
    }

    private fun activity(): ActivityBase =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()

    private fun assertSameRoute(expected: Intent, actual: Intent) {
        assertEquals(expected.component, actual.component)
        val keys = expected.extras!!.keySet()
        assertTrue("expected a route extra", keys.isNotEmpty())
        keys.forEach { assertEquals("extra $it", expected.extras!!.get(it), actual.extras?.get(it)) }
    }

    @Test fun openDownloadsStartsDownloadRouteForResult() {
        val activity = activity()
        AndroidReadingNavigator({ activity }).openDownloads(addons = true)
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("no activity was started", started)
        assertSameRoute(NavHostComposeActivity.intentFor(activity, NavRoutes.download(addons = true)), started.intent)
        assertEquals(IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH, started.requestCode)
    }

    @Test fun openReadingProgressCarriesTheTabInTheRoute() {
        val activity = activity()
        AndroidReadingNavigator({ activity }).openReadingProgress(2)
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("no activity was started", started)
        assertSameRoute(NavHostComposeActivity.intentFor(activity, NavRoutes.readingProgress(2)), started.intent)
        assertEquals(STD_REQUEST_CODE, started.requestCode)
    }

    @Test fun openWorkspaceSelectorUsesWorkspaceChangedRequestCode() {
        val activity = activity()
        AndroidReadingNavigator({ activity }).openWorkspaceSelector()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("no activity was started", started)
        assertSameRoute(NavHostComposeActivity.intentFor(activity, NavRoutes.WORKSPACE_SELECTOR), started.intent)
        assertEquals(WORKSPACE_CHANGED, started.requestCode)
    }

    @Test fun openChooseDocumentStartsTheChooserRouteWithTheStdRequestCode() {
        val activity = activity()
        AndroidReadingNavigator({ activity }).openChooseDocument("bible")

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("no activity was started", started)
        assertSameRoute(NavHostComposeActivity.intentFor(activity, NavRoutes.chooseDocument("bible")), started.intent)
        assertEquals(STD_REQUEST_CODE, started.requestCode)
    }

    @Test fun assignLabelsReturnsNullWhenCancelled() {
        val activity = activity()
        val navigator = AndroidReadingNavigator({ activity })
        val answer = CompletableDeferred<List<IdType>?>()
        activity.lifecycleScope.launch(Dispatchers.Main) {
            answer.complete(navigator.assignLabels(mock<BookmarkEntities.BaseBookmarkWithNotes>(), emptyList()))
        }
        shadowOf(Looper.getMainLooper()).idle()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("the label chooser was not started", started)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("assignLabels did not answer", answer.isCompleted)
        assertNull(answer.getCompleted())
    }
}
