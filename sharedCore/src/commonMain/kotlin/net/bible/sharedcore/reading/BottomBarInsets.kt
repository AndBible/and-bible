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

package net.bible.sharedcore.reading

/**
 * Whether the agent-log panel is the bottom-most visible bar and must therefore consume the bottom
 * navigation-bar inset inside its own painted surface.
 *
 * Round 12b §3 replaces the old `bottomInsetReserved` + unpainted `Spacer` arrangement. The whole
 * Compose reading tree is edge-to-edge: the toolbar consumes `statusBars` itself and the floating
 * window rail consumes `navigationBars` itself, so the bottom-most in-flow child has to consume the
 * bottom inset. Reserving it with a bare `Spacer` after the bars did reserve the right amount of
 * SPACE but painted nothing there, so the strip showed `BottomSheetScaffold`'s default `surface`
 * while the panel right above it is `surfaceColorAtElevation(3.dp)` — a visible seam, which is the
 * reported defect. Padding inside the owning bar's surface makes the colour and the panel's rounded
 * top corners come out right for free.
 *
 * Neither bar can decide this alone — both hide themselves, so neither knows whether it is the
 * bottom-most one, and padding both would leave dead space between them whenever both are visible.
 * The speak bar sits below the panel in `ReadingViewScreen`'s `Column`, so it wins whenever visible.
 *
 * With neither bar visible nobody pads, and the WebView pane keeps extending under the navigation
 * bar, which is what classic does (`mainBibleView` is bottom-padded only while the IME is open,
 * `MainBibleActivity.kt:642-648`).
 *
 * Lives in `:sharedCore` (fix round 1, moved from `:app`'s `ComposeReadingViewHost.kt`) rather than
 * `:app`: `ReadingViewScreen` (in `:sharedUi`, which cannot depend on `:app`) is the composable that
 * actually applies this decision, so the tested rule and the production rule must be the same code
 * — a copy living only in `:app` had zero production callers and could silently diverge from the
 * one `ReadingViewScreen` actually used.
 *
 * A pure function, mirroring `net.bible.android.view.activity.page.screen.speakBarVisible`, so the
 * decision is unit-testable.
 */
fun agentLogOwnsNavBarInset(agentLogVisible: Boolean, speakBarVisible: Boolean): Boolean =
    agentLogVisible && !speakBarVisible
