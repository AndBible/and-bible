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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * A/B round 6, spec §4.2-4.3. The agent panel is bottom-anchored and grows UPWARD, so classic maps
 * collapsed -> ic_expand_less (up); the port had it inverted. Classic also makes the robot glyph
 * and the status text expand/collapse toggles, which the port dropped.
 *
 * There is no Compose UI-test harness in this repo and compose-ui-test cannot be added under
 * strict egress (see SettingsBadgeLayoutDriftTest), so this source-level guard is what stops both
 * regressions; the re-recorded AgentLogPanel goldens are the visual proof.
 *
 * The tap-to-toggle guard asserts the caret's and the status band's wiring as two INDEPENDENT
 * exact-shape checks (`theCaretIconButtonTogglesExpansion` / `theStatusBandClickableTogglesExpansion`),
 * not a combined occurrence count. A count of `onClick = onToggleExpanded` (the original form of
 * this guard) can't distinguish a real toggle wiring from the drag handle's *call site*, which
 * passes the argument through -- so deleting the band's `.clickable` left the count at exactly the
 * threshold and the regression it exists to catch went undetected (whole-branch review, round 6).
 *
 * Round 12b §4 changed the handle itself: it is now DRAGGABLE as well as tappable, reversing the
 * round-6 decision (which rejected an M3 sheet -- still rejected -- and with it a hand-rolled
 * gesture, which is what this is). `theDragHandleIsDraggable` guards that, because the whole defect
 * being fixed was a handle that rendered the universal drag affordance and did nothing when dragged.
 */
class AgentLogHeaderStructureGuardTest {
    private val source =
        java.io.File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/ai/reading/AgentLogPanel.kt").readText()

    @Test
    fun collapsedShowsTheUpCaretBecauseThePanelGrowsUpward() {
        assertThat(
            "collapsed must map to ExpandLess (up): the panel is bottom-anchored",
            source.contains("if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess"),
            equalTo(true),
        )
        assertThat(
            "the inverted mapping must be gone",
            source.contains("if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore"),
            equalTo(false),
        )
    }

    @Test
    fun theCaretIconButtonTogglesExpansion() {
        // A plain count of `onClick = onToggleExpanded` occurrences (the previous form of this
        // test) could not tell the caret's wiring apart from the drag handle's *call site* --
        // `AgentLogDragHandle(onClick = onToggleExpanded)` -- passing at "2" even after the band's
        // own wiring below was deleted. Assert the caret's exact shape so it is guarded on its own.
        assertThat(
            "the caret IconButton must wire onToggleExpanded directly: IconButton(onClick = onToggleExpanded)",
            source.contains("IconButton(onClick = onToggleExpanded)"),
            equalTo(true),
        )
    }

    @Test
    fun theStatusBandClickableTogglesExpansion() {
        // Same rationale as theCaretIconButtonTogglesExpansion: assert the band's exact shape
        // so removing it fails independently of the caret and the drag-handle call site.
        assertThat(
            "the robot/status-text band must wire onToggleExpanded via .clickable(onClick = onToggleExpanded)",
            source.contains(".clickable(onClick = onToggleExpanded)"),
            equalTo(true),
        )
    }

    @Test
    fun theDragHandleIsDraggable() {
        // Round 12b §4: the reported defect was a handle that drew the drag affordance and ignored
        // drags. This is a SOURCE guard, and deliberately a cheap one — not the only guard possible
        // (whole-branch review, Blocker 2 corrected the claim that no Compose gesture harness is
        // available: `compose-ui-test` is in this module's test source set, `app/build.gradle.kts:508-509`,
        // and `AbSearchableOptionSheetContentTest` drives `createComposeRule()`). The wiring itself
        // IS guarded for real, by `AgentLogPanelDragGestureTest`, which drives the actual handle's
        // drag and tap; this cheap source assertion survives alongside it because it names the exact
        // modifier and callback and so fails with a pointed message if either is renamed away.
        assertThat(
            "the drag handle must carry a vertical draggable modifier",
            source.contains("orientation = Orientation.Vertical"),
            equalTo(true),
        )
        assertThat(
            "the handle's drag must be wired to the panel's onHeightDrag parameter",
            source.contains("onDrag = onHeightDrag"),
            equalTo(true),
        )
        assertThat(
            "the draggable state must actually call onDrag, not just declare the parameter",
            source.contains("onDrag(-with(density)"),
            equalTo(true),
        )
    }
}
