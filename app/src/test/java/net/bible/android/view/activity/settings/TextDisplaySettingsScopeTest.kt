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
package net.bible.android.view.activity.settings

import java.io.File
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.SettingsScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The text-display-settings destination's SCOPE resolution, and the defect nav-graph slice 7 fixes
 * in passing (design §3.2 item 2).
 *
 * **The defect.** `NavHostComposeActivity`'s global-text-settings row built an
 * `Screen.TextDisplaySettings` Intent and hung a detached settings bundle on it under the extra key
 * the classic Activity uses for a SELECTOR-originated edit — they are the same string. The classic
 * `scopeFromIntent` tested that extra FIRST, so the "global settings" row actually opened a DETACHED
 * edit of a workspace, and a `SettingsLevel.GLOBAL` bundle carries `IdType.empty()` as its workspace
 * id (`WorkspaceEntities.kt:780`), so the scope was an empty workspace rather than GLOBAL. The
 * detached edit's echo then went nowhere, because the caller used `startActivity` and had no result
 * to read. The kdoc above that row asserted the opposite ("GLOBAL scope comes from the ABSENT
 * scope-level extra"), so the comment was wrong too.
 *
 * **Why it cannot recur.** A route argument is named. `scopeLevel` and `settingsBundle` are two
 * distinct arguments of [NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN], and the global row now sets only
 * the first — [theGlobalSettingsRowOpensGlobalScopeNotADetachedEdit] would fail the moment either
 * half of that regressed (the row re-acquiring a bundle, or [scopeFromRoute] re-acquiring the old
 * precedence over an explicit scope level).
 *
 * [aDetachedBundleStillWinsWhenItIsTheOnlyThingSaidAboutScope] and
 * [aGlobalBundleAsADetachedEditIsTheEmptyWorkspace] keep the classic precedence and the defect's own
 * mechanism as LIVE premises rather than claims in a comment: the detached branch is a real launch
 * shape (the workspace selector's round trip), and it must go on behaving exactly as it did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class TextDisplaySettingsScopeTest {

    private fun workspaceBundle(id: IdType): SettingsBundle = SettingsBundle(
        level = SettingsLevel.WORKSPACE,
        workspaceId = id,
        workspaceName = "Other workspace",
        workspaceSettings = WorkspaceEntities.TextDisplaySettings(),
        globalSettings = WorkspaceEntities.TextDisplaySettings(),
    )

    private fun scopeOf(route: String): SettingsScope =
        scopeFromRoute(NavRoutes.readTextDisplaySettings(route))

    @Test
    fun theGlobalSettingsRowOpensGlobalScopeNotADetachedEdit() {
        val route = NavRoutes.textDisplaySettings(scopeLevel = "global")
        assertFalse(
            "the global row's route must carry no detached bundle at all — that argument being " +
                "present is the whole of the defect",
            route.contains(NavRoutes.ARG_SETTINGS_BUNDLE),
        )
        assertEquals(SettingsScope.Global, scopeOf(route))
    }

    @Test
    fun aWindowScopeRouteCarriesBothIdsThroughToTheScope() {
        val windowId = IdType().toString()
        val workspaceId = IdType().toString()
        val route = NavRoutes.textDisplaySettings(
            scopeLevel = "window", windowId = windowId, workspaceId = workspaceId,
        )
        assertEquals(SettingsScope.Window(windowId, workspaceId), scopeOf(route))
    }

    @Test
    fun aWorkspaceScopeRouteCarriesItsWorkspaceIdThroughToTheScope() {
        val workspaceId = IdType().toString()
        val route = NavRoutes.textDisplaySettings(scopeLevel = "workspace", workspaceId = workspaceId)
        assertEquals(SettingsScope.Workspace(workspaceId), scopeOf(route))
    }

    @Test
    fun aDetachedWorkspaceEditIsADifferentArgumentFromAScopeLevel() {
        val id = IdType()
        val route = NavRoutes.textDisplaySettings(settingsBundle = workspaceBundle(id).toJson())
        assertTrue(
            "a detached bundle is JSON, so the route must percent-encode it rather than let its " +
                "braces and quotes split the query",
            route.contains(NavRoutes.ARG_SETTINGS_BUNDLE),
        )
        assertFalse("the detached route says nothing about a scope level", route.contains("scopeLevel="))
        assertEquals(SettingsScope.Workspace(id.toString()), scopeOf(route))
    }

    @Test
    fun aDetachedBundleStillWinsWhenItIsTheOnlyThingSaidAboutScope() {
        // Classic's documented precedence, preserved: a detached launch scopes to the BUNDLE's own
        // workspace, never the active one. Keeping it is safe now only because the global row no
        // longer sets a bundle -- see this class's kdoc.
        val id = IdType()
        val route = NavRoutes.textDisplaySettings(
            scopeLevel = "window",
            windowId = IdType().toString(),
            workspaceId = IdType().toString(),
            settingsBundle = workspaceBundle(id).toJson(),
        )
        assertEquals(SettingsScope.Workspace(id.toString()), scopeOf(route))
    }

    @Test
    fun aGlobalBundleAsADetachedEditIsTheEmptyWorkspace() {
        // The defect's mechanism itself, as a live premise: this is what the global row USED to
        // resolve to. It is still the honest answer for a route that really does ask for a detached
        // edit of a GLOBAL bundle -- nothing builds one, and the row that used to no longer can.
        val route = NavRoutes.textDisplaySettings(
            settingsBundle = SettingsBundle(level = SettingsLevel.GLOBAL).toJson(),
        )
        assertEquals(SettingsScope.Workspace(IdType.empty().toString()), scopeOf(route))
    }

    @Test
    fun startAtColoursSurvivesTheRouteRoundTripBothWays() {
        assertTrue(NavRoutes.readTextDisplaySettings(NavRoutes.textDisplaySettings(startAtColors = true)).startAtColors)
        assertFalse(NavRoutes.readTextDisplaySettings(NavRoutes.textDisplaySettings()).startAtColors)
    }

    /**
     * The host-side half of the fix, as a source scan — the only shape available, since the row's
     * lambda can only run inside a launched host. Both halves matter: the row must NAVIGATE to the
     * global route, and the classic `Screen` launch it used to build (the thing that carried the
     * bundle) must be gone from the host entirely.
     */
    @Test
    fun theHostsGlobalTextSettingsRowNavigatesToTheGlobalRoute() {
        val source = File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")
        assertTrue("cannot find ${source.absolutePath}", source.isFile)
        val text = source.readText()
        assertTrue(
            "the global text-settings row must navigate to the global route",
            text.contains("""NavRoutes.textDisplaySettings(scopeLevel = "global")"""),
        )
        assertFalse(
            "the host still launches the classic text-settings Screen — that launch is what hung " +
                "a detached bundle on the global row",
            text.contains("Screen." + "TextDisplaySettings"),
        )
    }
}
