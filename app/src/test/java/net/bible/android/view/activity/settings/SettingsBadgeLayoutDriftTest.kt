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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * A/B batch 4a F2. The inheritance badge ("Workspace"/"Global") used to be drawn as a Box overlay
 * aligned CenterEnd with a hard-coded `padding(end = 56.dp)` guess at the trailing control's width,
 * so it covered the row's summary and switch. It is now laid out INSIDE the row's text column.
 *
 * There is no Compose UI-test harness in this repo (nothing uses createComposeRule, and
 * compose-ui-test cannot be added under strict egress), so this source-level guard is what stops
 * the overlay shape coming back; the re-recorded TextDisplaySettings goldens are the visual proof.
 */
class SettingsBadgeLayoutDriftTest {
    private val source =
        java.io.File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/AbSettingsScreen.kt").readText()

    @Test
    fun badgeIsNotDrawnAsAnOverlayWithAGuessedTrailingWidth() {
        assertThat("the 56dp trailing-width guess must be gone", source.contains("end = 56.dp"), equalTo(false))
        assertThat("the badge must not be aligned over the row", source.contains("Alignment.CenterEnd"), equalTo(false))
    }
}
