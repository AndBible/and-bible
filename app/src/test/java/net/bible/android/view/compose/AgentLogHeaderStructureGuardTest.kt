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
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.Test

/**
 * A/B round 6, spec §4.2-4.3. The agent panel is bottom-anchored and grows UPWARD, so classic maps
 * collapsed -> ic_expand_less (up); the port had it inverted. Classic also makes the robot glyph
 * and the status text expand/collapse toggles, which the port dropped.
 *
 * There is no Compose UI-test harness in this repo and compose-ui-test cannot be added under
 * strict egress (see SettingsBadgeLayoutDriftTest), so this source-level guard is what stops both
 * regressions; the re-recorded AgentLogPanel goldens are the visual proof.
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
    fun theToggleHasMoreThanOneCallSiteSoTheRobotAndStatusTextAreTappable() {
        // The caret IconButton plus the robot/status-text band -- and, from Task 6, the drag
        // handle. Counting call sites (not identifier occurrences) keeps this insensitive to kdoc.
        val callSites = Regex("""onClick = onToggleExpanded""").findAll(source).count()
        assertThat(
            "onToggleExpanded must be wired from at least the caret and the status band",
            callSites,
            greaterThanOrEqualTo(2),
        )
    }
}
