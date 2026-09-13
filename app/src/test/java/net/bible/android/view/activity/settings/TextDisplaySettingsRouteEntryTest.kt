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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TextDisplaySettingsRouteEntry] -- the successor to the two halves of classic
 * `TextDisplaySettingsComposeActivityDetachedTest` that nav-graph slice 7 Task 13 deletes along with
 * the Activity: `aDetachedLaunchDoesNotUseTheSharedService`, and the `finish()` result contract's
 * unedited-returns-nothing / edited-returns-the-bundle pair (plan D3).
 *
 * Task 5 ported both into a private method of `NavHostComposeActivity`, where nothing could reach
 * them; fix round 1 lifted them into a class a constructor call away. What was a `Robolectric
 * .buildActivity(...)` of a whole settings Activity is now three lines, and the result contract is
 * driven the way a user drives it -- through a WRITE on the entry's own service -- rather than by
 * reflecting on a private `detachedEdit` and calling `markDirty()` by hand, as classic had to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TextDisplaySettingsRouteEntryTest {

    private lateinit var sharedService: TextDisplaySettingsServiceImpl

    @Before fun setUp() {
        val windowControl = CommonUtils.windowControl
        windowControl.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.activeWindow              // auto-creates window 1 + workspace
        sharedService = TextDisplaySettingsServiceImpl()
    }

    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private fun workspaceBundle(): SettingsBundle = SettingsBundle(
        level = SettingsLevel.WORKSPACE,
        workspaceId = IdType(),
        workspaceName = "Other workspace",
        workspaceSettings = WorkspaceEntities.TextDisplaySettings(),
        globalSettings = CommonUtils.globalTextDisplaySettings,
    )

    private fun detachedEntry(bundle: SettingsBundle) = TextDisplaySettingsRouteEntry(
        NavRoutes.readTextDisplaySettings(NavRoutes.textDisplaySettings(settingsBundle = bundle.toJson())),
        sharedService,
    )

    /**
     * Classic's `aDetachedLaunchDoesNotUseTheSharedService`. A shared instance would leak the
     * selector's staged edit into the reading view's in-place settings editor, which holds the same
     * singleton -- and, the other way round, would let the live editor's writes land in a workspace
     * the user is only previewing.
     *
     * Mutation this catches: `detachedEdit?.let { TextDisplaySettingsServiceImpl(it) } ?: shared`
     * collapsing to `shared`.
     */
    @Test
    fun aDetachedEntryBuildsItsOwnServiceInstance() {
        val entry = detachedEntry(workspaceBundle())
        assertNotSame(
            "a detached entry must not edit through the shared service",
            sharedService, entry.service,
        )
    }

    @Test
    fun aPlainEntryUsesTheSharedServiceAndHasNoDetachedEdit() {
        val entry = TextDisplaySettingsRouteEntry(
            NavRoutes.readTextDisplaySettings(NavRoutes.textDisplaySettings(scopeLevel = "global")),
            sharedService,
        )
        assertNull(entry.detachedEdit)
        assertSame(sharedService, entry.service)
        assertEquals(SettingsScope.Global, entry.scope)
    }

    /**
     * Plan D3, the half classic's own port got wrong before it: merely OPENING the editor and
     * leaving must publish nothing, or the selector marks the workspace changed on its next Save
     * (which is exactly what classic did before the condition existed -- it called `setResult` at
     * the end of `loadSettingsBundle`).
     *
     * Mutation this catches: dropping `?.takeIf { it.changed }`.
     */
    @Test
    fun leavingAnUneditedDetachedEntryPublishesNothing() {
        assertNull(detachedEntry(workspaceBundle()).resultOnLeave())
    }

    /**
     * The other half: a real edit comes back as the bundle the selector applies, with `reset` false.
     * Driven through `entry.service` rather than by poking the edit, which also pins that the
     * entry's service really is bound to the entry's own [DetachedWorkspaceEdit] -- a service built
     * against some other edit would leave this one unchanged and publish nothing.
     */
    @Test
    fun leavingAnEditedDetachedEntryPublishesTheBundleAndResetFlag() {
        val bundle = workspaceBundle()
        val entry = detachedEntry(bundle)

        entry.service.setValue(entry.scope, TextSettingType.JUSTIFY, TextSettingValue.BoolValue(true))

        val result = entry.resultOnLeave()
        assertTrue("an edit made through this entry's service must be published", result != null)
        assertEquals(bundle.workspaceId, SettingsBundle.fromJson(result!!.settingsBundleJson).workspaceId)
        assertFalse("a value edit is not a whole-scope reset", result.reset)
    }

    /** A whole-scope reset is the same result with `reset` set -- the flag the selector needs to
     *  tell "apply these settings" from "clear this workspace's settings". */
    @Test
    fun aWholeScopeResetPublishesTheResetFlag() {
        val entry = detachedEntry(workspaceBundle())
        entry.service.reset(entry.scope)
        val result = entry.resultOnLeave()
        assertTrue(result != null)
        assertTrue("reset must survive to the selector", result!!.reset)
    }

    /** The detached entry scopes to the BUNDLE's workspace, never the active one -- classic's
     *  `aDetachedLaunchScopesToTheBundlesWorkspaceNotTheActiveOne`, via [scopeFromRoute]. */
    @Test
    fun aDetachedEntryScopesToTheBundlesWorkspace() {
        val bundle = workspaceBundle()
        assertEquals(SettingsScope.Workspace(bundle.workspaceId.toString()), detachedEntry(bundle).scope)
    }

    /** The ambiguity guard fires through the entry too, not only through a direct [scopeFromRoute]
     *  call -- the entry is what production actually builds. */
    @Test
    fun anEntryThatNamesBothAScopeLevelAndABundleIsRejected() {
        val args = TextDisplaySettingsArgs(
            scopeLevel = "global",
            settingsBundleJson = workspaceBundle().toJson(),
        )
        val thrown = runCatching { TextDisplaySettingsRouteEntry(args, sharedService) }.exceptionOrNull()
        assertTrue(
            "an ambiguous route must fail loudly, not resolve to one of the two: $thrown",
            thrown is IllegalArgumentException,
        )
    }
}
