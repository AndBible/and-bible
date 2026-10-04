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
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchIndexError
import net.bible.sharedui.search.SearchIndexPanel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The search sheet's index panel in both of its states. Captured directly, never inside a
 * `BottomSheetScaffold`: the sheet chrome belongs to the host and forcing a sheet open in a capture
 * is unreliable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchIndexPanelGoldenTest {

    // One determinate and one indeterminate job: ProgressRow renders those two branches differently,
    // and JSword reports work=0 (indeterminate) until the first chunk of an index lands.
    private val jobs = listOf(
        ProgressJob(id = "1", label = "Creating index for ESV", percent = 42, indeterminate = false),
        ProgressJob(id = "2", label = "Optimizing index", percent = 0, indeterminate = true),
    )

    private fun panel(
        isRebuild: Boolean = false,
        indexing: Boolean = false,
        jobs: List<ProgressJob> = emptyList(),
        error: SearchIndexError? = null,
    ): @Composable () -> Unit = {
        SearchIndexPanel(
            documentName = "ESV",
            isRebuild = isRebuild,
            indexing = indexing,
            jobs = jobs,
            error = error,
            onCreate = {},
            onCancel = {},
            onDismissError = {},
        )
    }

    @Test fun prompt() = captureMatrix("SearchIndexPanel", "prompt", content = panel())

    @Test fun indexing() =
        captureMatrix("SearchIndexPanel", "indexing", content = panel(indexing = true, jobs = jobs))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun prompt_rtl() = captureRtl("SearchIndexPanel", "prompt", content = panel())

    @Test fun rebuildPrompt() =
        captureGolden("SearchIndexPanel", "rebuildPrompt", EDGE_MODE, content = panel(isRebuild = true))

    // The failure dialog sits over a sheet that stays open — nothing to finish, unlike the Activity.
    @Test fun error() = captureGolden(
        "SearchIndexPanel",
        "error",
        EDGE_MODE,
        content = panel(indexing = true, jobs = jobs, error = SearchIndexError.FAILED),
    )
}
