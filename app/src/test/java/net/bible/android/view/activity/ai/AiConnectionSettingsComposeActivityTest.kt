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

package net.bible.android.view.activity.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the silent-data-loss bug where saving an UNCHANGED custom prompt
 * (typed value equal to its own editor prefill, which is the CURRENT custom text) was wrongly
 * treated as a reset. [resolvedCustomPromptValue] must compare only against the raw built-in
 * default, never against the current/prefill value.
 */
class AiConnectionSettingsComposeActivityTest {
    private val builtInDefault = "BUILT_IN_DEFAULT_PROMPT"

    @Test
    fun unchangedCustomPrompt_isPreserved_notResetToDefault() {
        // The bug: the editor is prefilled with the current custom text, so saving without
        // editing yields value == currentCustomText. That must NOT be confused with the raw
        // built-in default and must NOT reset to null.
        val currentCustomText = "My custom prompt"
        assertEquals(currentCustomText, resolvedCustomPromptValue(currentCustomText, builtInDefault))
    }

    @Test
    fun editedCustomPrompt_isStoredAsIs() {
        assertEquals("Edited text", resolvedCustomPromptValue("Edited text", builtInDefault))
    }

    @Test
    fun valueEqualToRawBuiltInDefault_resetsToNull() {
        assertNull(resolvedCustomPromptValue(builtInDefault, builtInDefault))
    }

    @Test
    fun blankValue_resetsToNull() {
        assertNull(resolvedCustomPromptValue("", builtInDefault))
        assertNull(resolvedCustomPromptValue("   ", builtInDefault))
    }

    @Test
    fun nullValue_resetsToNull() {
        assertNull(resolvedCustomPromptValue(null, builtInDefault))
    }
}
