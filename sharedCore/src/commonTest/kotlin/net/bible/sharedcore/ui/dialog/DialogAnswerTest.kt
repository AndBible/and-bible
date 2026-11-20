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

package net.bible.sharedcore.ui.dialog

import net.bible.sharedcore.settings.SettingsItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class DialogAnswerTest {
    @Test
    fun toAnswerKeepsValueAndLabelWithNoConsequence() {
        val answer = SettingsItem.Choice("both", "Delete both").toAnswer()
        assertEquals(DialogAnswer("both", "Delete both"), answer)
        assertNull(answer.consequence)
        assertFalse(answer.destructive)
    }

    @Test
    fun toAnswersPreservesOrderAndSize() {
        val answers = listOf(
            SettingsItem.Choice("b", "B"), SettingsItem.Choice("a", "A"), SettingsItem.Choice("c", "C"),
        ).toAnswers()
        assertEquals(listOf("b", "a", "c"), answers.map { it.value })
        assertEquals(listOf("B", "A", "C"), answers.map { it.label })
    }

    @Test
    fun toAnswersOfEmptyListIsEmpty() {
        assertEquals(emptyList(), emptyList<SettingsItem.Choice>().toAnswers())
    }
}
