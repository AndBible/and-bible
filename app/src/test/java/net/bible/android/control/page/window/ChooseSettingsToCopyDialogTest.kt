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
package net.bible.android.control.page.window

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Task 19 Step 1: [WindowControl.chooseSettingsToCopy] (one of spec §3.2 finding 9's three
 * hand-written select-all/none dialogs) is now [net.bible.android.view.activity.base.Dialogs.multiselect]
 * — a [AppDialogRequest.MultiChoice] on the app-wide [AppDialogController], not a hand-built
 * `AlertDialog`. Covers the request's fields and each of [WindowControl.copySettingsToWorkspace],
 * [WindowControl.copySettingsToGlobal] and [WindowControl.copySettingsToWindow]'s branches.
 *
 * **A bare [ActivityBase], not `CalculatorComposeActivity`, on purpose.** `chooseSettingsToCopy` only
 * needs `CurrentActivityHolder.currentActivity` (for the context and `WindowControl.scope`'s
 * `lifecycleScope`) — no Compose content, so no `AppDialogOverlay`. `copySettingsToWorkspace` et al.
 * launch on the REAL `Dispatchers.Main` (this class builds no test dispatcher), so [idle] genuinely
 * has to pump the Robolectric main Looper to make them run; doing that while a real `AppDialogOverlay`
 * has a sheet-shaped `MultiChoice` request pending hangs forever (the `ModalBottomSheet`/`Popup`
 * re-posts a frame task via `PopupLayout.pollForLocationOnScreenChange` that a paused Looper never
 * finishes draining — same trap `CommonUtilsDialogsTest`'s bare `ThreadRecordingActivity` sidesteps
 * for the same reason). A bare `ActivityBase` composes nothing, so idling it is safe regardless of
 * what is pending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ChooseSettingsToCopyDialogTest {
    /** See the class KDoc: no Compose content, so no `AppDialogOverlay` to hang the Looper on. */
    private class BareActivity : ActivityBase()

    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        Robolectric.buildActivity(BareActivity::class.java).also { controllers += it }.setup()
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        dialogs.cancelAll()
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test(timeout = 30000)
    fun postsAMultiChoiceWithNothingPreselectedAndTheSelectAllNoneTexts() {
        val window = windowControl.activeWindow
        windowControl.copySettingsToWorkspace(window)
        idle()

        val request = dialogs.pending.value!!.request as AppDialogRequest.MultiChoice
        val expectedContext = RuntimeEnvironment.getApplication()
        assertEquals(expectedContext.getString(R.string.copy_settings_title), request.title)
        assertEquals(WorkspaceEntities.TextDisplaySettings.Types.values().size, request.options.size)
        assertTrue("nothing is preselected, as today", request.selectedIds.isEmpty())
        assertEquals(expectedContext.getString(R.string.okay), request.confirmText)
        assertEquals(expectedContext.getString(R.string.cancel), request.dismissText)
        assertEquals(expectedContext.getString(R.string.select_all), request.selectAllText)
        assertEquals(expectedContext.getString(R.string.select_none), request.selectNoneText)
        // The shim indexes options positionally -- Dialogs.multiselect maps ids back through the
        // ORIGINAL items list, not through the order ids come back in.
        assertEquals((0 until request.options.size).map { it.toString() }, request.options.map { it.value })
    }

    @Test(timeout = 30000)
    fun copySettingsToWorkspaceCopiesOnlyTheSelectedTypes() {
        val window = windowControl.activeWindow
        window.pageManager.textDisplaySettings.showFootNotes = true
        windowRepository.textDisplaySettings.showFootNotes = false

        windowControl.copySettingsToWorkspace(window)
        idle()

        val footnotesIndex = WorkspaceEntities.TextDisplaySettings.Types.values().indexOf(WorkspaceEntities.TextDisplaySettings.Types.FOOTNOTES)
        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.SelectedMany(listOf(footnotesIndex.toString())))
        idle()

        assertEquals(true, windowRepository.textDisplaySettings.showFootNotes)
    }

    @Test(timeout = 30000)
    fun copySettingsToWorkspaceCancelCopiesNothing() {
        val window = windowControl.activeWindow
        window.pageManager.textDisplaySettings.showFootNotes = true
        windowRepository.textDisplaySettings.showFootNotes = false

        windowControl.copySettingsToWorkspace(window)
        idle()

        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.Cancel)
        idle()

        assertEquals(
            "Cancel must copy nothing -- the target keeps whatever it already had",
            false, windowRepository.textDisplaySettings.showFootNotes,
        )
    }

    @Test(timeout = 30000)
    fun copySettingsToGlobalWithNothingSelectedLeavesTheGlobalRowUntouched() {
        val window = windowControl.activeWindow
        val before = CommonUtils.globalTextDisplaySettings

        windowControl.copySettingsToGlobal(window)
        idle()

        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.SelectedMany(emptyList()))
        idle()

        assertEquals(
            "an empty selection must be a no-op -- the existing dirtyTypes.isEmpty() guard",
            before, CommonUtils.globalTextDisplaySettings,
        )
    }

    @Test(timeout = 30000)
    fun copySettingsToWindowCopiesOnlyTheSelectedTypesIntoTheSecondWindow() {
        val window = windowControl.activeWindow
        val secondWindow = windowControl.addNewWindow(window)
        window.pageManager.textDisplaySettings.showFootNotes = true
        secondWindow.pageManager.textDisplaySettings.showFootNotes = false

        val order = windowRepository.visibleWindows.indexOf(secondWindow)
        windowControl.copySettingsToWindow(window, order)
        idle()

        val footnotesIndex = WorkspaceEntities.TextDisplaySettings.Types.values().indexOf(WorkspaceEntities.TextDisplaySettings.Types.FOOTNOTES)
        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.SelectedMany(listOf(footnotesIndex.toString())))
        idle()

        assertEquals(true, secondWindow.pageManager.textDisplaySettings.showFootNotes)
    }
}
