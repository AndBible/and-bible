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
 * Compose reading tree is edge-to-edge: the toolbar consumes `statusBars` itself, the floating
 * window rail consumes the bottom `navigationBars` inset only when it is the bottom-most surface
 * (`railOwnsNavBarInset`), and the split consumes the side insets, so the bottom-most in-flow child has to consume the
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

/**
 * Whether the Speak transport bar draws its own top edge — the rounded top corners and the 8dp
 * shadow — or hands that edge to the surface stacked on top of it.
 *
 * Round 14b §6, from device feedback: "the Speak transport bar looks bad when the AI widget is above
 * it; colour-wise it should run smoothly underneath it." The two surfaces already resolve to the
 * SAME colour — both are `Surface(tonalElevation = 3.dp)`, measured as RGB 242,233,248 on both in
 * `ReadingViewScreen_withAgentLogAndSpeakBar_light.png` — so the visible defect was never the
 * colour. It was the lower surface drawing its corners and its shadow AGAINST the upper one.
 *
 * So the fix is edge ownership, not a colour change, and it is the mirror image of
 * [agentLogOwnsNavBarInset] one line above: the navigation-bar inset belongs to the BOTTOM-most
 * visible bar, the corners and the shadow to the TOP-most one. Read the two together — with both
 * bars up, the speak bar owns the inset and the agent panel owns the edge, so a predicate
 * copy-pasted from one to the other is wrong in exactly the configuration this exists for.
 *
 * Only [agentLogVisible] is needed: this function answers a question the speak bar only asks while
 * it is itself being rendered, so its own visibility is a precondition, not an argument. Rejected
 * alternatives (spec D2): a 1dp divider between the pair — classic had one, round 13a dropped it
 * deliberately — and a tonal step between them, which would either vanish or turn coarse in the
 * black-and-white and e-ink display modes.
 *
 * A pure function so the decision is unit-testable, like its neighbour — see `BottomBarInsetsTest`.
 */
fun speakBarOwnsTopEdge(agentLogVisible: Boolean): Boolean = !agentLogVisible

/**
 * Whether the floating window-buttons strip is the bottom-most surface and must therefore pad the
 * navigation bar's BOTTOM inset itself (fix batch 2, F66/F67). When either bottom bar is visible, that
 * bar owns the inset (see [agentLogOwnsNavBarInset]) and the split -- and the strip in its
 * `railOverlay` -- already ends above it. Padding the strip then puts one nav-bar height of gap
 * between it and the bar (F67; the Speak bar showed the same). With the keyboard up the column is
 * already padded by the IME, which includes the nav bar (F66); the strip handles that by excluding
 * `ime` from the inset it pads, not through this predicate.
 */
fun railOwnsNavBarInset(agentLogVisible: Boolean, speakBarVisible: Boolean): Boolean =
    !agentLogVisible && !speakBarVisible
