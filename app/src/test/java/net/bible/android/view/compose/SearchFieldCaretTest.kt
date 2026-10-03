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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import net.bible.android.TEST_SDK
import net.bible.sharedui.reading.ReadingSearchField
import net.bible.sharedui.reading.SEARCH_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F44/B2 regression: the reading toolbar's search field must keep its own caret even when the
 * hoisted query is published a recomposition late, exactly as the real host's
 * `combine(...).stateIn(hostScope, Eagerly, null)` pipeline does (`hostScope` is plain
 * `Dispatchers.Main`, not `Main.immediate`).
 *
 * Drives [ReadingSearchField] directly — the same composable [net.bible.sharedui.reading.ReadingToolbar]
 * renders for its search mode — so this test cannot pass against a copy of the field while the real
 * one stays broken.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchFieldCaretTest {
    @get:Rule val composeRule = createComposeRule()

    @Composable
    private fun SearchFieldUnderTest(
        value: String,
        onValueChange: (String) -> Unit,
    ) {
        ReadingSearchField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions.Default,
        )
    }

    /**
     * The hoisted query is published one recomposition LATE, exactly as the host's
     * combine/stateIn pipeline does. With a String-valued field and no local TextFieldValue the
     * selection is clamped to 0 and the second character lands in front of the first ("ba").
     */
    @Test
    fun typingTwoCharactersKeepsTheCaretAtTheEnd() {
        var published by mutableStateOf("")
        var pending = ""
        composeRule.setContent {
            SearchFieldUnderTest(
                value = published,
                onValueChange = { pending = it },
            )
        }
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("a")
        composeRule.runOnIdle { published = pending }   // the late publish
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("b")
        composeRule.runOnIdle { published = pending }
        assertEquals("ab", published)
    }
}
