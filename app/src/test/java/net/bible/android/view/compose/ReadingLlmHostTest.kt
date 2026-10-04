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

import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.sharedcore.ai.reading.ReadingLlmDialog
import net.bible.sharedcore.ai.reading.ReadingLlmDialogState
import net.bible.sharedcore.ai.reading.ReadingPromptGroupVd
import net.bible.sharedcore.ai.reading.ReadingPromptVd
import net.bible.sharedcore.window.WindowCommands
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** No-op fake — mirrors `ComposeReadingViewHostTest`'s own private `noopCommands` (not reusable
 *  across files: that one is file-private). No mocking framework is used in this repo. */
private val noopCommands = object : WindowCommands {
    override fun setActive(windowId: String) {}
    override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {}
    override fun addNewWindow(fromWindowId: String) {}
    override fun minimise(windowId: String) {}
    override fun close(windowId: String) {}
    override fun restore(windowId: String) {}
    override fun maximise(windowId: String) {}
    override fun unMaximise() {}
    override fun setPin(windowId: String, value: Boolean) {}
    override fun move(windowId: String, position: Int) {}
    override fun setSynchronised(windowId: String, value: Boolean) {}
    override fun changeSyncGroup(windowId: String, group: Int) {}
    override fun focusNext() {}
    override fun focusPrevious() {}
    override fun setRestoreButtonsVisible(value: Boolean) {}
}

/**
 * Probe test (Batch 12e-A Task 6): `ComposeReadingViewHost.mountComposeView` must accept the
 * reading-view LLM dialog state (`net.bible.sharedcore.ai.reading.ReadingLlmDialogState`) + its
 * callbacks as new defaulted params — threaded through so `ComposeReadingViewHost.install` can
 * render `net.bible.sharedui.ai.reading.ReadingLlmDialogs` as a sibling of `ReadingViewScreen` —
 * and must mount without crashing. Mirrors `ComposeReadingViewHostTest`'s minimal-mount
 * `installAccepts*` style: this repo's `:app` JVM unit tests have no `ComposeTestRule`, so
 * composition itself never runs for a container that is never attached to a real window (see that
 * test class's kdoc) — the actual dialog-rendering pixels are covered by
 * `ReadingLlmDialogsGoldenTest`, and the dialog state machine by `ReadingLlmDialogControllerTest`
 * (`:sharedCore`). What THIS test guards is purely the host wiring: the new params exist, compile,
 * and reach `mountComposeView` without the mount itself invoking any of them (same "caller-owned,
 * never invoked at mount time" boundary as the existing overflow/pane-menu params).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ReadingLlmHostTest {

    @Test fun mountAcceptsLlmDialogStateAndCallbacksWithoutInvokingThem() {
        val container = FrameLayout(ApplicationProvider.getApplicationContext())
        val oneGroup = listOf(
            ReadingPromptGroupVd(
                categoryName = "Study",
                categoryId = "c1",
                isFavorites = false,
                collapsed = false,
                prompts = listOf(
                    ReadingPromptVd("p1", "Explain verse", "", isFavorite = false, specifyBeforeRun = false),
                ),
            )
        )
        val llmDialogState = MutableStateFlow(ReadingLlmDialogState(ReadingLlmDialog.PromptSelector(oneGroup)))
        var promptChosenWith: String? = null
        var favoriteToggledWith: String? = null
        var categoryChangedWith: Pair<String?, Boolean>? = null
        var specifySubmittedWith: String? = null
        var modelChosenWith: Pair<String, Boolean>? = null
        var regenerateConfirmedWith: Triple<String?, Boolean, Boolean>? = null
        var dismissed = false

        ComposeReadingViewHost.mountComposeView(
            container = container,
            windowState = WindowStateServiceImpl(),
            commands = noopCommands,
            nightModeState = mutableStateOf(false),
            pane = { },
            llmDialogState = llmDialogState,
            onLlmPromptChosen = { promptChosenWith = it },
            onLlmToggleFavorite = { favoriteToggledWith = it },
            onLlmCategoryExpandedChanged = { categoryId, expanded -> categoryChangedWith = categoryId to expanded },
            onLlmSpecifySubmitted = { specifySubmittedWith = it },
            onLlmModelChosen = { modelId, setDefault -> modelChosenWith = modelId to setDefault },
            onLlmRegenerateConfirmed = { instructions, keepPrevious, freshRun ->
                regenerateConfirmedWith = Triple(instructions, keepPrevious, freshRun)
            },
            onLlmDismiss = { dismissed = true },
        )

        assertTrue(
            (0 until container.childCount).any { container.getChildAt(it) is ComposeView },
            "mountComposeView must still add its ComposeView child with the new LLM params present",
        )
        // None of the caller-owned callbacks fire from the mount call itself (composition never
        // runs for a container never attached to a real window — see class kdoc).
        assertNull(promptChosenWith)
        assertNull(favoriteToggledWith)
        assertNull(categoryChangedWith)
        assertNull(specifySubmittedWith)
        assertNull(modelChosenWith)
        assertNull(regenerateConfirmedWith)
        assertFalse(dismissed)
    }
}
