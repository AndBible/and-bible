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

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.TEST_SDK
import net.bible.android.view.compose.golden.captureMatrix
import net.bible.android.view.compose.golden.captureRtl
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.ReadingProgressSettingsController
import net.bible.sharedcore.settings.ReadingProgressSettingsLabels
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.sharedcore.settings.ReadingProgressSettingsSnapshot
import net.bible.sharedui.settings.AbSettingsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the [ReadingProgressSettingsComposeActivity] screen (Batch 10c) — the classic
 * category-less reading-progress/memorization settings screen (5 switches + 1 list-choice row).
 * Renders through the REAL [ReadingProgressSettingsController] (a fake
 * [ReadingProgressSettingsService] feeds it a hand-built [ReadingProgressSettingsSnapshot]),
 * exactly as [AppSettingsGoldenTest] does for [net.bible.sharedcore.settings.AppSettingsController].
 * The screen is flat with no conditional states, so only one representative fixture is needed
 * (4 theme modes + Arabic RTL — no edge-state variants).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingProgressSettingsGoldenTest {

    /** Minimal fake: the golden only needs a fixed snapshot, so writes are no-ops. */
    private class FakeReadingProgressSettingsService(
        initial: ReadingProgressSettingsSnapshot,
    ) : ReadingProgressSettingsService {
        override val snapshot: StateFlow<ReadingProgressSettingsSnapshot> = MutableStateFlow(initial)
        override fun setBool(key: String, value: Boolean) {}
        override fun setString(key: String, value: String) {}
        override fun refresh() {}
    }

    /** Representative baseline snapshot: a mix of on/off switches and a chosen list value. */
    private fun baseSnapshot() = ReadingProgressSettingsSnapshot(
        autoMarkMemorized = true,
        memorizeTypeFullWords = false,
        memorizeWordVisibility = "normal",
        memorizeWordVisibilityChoices = listOf(
            Choice2("none", "Hidden"),
            Choice2("first_letter", "First letter"),
            Choice2("normal", "Normal"),
            Choice2("full", "Full word"),
        ),
        memorizeErrorHeatmap = true,
        memorizeScrambleHideUsed = false,
        memorizeIncludeReference = true,
    )

    /** Builds the real [ReadingProgressSettingsController]'s state for [snapshot] (a fresh,
     *  uncollected scope is fine: the constructor computes `state.value` synchronously from
     *  `service.snapshot.value` before the background collector ever runs, and the fake service
     *  never emits again). */
    private fun stateFor(snapshot: ReadingProgressSettingsSnapshot) =
        ReadingProgressSettingsController(
            service = FakeReadingProgressSettingsService(snapshot),
            scope = CoroutineScope(Job()),
            labels = ReadingProgressSettingsLabels.forTest(),
        ).state.value

    private fun screen(snapshot: ReadingProgressSettingsSnapshot): @Composable () -> Unit = {
        AbSettingsScreen(
            state = stateFor(snapshot),
            onUp = {},
            onSwitch = { _, _ -> },
            onListChoice = { _, _ -> },
            onTextInput = { _, _ -> },
            onNavigate = {},
        )
    }

    // heightDp=700: the flat 6-row screen (5 two-line switches + 1 list-choice row) clips the last
    // row ("Include reference") out of the default viewport; render the whole thing.
    @Test fun baseline_matrix() =
        captureMatrix("ReadingProgressSettings", "baseline", heightDp = 700, content = screen(baseSnapshot()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun baseline_rtl() =
        captureRtl("ReadingProgressSettings", "baseline", heightDp = 700, content = screen(baseSnapshot()))
}
