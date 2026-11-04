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
package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedui.theme.AbTheme
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The workspace quick sheet on an e-ink device with animations off sometimes opened showing only its first row
 * (2026-11-04, Onyx Boox). Its rows arrive a moment after the sheet is composed, so the sheet grows while it opens.
 * With a zero motion-duration scale the opening slide lasts one frame; when the content grows in exactly that frame,
 * Material's anchor update cannot snap (the slide holds the sheet's drag mutex) and the slide then lands on the OLD
 * expanded offset: the taller sheet rests with its bottom below the window, nothing animating, nothing correcting it.
 *
 * Both no-animation paths are covered: Android, where the composition's effect context carries a zero
 * [MotionDurationScale] (the app setting via `AppWindowRecomposer`, or the system animator scale 0), and iOS/desktop,
 * where [AbModalBottomSheet]'s own takeover moves the sheet. The growth is placed on each of the first few frames,
 * because which frame it lands on is the race.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class AbModalBottomSheetContentGrowthTest {

    private object ZeroMotionDurationScale : MotionDurationScale {
        override val scaleFactor = 0f
    }

    private data class Settled(val value: SheetValue, val footerBottomPx: Float, val windowHeightPx: Float)

    private fun openAndGrowAfter(frames: Int, effectContext: CoroutineContext): Settled {
        var rows by mutableIntStateOf(0)
        lateinit var state: SheetState
        lateinit var settled: Settled
        runComposeUiTest(effectContext = effectContext) {
            mainClock.autoAdvance = false
            setContent {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    AbModalBottomSheet(onDismissRequest = {}, sheetState = state, showDragHandle = false) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("header", Modifier.height(48.dp))
                            repeat(rows) { Box(Modifier.fillMaxWidth().height(48.dp)) }
                            Text("footer", Modifier.height(48.dp).testTag(FOOTER))
                        }
                    }
                }
            }
            // Hidden rests at the window's full height.
            val windowHeight = state.requireOffset()
            repeat(frames) { mainClock.advanceTimeByFrame() }
            rows = 5
            mainClock.advanceTimeBy(5_000)
            val footer = onNodeWithTag(FOOTER, useUnmergedTree = true).getUnclippedBoundsInRoot()
            settled = Settled(state.currentValue, with(density) { footer.bottom.toPx() }, windowHeight)
        }
        return settled
    }

    private fun assertFullyOpenWhateverTheGrowthFrame(effectContext: CoroutineContext) {
        for (frames in 0..4) {
            val settled = openAndGrowAfter(frames, effectContext)
            assertEquals(SheetValue.Expanded, settled.value, "growth after $frames frames: $settled")
            assertEquals(settled.windowHeightPx, settled.footerBottomPx, 1f, "growth after $frames frames: $settled")
        }
    }

    @Test fun androidZeroScaleSheetOpensFullyWhenContentGrowsWhileOpening() =
        assertFullyOpenWhateverTheGrowthFrame(ZeroMotionDurationScale)

    @Test fun takeoverSheetOpensFullyWhenContentGrowsWhileOpening() =
        assertFullyOpenWhateverTheGrowthFrame(EmptyCoroutineContext)

    /**
     * The resettle must never re-open a sheet that a caller closes while (or right after) it opens. From frame 1: a
     * hide() before the first frame runs before Material's own show effect, which then opens the sheet regardless.
     */
    private fun assertHideRightAfterOpenStaysClosed(effectContext: CoroutineContext) {
        for (frames in 1..3) {
            lateinit var state: SheetState
            lateinit var scope: CoroutineScope
            var dismissCalls = 0
            runComposeUiTest(effectContext = effectContext) {
                mainClock.autoAdvance = false
                setContent {
                    AbTheme(darkTheme = false, disableAnimations = true) {
                        state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        scope = rememberCoroutineScope()
                        AbModalBottomSheet(onDismissRequest = { dismissCalls++ }, sheetState = state) {
                            Text("sheet", Modifier.height(200.dp))
                        }
                    }
                }
                repeat(frames) { mainClock.advanceTimeByFrame() }
                runOnIdle { scope.launch { state.hide() } }
                mainClock.advanceTimeBy(5_000)
                assertEquals(SheetValue.Hidden, state.currentValue, "hide after $frames frames")
                assertTrue(dismissCalls <= 1, "hide after $frames frames reported $dismissCalls dismisses")
            }
        }
    }

    @Test fun androidZeroScaleHideRightAfterOpenStaysClosed() =
        assertHideRightAfterOpenStaysClosed(ZeroMotionDurationScale)

    @Test fun takeoverHideRightAfterOpenStaysClosed() = assertHideRightAfterOpenStaysClosed(EmptyCoroutineContext)

    private companion object {
        const val FOOTER = "growth-footer"
    }
}
