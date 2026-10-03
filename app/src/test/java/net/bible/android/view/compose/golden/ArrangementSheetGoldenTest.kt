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

import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocSortCriterion
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedui.components.AbArrangementLabels
import net.bible.sharedui.components.AbArrangementSheetContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captured via [AbArrangementSheetContent] directly, never inside `AbArrangementSheet`: an open
 * ModalBottomSheet is a popup and hangs the Roborazzi capture (and the whole :app suite with it).
 *
 * `labels` is built from literal English strings rather than `LocalStrings`, so this golden does
 * not depend on Android resource lookup.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ArrangementSheetGoldenTest {

    private val labels = AbArrangementLabels(
        title = "Filter and sort", repositoryLabel = "Repository",
        allRepositories = "All repositories", sortLabel = "Sort order", groupLabel = "Group by",
        rememberLabel = "Remember these settings", resetLabel = "Reset to defaults",
        reorderLabel = "Reorder", ascending = "Ascending", descending = "Descending",
        sortKeyLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
        groupKeyLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
    )

    @Test fun downloadConfig() {
        captureGolden("ArrangementSheet", "download", EDGE_MODE, heightDp = 1200) {
            AbArrangementSheetContent(
                labels = labels,
                sort = listOf(
                    DocSortCriterion(DocSortKey.STATUS),
                    DocSortCriterion(DocSortKey.RECOMMENDED, descending = true),
                    DocSortCriterion(DocSortKey.TYPE),
                    DocSortCriterion(DocSortKey.NAME),
                    DocSortCriterion(DocSortKey.LANGUAGE),
                    DocSortCriterion(DocSortKey.REPOSITORY),
                    DocSortCriterion(DocSortKey.SIZE),
                ),
                groupBy = DocGroupBy.TYPE,
                groupKeys = listOf(DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.LANGUAGE, DocGroupBy.REPOSITORY),
                repositories = listOf("CrossWire", "CrossWire Beta", "eBible"),
                selectedRepository = "CrossWire",
                rememberSettings = true,
                resultCount = "312 documents",
                onMoveSort = { _, _ -> }, onToggleDirection = {}, onGroupByChange = {},
                onRepositoryChange = {}, onRememberChange = {}, onReset = {},
            )
        }
    }
}
