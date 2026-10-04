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
package net.bible.android.view.activity.installzip

import android.content.Intent
import android.net.Uri
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Slice 8 D3 (spec §3.2): the exported entry redirects to the nav host's INSTALL_ZIP route and finishes. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallZipRedirectTest {

    private val uri = Uri.parse("content://com.example.files/doc/module.zip")
    private val uri2 = Uri.parse("content://com.example.files/doc/a,b.epub")
    // Review Focus #5: a URI list with `,`, `%` and non-ASCII, round-tripped through the real
    // NavRoutes.installZip encoding (proven against the real NavHost in D1's InstallZipFlowTest).
    private val uri3 = Uri.parse("content://com.example.files/doc/50%25 säästö, käyttö.zip")

    @Test
    fun anExternalViewGoesToTheNavHostInTheSameTaskWithItsGrant() {
        val external = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        val redirect = Robolectric.buildActivity(InstallZipComposeActivity::class.java, external).create().get()

        val started = shadowOf(redirect).nextStartedActivity
        assertEquals(NavHostComposeActivity::class.java.name, started.component?.className)
        assertEquals(NavRoutes.installZip(Intent.ACTION_VIEW, listOf(uri.toString())), started.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        assertEquals("the URI travels with the Intent, which is what re-grants it", uri, started.data)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("same task: no FLAG_ACTIVITY_* travels", 0, started.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        assertTrue("the redirect finishes at once", redirect.isFinishing)
    }

    @Test
    fun aSendMultipleCarriesEveryUriInTheRoute() {
        val external = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/zip"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri, uri2))
        }
        val redirect = Robolectric.buildActivity(InstallZipComposeActivity::class.java, external).create().get()
        val started = shadowOf(redirect).nextStartedActivity
        assertEquals(
            NavRoutes.installZip(Intent.ACTION_SEND_MULTIPLE, listOf(uri.toString(), uri2.toString())),
            started.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun aMalformedIntentStartsNothingAndFinishes() {
        val redirect = Robolectric.buildActivity(InstallZipComposeActivity::class.java, Intent(Intent.ACTION_SEND)).create().get()
        assertNull(shadowOf(redirect).nextStartedActivity)
        assertTrue(redirect.isFinishing)
    }

    /** Review Focus #5: a URI list with `,`, `%` and non-ASCII survives the redirect's route encoding intact. */
    @Test
    fun aUriListWithCommaPercentAndNonAsciiSurvivesTheRoute() {
        val external = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/zip"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri2, uri3))
        }
        val redirect = Robolectric.buildActivity(InstallZipComposeActivity::class.java, external).create().get()
        val started = shadowOf(redirect).nextStartedActivity
        assertEquals(
            NavRoutes.installZip(Intent.ACTION_SEND_MULTIPLE, listOf(uri2.toString(), uri3.toString())),
            started.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    /**
     * D1 finding: `classifyEntry`/`classifyInstallZipRoute` diverge on an unknown non-null action with
     * no URIs -- `classifyEntry`'s `when` falls to its `else -> PickFile` arm (mirrors classic
     * `InstallZip.onCreate`'s dispatch, which showed the picker for any action it didn't recognise),
     * while `classifyInstallZipRoute` (used only once the redirect has already landed on the graph)
     * returns `Invalid` for the same shape. The redirect classifies with `classifyEntry`, so an
     * unrecognised external action reaches the SAME picker route as a bare in-app launch -- pinned here
     * so the two classifiers' divergence cannot silently change what the exported entry does.
     */
    @Test
    fun anUnrecognisedActionWithNoDataOpensThePickerLikeABareLaunch() {
        val external = Intent("com.example.SOME_OTHER_ACTION")
        val redirect = Robolectric.buildActivity(InstallZipComposeActivity::class.java, external).create().get()
        val started = shadowOf(redirect).nextStartedActivity
        assertEquals(NavRoutes.installZip(), started.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }
}
