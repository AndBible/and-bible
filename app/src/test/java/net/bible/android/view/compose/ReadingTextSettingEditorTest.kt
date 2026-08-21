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
package net.bible.android.view.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextSettingType
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Settings editor sheets T10 — the `:app` host wiring for the reading view's in-place
 * text-settings editor ([ComposeReadingViewHost.showTextSettingEditor]).
 *
 * `:app` has no `ComposeTestRule` and no golden covers `ComposeReadingViewHost` (same limit
 * `ReadingSearchHostTest` documents), so these tests drive the host API directly — the callable,
 * tested surface this task is required to leave behind, with nothing yet wired to call it (Task 11
 * owns the two menu dispatch sites).
 *
 * Host construction/Koin setup is copied verbatim from `ReadingSearchHostTest.setUpRealHost`/
 * `tearDownRealHost` — a real [MainBibleActivity]/[WindowControl]/[WindowRepository] graph, the
 * activity built WITHOUT `.create()`, the host constructed directly and never `.install()`ed.
 */
@RunWith(RobolectricTestRunner::class)
// TestBibleApplication, not the bare Application the other `*HostTest`s use — see
// ReadingSearchHostTest's own kdoc for why (Book.isEpub's static initializer requirement doesn't
// bite here, but this mirrors that fixture exactly rather than inventing a leaner one).
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingTextSettingEditorTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: MainBibleActivity

    /** Mirrors `ReadingSearchHostTest.setUpRealHost`/`tearDownRealHost` verbatim. */
    @Before
    fun setUpRealHost() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(activity)
    }

    @After
    fun tearDownRealHost() {
        CurrentActivityHolder.deactivate(activity)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun host() = ComposeReadingViewHost(activity)

    @Test
    fun showTextSettingEditorOpensTheStackAtThatPage() {
        val host = host()

        host.showTextSettingEditor(
            SettingsScope.Workspace("ws"),
            SettingsEditorPage.Row(TextSettingType.FONTSIZE.name),
        ) { }

        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
        assertEquals(1, host.textSettingsEditor.depth)
    }

    @Test
    fun openingASecondEditorReplacesTheFirstStackRatherThanStackingOnIt() {
        val host = host()

        host.showTextSettingEditor(SettingsScope.Workspace("ws"), SettingsEditorPage.Colors) { }
        host.textSettingsEditor.push(SettingsEditorPage.ColorPick(ColorField.DAY_TEXT))
        host.showTextSettingEditor(SettingsScope.Workspace("ws"), SettingsEditorPage.Row("FONTSIZE")) { }

        assertEquals(1, host.textSettingsEditor.depth)
        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
    }
}
