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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbCreateItemSheetContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captured via [AbCreateItemSheetContent] directly, never inside `AbCreateItemSheet`: forcing a
 * ModalBottomSheet open in a capture hangs Roborazzi (see SettingsEditorSheetGoldenTest). The
 * `extraContent` variant passes a plain Text, NOT a real dropdown — an expanded dropdown inside a
 * captured sheet is the two-popup shape that hangs the whole :app suite, and a collapsed one would
 * only prove that the slot renders, which a Text proves just as well.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbCreateItemSheetGoldenTest {

    @Composable
    private fun sheet(extra: (@Composable () -> Unit)? = null) = AbCreateItemSheetContent(
        title = "Create new document",
        initialName = "Document 3",
        confirmText = "OK",
        importText = "Import document",
        onCreate = {}, onImport = {},
        extraContent = extra,
    )

    @Test fun createItemSheet_plain() {
        captureMatrix("AbCreateItemSheet", "plain") { sheet() }
    }

    @Test fun createItemSheet_withExtra() {
        captureGolden("AbCreateItemSheet", "withExtra", EDGE_MODE) {
            sheet(extra = { Text("Content type: MARKDOWN") })
        }
    }
}
