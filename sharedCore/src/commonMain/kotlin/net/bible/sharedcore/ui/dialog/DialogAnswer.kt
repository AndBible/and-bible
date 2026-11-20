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

/**
 * One answer to a question dialog. [value] is what the caller gets back, [label] the button text,
 * [consequence] an optional line saying what the answer does, [destructive] marks a harmful answer.
 */
data class DialogAnswer(
    val value: String,
    val label: String,
    val consequence: String? = null,
    val destructive: Boolean = false,
)

/** A plain [SettingsItem.Choice] as an answer with no consequence line. */
fun SettingsItem.Choice.toAnswer(): DialogAnswer = DialogAnswer(value, label)

/** Maps each [SettingsItem.Choice] with [toAnswer], keeping order and size (no consequence lines, none destructive). */
fun List<SettingsItem.Choice>.toAnswers(): List<DialogAnswer> = map { it.toAnswer() }
