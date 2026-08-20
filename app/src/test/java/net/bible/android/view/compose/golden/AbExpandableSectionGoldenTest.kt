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

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbExpandableSection
import net.bible.sharedui.components.AbSwitchRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Collapsed and expanded, side by side. The collapsed header's INDICATORS are the point: a
 * collapsed section that cannot say whether anything inside it is set is a section users will not
 * open, which is why the label editor's context groups carry ⚡ / 🔖 / ⚙ marks in their headers
 * rather than a summary sentence (which would need new strings in 50 locales).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbExpandableSectionGoldenTest {

    @Test fun abExpandableSection_states() = captureMatrix("AbExpandableSection", "states", heightDp = 400) {
        Column {
            AbExpandableSection(
                title = "This workspace",
                expanded = false,
                onToggle = {},
                indicators = { Icon(Icons.Filled.Bolt, contentDescription = null) },
            ) {}
            AbExpandableSection(
                title = "This bookmark",
                expanded = true,
                onToggle = {},
                indicators = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
            ) {
                AbSwitchRow(
                    label = "Auto-assign label to new bookmarks",
                    checked = true,
                    onCheckedChange = {},
                    leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                )
                Text("…")
            }
        }
    }
}
