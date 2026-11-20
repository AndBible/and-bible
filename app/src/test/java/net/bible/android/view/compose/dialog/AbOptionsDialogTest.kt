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

package net.bible.android.view.compose.dialog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.DialogAnswer
import net.bible.sharedui.components.AbOptionsDialog
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [AbOptionsDialog] on the dialog shell: answer cards, consequence lines, AX5 reachability and the checkbox variant. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbOptionsDialogTest {
    /** `@Ax5` methods run with the real activity font scale 3 (see AbDialogShellTest). */
    @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) annotation class Ax5

    @get:Rule(order = 0) val fontScaleRule: org.junit.rules.TestRule = org.junit.rules.TestRule { base, description ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                org.robolectric.RuntimeEnvironment.setFontScale(if (description.getAnnotation(Ax5::class.java) != null) 3f else 1f)
                base.evaluate()
            }
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private val orphaned = listOf(
        DialogAnswer("labelOnly", "Delete label only", "The bookmarks stay, without a label."),
        DialogAnswer("both", "Delete label and bookmarks", "The bookmarks with this label are deleted too.", destructive = true),
    )

    private val cloudSync = listOf(
        DialogAnswer("a", "Use the cloud copy", "Replaces what is on this device with the version stored in the cloud, for every synced category."),
        DialogAnswer("b", "Keep this device's copy", "Overwrites the cloud version with what is on this device, for every synced category."),
        DialogAnswer("c", "Merge both copies", "Combines the two where possible and asks about each conflict it cannot settle by itself.", false),
        DialogAnswer("d", "Start over in the cloud", "Deletes the cloud copy and uploads this device's data as a new first version.", destructive = true),
    )

    @Test fun cardClickReturnsItsValue() {
        var selected: String? = null
        compose.setContent {
            AbTheme { AbOptionsDialog("Delete the label?", null, orphaned, { selected = it }, {}, dismissText = "Cancel") }
        }
        compose.onNodeWithTag("dialog-answer-labelOnly").performClick()
        assertEquals("labelOnly", selected)
    }

    @Test fun consequenceLineIsShown() {
        compose.setContent {
            AbTheme { AbOptionsDialog("Delete the label?", null, orphaned, {}, {}, dismissText = "Cancel") }
        }
        compose.onNodeWithText("The bookmarks stay, without a label.").assertIsDisplayed()
    }

    @Ax5 @Test fun allFourAnswersReachableAtAx5() {
        compose.setContent {
            AbTheme { AbOptionsDialog("How should the two copies be reconciled?", null, cloudSync, {}, {}, dismissText = "Cancel") }
        }
        compose.waitForIdle()
        cloudSync.forEach { a ->
            compose.onNodeWithTag("dialog-answer-${a.value}").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("dialog-dismiss").assertIsDisplayed()
    }

    @Test fun checkboxVariantKeepsCheckboxAndButtons() {
        var result: Pair<String, Boolean>? = null
        compose.setContent {
            AbTheme {
                AbOptionsDialog(
                    "New version available", null,
                    listOf(DialogAnswer("download", "Download"), DialogAnswer("later", "Later")),
                    {}, {}, checkboxLabel = "Don't notify me", onSelectWithCheckbox = { v, c -> result = v to c },
                )
            }
        }
        compose.onNodeWithText("Don't notify me").assertIsDisplayed()
        compose.onAllNodesWithTag("dialog-answer-download").assertCountEquals(0)
        compose.onNodeWithText("Don't notify me").performClick()
        compose.onNodeWithText("Download").performClick()
        assertEquals("download" to true, result)
    }
}
