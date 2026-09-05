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

import android.app.Activity
import androidx.compose.runtime.State
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Spec 11.4 (resolved): a selector-originated launch of [TextDisplaySettingsComposeActivity] edits
 * a DETACHED copy of the named workspace's settings, never the active workspace or the Koin
 * singleton service -- see [DetachedWorkspaceEdit]'s kdoc. This pins the activity-side half of that
 * contract: which [SettingsScope] a detached launch resolves to, that it builds its own service
 * instance, and the result contract ([TextDisplaySettingsComposeActivity.finish]) returning
 * `settingsBundle` + `reset` only when the edit actually changed (plan D3).
 *
 * Reflection helpers over private state (`navStack`, `service`, `detachedEdit`) mirror the pattern
 * `TextDisplaySettingsComposeActivityColorsTest` uses for `colorsScope$delegate`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class TextDisplaySettingsComposeActivityDetachedTest {

    private fun detachedBundle(name: String = "Other workspace"): SettingsBundle =
        SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceId = IdType(), workspaceName = name,
            workspaceSettings = WorkspaceEntities.TextDisplaySettings(),
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )

    private fun scopeOf(activity: TextDisplaySettingsComposeActivity): SettingsScope {
        val field = TextDisplaySettingsComposeActivity::class.java.getDeclaredField("navStack\$delegate")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(activity) as State<List<SettingsScope>>
        return state.value.last()
    }

    private fun serviceOf(activity: TextDisplaySettingsComposeActivity): TextDisplaySettingsServiceImpl {
        val field = TextDisplaySettingsComposeActivity::class.java.getDeclaredField("service\$delegate")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val lazy = field.get(activity) as Lazy<TextDisplaySettingsServiceImpl>
        return lazy.value
    }

    private fun detachedEditOf(activity: TextDisplaySettingsComposeActivity): DetachedWorkspaceEdit? {
        val field = TextDisplaySettingsComposeActivity::class.java.getDeclaredField("detachedEdit\$delegate")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val lazy = field.get(activity) as Lazy<DetachedWorkspaceEdit?>
        return lazy.value
    }

    @Test
    fun aDetachedLaunchScopesToTheBundlesWorkspaceNotTheActiveOne() {
        val bundle = detachedBundle(name = "Other workspace")
        val intent = TextDisplaySettingsComposeActivity.intentForDetachedWorkspace(
            ApplicationProvider.getApplicationContext(), bundle.toJson(),
        )
        Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).use { c ->
            val activity = c.setup().get()
            val scope = scopeOf(activity)
            assertTrue(scope is SettingsScope.Workspace)
            assertEquals(bundle.workspaceId.toString(), (scope as SettingsScope.Workspace).workspaceId)
        }
    }

    @Test
    fun aDetachedLaunchDoesNotUseTheSharedService() {
        val bundle = detachedBundle()
        val intent = TextDisplaySettingsComposeActivity.intentForDetachedWorkspace(
            ApplicationProvider.getApplicationContext(), bundle.toJson(),
        )
        Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).use { c ->
            val activity = c.setup().get()
            val koinSingleton: TextDisplaySettingsServiceImpl = GlobalContext.get().get()
            assertNotSame(
                "a detached launch must build its own service instance -- a shared one leaks the " +
                    "edit into the reading view's in-place settings editor",
                koinSingleton, serviceOf(activity),
            )
        }
    }

    @Test
    fun closingAnUneditedDetachedScreenReturnsNoResult() {
        val bundle = detachedBundle()
        val intent = TextDisplaySettingsComposeActivity.intentForDetachedWorkspace(
            ApplicationProvider.getApplicationContext(), bundle.toJson(),
        )
        Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).use { c ->
            val activity = c.setup().get()
            activity.finish()
            val shadow = Shadows.shadowOf(activity)
            assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
            assertNull("plan D3: an unedited open must not mark the workspace changed", shadow.resultIntent)
        }
    }

    @Test
    fun anEditedDetachedScreenReturnsTheBundleAndResetFlag() {
        val bundle = detachedBundle()
        val intent = TextDisplaySettingsComposeActivity.intentForDetachedWorkspace(
            ApplicationProvider.getApplicationContext(), bundle.toJson(),
        )
        Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).use { c ->
            val activity = c.setup().get()
            detachedEditOf(activity)!!.markDirty()
            activity.finish()
            val shadow = Shadows.shadowOf(activity)
            assertEquals(Activity.RESULT_OK, shadow.resultCode)
            val returned = shadow.resultIntent.getStringExtra("settingsBundle")!!
            assertEquals(bundle.workspaceId, SettingsBundle.fromJson(returned).workspaceId)
            assertFalse(shadow.resultIntent.getBooleanExtra("reset", true))
        }
    }

    @Test
    fun aPlainLaunchIsUnaffected() {
        val intent = TextDisplaySettingsComposeActivity.intentFor(
            ApplicationProvider.getApplicationContext(), SettingsScope.Global,
        )
        Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).use { c ->
            val activity = c.setup().get()
            assertNull("a plain launch must have no detached edit", detachedEditOf(activity))
            assertSame(GlobalContext.get().get<TextDisplaySettingsService>(), serviceOf(activity))
            activity.finish()
            assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).resultCode)
        }
    }
}
