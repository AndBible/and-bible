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

package net.bible.android.view.nav

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The coexistence seam's contract: a MIGRATED screen resolves to the nav host carrying its route,
 * an unmigrated one still resolves to its own Activity, and no screen is both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class NavHostRoutingGuardTest {

    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun aMigratedScreenResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.ToolInfo)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_TOOL_INFO, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiDocumentFilterResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiDocumentFilter)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_DOCUMENT_FILTER, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun globalToolPermissionsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.GlobalToolPermissions)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiModelsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiModels)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(NavRoutes.AI_MODELS, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }

    @Test
    fun aiConnectionSettingsResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiConnectionSettings)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.AI_CONNECTION_SETTINGS,
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun aiProvidersResolvesToTheNavHostCarryingItsRoute() {
        val intent = ScreenLauncher.intentFor(context, Screen.AiProviders)
        assertEquals(NavHostComposeActivity::class.java.name, intent.component?.className)
        assertEquals(
            NavRoutes.aiProviders(startEasySetup = false),
            intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE),
        )
    }

    @Test
    fun aiProvidersEasySetupRouteCarriesTheArgument() {
        val plainRoute = NavRoutes.aiProviders(startEasySetup = false)
        val easySetupRoute = NavRoutes.aiProviders(startEasySetup = true)
        assertTrue(
            easySetupRoute.contains("${NavRoutes.ARG_START_EASY_SETUP}=true"),
            "the easy-setup route must carry startEasySetup=true: $easySetupRoute",
        )
        assertTrue(
            plainRoute != easySetupRoute,
            "the easy-setup route must be distinguishable from the plain AiProviders route",
        )
    }

    @Test
    fun anUnmigratedScreenStillResolvesToItsOwnActivity() {
        val intent = ScreenLauncher.intentFor(context, Screen.Bookmarks)
        assertEquals(
            "net.bible.android.view.activity.bookmark.BookmarksComposeActivity",
            intent.component?.className,
        )
        assertTrue(intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE) == null)
    }

    @Test
    fun everyMigratedScreenHasARouteAndNoneIsBlank() {
        assertTrue(ScreenLauncher.MIGRATED.isNotEmpty())
        ScreenLauncher.MIGRATED.forEach { (screen, route) ->
            assertTrue(route.isNotBlank(), "$screen has a blank route")
        }
    }
}
