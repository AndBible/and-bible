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

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.BookPick
import net.bible.sharedcore.navigation.ChapterPick
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.reading.host.slots.GridKeyChooserSheetFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Spec §4.6 / Amendment D3, with a REAL back press: inside the grid key-chooser sheet, system back
 * pops the grid level first (the `PlatformBackHandler` inside the sheet content wins over Material's
 * own back handling because it registers later on the dialog's dispatcher), and only at the book step
 * does back close the sheet. The `:sharedUi` jvm test cannot do this: its `PlatformBackHandler` is a
 * no-op, so the ordering is only observable with the Android actual, here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = Application::class)
class GridKeyChooserBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun controller(): GridChoosePassageController {
        fun ui(step: GridStep) = GridUi(
            step = step, title = "Step-$step", columns = 1, showLongNames = false, showProgress = false,
            showDeutToggle = false, buttons = listOf(GridButton(0, "b")),
        )
        return GridChoosePassageController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            initialOptions = GridOptions(true, false, true, false, false, false),
            buildStep = { step, _ -> ui(step) },
            onPersistOptions = {},
            onPickBook = { BookPick.GoChapter },
            onPickChapter = { ChapterPick.GoVerse },
            onPickVerse = { "x" },
            onFinish = {},
        )
    }

    @Test fun systemBackPopsTheGridLevelBeforeClosingTheSheet() {
        val controller = controller()
        var closed = 0
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                net.bible.sharedui.strings.LocalStrings provides net.bible.sharedui.strings.AndroidStrings(composeRule.activity),
            ) {
                net.bible.sharedui.theme.AbTheme(darkTheme = false) {
                    GridKeyChooserSheetFor(controller, "Fallback") { closed++ }
                }
            }
        }
        composeRule.waitForIdle()
        controller.pick(0) // BOOK -> CHAPTER
        controller.pick(0) // CHAPTER -> VERSE
        composeRule.waitForIdle()
        assertEquals(GridStep.VERSE, controller.step.value)

        val dialog = ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
        dialog.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals("first back pops VERSE -> CHAPTER", GridStep.CHAPTER, controller.step.value)
        assertEquals(0, closed)

        dialog.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals("second back pops CHAPTER -> BOOK", GridStep.BOOK, controller.step.value)
        assertEquals(0, closed)

        dialog.onBackPressedDispatcher.onBackPressed() // at the root the grid's handler is off: Material closes
        composeRule.waitForIdle()
        assertEquals("back at the book step closes the sheet", 1, closed)
    }
}
