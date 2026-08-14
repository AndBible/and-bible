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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.navigation.iconCategory
import net.bible.sharedui.components.AbSearchableOptionSheetContent
import net.bible.sharedui.navigation.LocalCategoryIcon
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures [AbSearchableOptionSheetContent] in the two configurations [DocumentFilterBar] uses —
 * NEVER the live [net.bible.sharedui.components.AbSearchableOptionSheet]: that wraps a
 * ModalBottomSheet, which is a popup, and popups hang Roborazzi captures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DocumentFilterBarGoldenTest {
    private val languages = listOf(
        LangOption("en", "English", "en"),
        LangOption("fi", "Finnish", "fi"),
        LangOption("grc", "Greek", "grc"),
    )

    private val typeFilters = listOf(
        DocTypeFilter.ALL to "All types",
        DocTypeFilter.BIBLE to "Bible",
        DocTypeFilter.COMMENTARY to "Commentary",
        DocTypeFilter.DICTIONARY to "Dictionary",
        DocTypeFilter.GENERAL_BOOK to "Book",
        DocTypeFilter.MAPS to "Map",
        DocTypeFilter.ADDON to "Add-ons",
    )

    private val typeFilterIconSize = 24.dp

    /** The language sheet's configuration: a search field, no leading icons. */
    @Test fun languageSheet() {
        captureGolden("DocumentFilterBar", "languageSheet", EDGE_MODE) {
            AbSearchableOptionSheetContent(
                options = languages,
                selected = languages[1],
                optionLabel = { it.displayName },
                onSelect = {},
                searchPlaceholder = "Search",
            )
        }
    }

    /** The type sheet's configuration: per-option leading category icons, no search field. */
    @Test fun typeSheet() {
        captureGolden("DocumentFilterBar", "typeSheet", EDGE_MODE) {
            AbSearchableOptionSheetContent(
                options = typeFilters,
                selected = typeFilters[0],
                optionLabel = { it.second },
                onSelect = {},
                searchPlaceholder = null,
                leadingIcon = { pair ->
                    val category = pair.first.iconCategory
                    if (category == null) {
                        Spacer(Modifier.size(typeFilterIconSize))
                    } else {
                        Icon(
                            painter = LocalCategoryIcon.current(category),
                            contentDescription = null,
                            modifier = Modifier.size(typeFilterIconSize),
                        )
                    }
                },
            )
        }
    }
}
