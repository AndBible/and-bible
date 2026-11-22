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
package net.bible.sharedui.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DocumentQuickContentHeadingsTest {
    private fun row(id: String, cat: DocCategory) = DocRow(
        docId = id, osisId = id, abbreviation = id, name = "Doc $id",
        language = LangOption("en", "en", "en"), repository = "r", category = cat,
        installStatus = DocInstallStatus.INSTALLED, percentDone = 100, recommended = false,
        badWarn = false, locked = false, enciphered = false, canDelete = true, installSizeMb = null,
    )

    private val rows = listOf(
        row("MHC", DocCategory.COMMENTARY), row("TSK", DocCategory.COMMENTARY),
        row("STRONG", DocCategory.DICTIONARY), row("MAP1", DocCategory.MAPS),
    )

    private fun label(c: DocCategory) = "H-" + c.name

    @Test
    fun groupedRowsGetOneHeadingPerTypeRun() = runComposeUiTest {
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                CompositionLocalProvider(LocalCategoryIcon provides { ColorPainter(Color.Gray) }) {
                    DocumentQuickContent(
                        rows, currentDocId = null, onSelect = {}, modifier = Modifier.size(400.dp, 600.dp),
                        groupHeader = ::label,
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("H-COMMENTARY").assertExists()
        onNodeWithText("H-DICTIONARY").assertExists()
        onNodeWithText("H-MAPS").assertExists()
        // two commentaries share one heading
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(3)
    }

    @Test
    fun withoutAGroupHeaderThereAreNoHeadings() = runComposeUiTest {
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                CompositionLocalProvider(LocalCategoryIcon provides { ColorPainter(Color.Gray) }) {
                    DocumentQuickContent(rows, currentDocId = null, onSelect = {}, modifier = Modifier.size(400.dp, 600.dp))
                }
            }
        }
        waitForIdle()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(0)
    }
}
