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

package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedui.reading.ReadingToolbar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingToolbarGoldenTest {

    // The icons and the no-op callbacks now live in ReadingToolbarGoldenFixtures.kt in this same
    // package, shared with ReadingToolbarSearchGoldenTest. Same values, so these goldens are
    // unchanged.

    // Requests only Bible + Search + Workspace (3 quick buttons) — comfortably fits even at the
    // default portrait budget (maxButtons=3 at 320dp), so on a wide (land) render every requested
    // button is visible with room to spare: a clean "nothing truncated" state.
    private val fullState = ToolbarState(
        pageTitle = "Genesis 1:1-3",
        documentTitle = "King James Version (KJV)",
        syncRunning = false,
        showBible = true,
        showCommentary = false,
        showStrongs = false,
        strongsMode = 1,
        searchable = true,
        speakable = false,
        speakStopped = true,
    )

    // Requests all 6 possible quick buttons (Bible/Commentary/Strongs/Search/Speak/Workspace).
    // Rendered at the default (narrow, portrait) viewport this exceeds the width budget
    // (maxButtons=3), so fitToolbarButtons must drop Search/Speak/Workspace — the overflow
    // button stays visible regardless, so the row shows exactly 3 quick buttons + overflow.
    private val narrowState = ToolbarState(
        pageTitle = "Genesis 1:1-3, a longer page title to exercise ellipsis",
        documentTitle = "King James Version (KJV)",
        syncRunning = false,
        showBible = true,
        showCommentary = true,
        showStrongs = true,
        strongsMode = 1,
        searchable = true,
        speakable = true,
        speakStopped = true,
    )

    // Requests only Bible + Strongs + Search (3 quick buttons, comfortably fits) with
    // strongsMode=0 ("Strong's numbers off") — exercises QuickToolbarButton's
    // `alpha = if (state.strongsMode == 0) 0.5f else 1f` dimmed-icon branch, which `fullState`
    // (strongsMode=1, showStrongs=false) and `narrowState` (strongsMode=1) never hit.
    private val dimmedStrongsState = ToolbarState(
        pageTitle = "Genesis 1:1-3",
        documentTitle = "King James Version (KJV)",
        syncRunning = false,
        showBible = true,
        showCommentary = false,
        showStrongs = true,
        strongsMode = 0,
        searchable = true,
        speakable = false,
        speakStopped = true,
    )

    // A/B batch 3 F3: a workspace colour the user actually chose (NOT defaultWorkspaceColor), so
    // the toolbar renders its opt-in tint — full strength in light, blended into the surface in
    // dark, greyscaled in BW, coloured in eink. The other cases leave workspaceColorArgb null and
    // must therefore render exactly as before.
    private val workspaceColorState = fullState.copy(workspaceColorArgb = 0xFF1B5E20.toInt())

    private fun screen(state: ToolbarState): @Composable () -> Unit = {
        ReadingToolbar(state = state, icons = goldenToolbarIcons(), callbacks = goldenToolbarCallbacks())
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun full() = captureMatrix("ReadingToolbar", "full", content = screen(fullState))

    @Test
    fun narrow() = captureGolden("ReadingToolbar", "narrow", EDGE_MODE, content = screen(narrowState))

    // Default (narrow, portrait) viewport — proves the sync indicator is visible even in the
    // common-case squeezed title column. Fixed in ReadingToolbar.kt: the document-title Text now
    // carries `Modifier.weight(1f, fill = false)` inside the title Row, so it yields width to the
    // non-weighted trailing SyncIndicator instead of claiming the whole column (a Row measures
    // non-weighted children first and reserves their size before dividing the remainder among
    // weighted ones). Previously this case was re-recorded with `land` qualifiers to paper over the
    // squeeze rather than fix it — see fix wave 2.
    @Test
    fun syncing() = captureGolden("ReadingToolbar", "syncing", EDGE_MODE, content = screen(fullState.copy(syncRunning = true)))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun dimmed_strongs() = captureGolden("ReadingToolbar", "dimmed_strongs", EDGE_MODE, content = screen(dimmedStrongsState))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun narrow_rtl() = captureRtl("ReadingToolbar", "narrow", content = screen(narrowState))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun workspace_color() = captureMatrix("ReadingToolbar", "workspace_color", content = screen(workspaceColorState))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun full_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingToolbar", "full", mode, content = screen(fullState)) }
    }

    @Test
    fun narrow_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingToolbar", "narrow", mode, content = screen(narrowState)) }
    }

    @Test
    fun syncing_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingToolbar", "syncing", mode, content = screen(fullState.copy(syncRunning = true))) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun dimmed_strongs_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingToolbar", "dimmed_strongs", mode, content = screen(dimmedStrongsState)) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun workspace_color_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingToolbar", "workspace_color", mode, content = screen(workspaceColorState)) }
    }
}
