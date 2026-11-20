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

package net.bible.sharedui.components.dialog

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import net.bible.sharedui.theme.isPureMonochrome

/** What a dialog button does; decides its colour and test tag. */
enum class DialogButtonRole { Cancel, Neutral, Confirm, Destructive }

/** Semantics property carrying the [DialogButtonRole] of a dialog button (for tests). */
val DialogButtonRoleKey = SemanticsPropertyKey<DialogButtonRole>("DialogButtonRole")
var SemanticsPropertyReceiver.dialogButtonRole: DialogButtonRole by DialogButtonRoleKey

/** Text colour and weight of a dialog button. */
data class DialogButtonLook(val color: Color, val weight: FontWeight)

/**
 * Pure look of a role. In pure monochrome nothing carries a hue: every role is ink, and
 * [DialogButtonRole.Destructive] is told apart by bold weight.
 */
fun dialogButtonLook(role: DialogButtonRole, mono: Boolean, scheme: ColorScheme): DialogButtonLook =
    if (mono) {
        DialogButtonLook(scheme.onSurface, if (role == DialogButtonRole.Destructive) FontWeight.Bold else FontWeight.Medium)
    } else {
        DialogButtonLook(if (role == DialogButtonRole.Destructive) scheme.error else scheme.primary, FontWeight.Medium)
    }

private fun DialogButtonRole.testTagName() = when (this) {
    DialogButtonRole.Cancel -> "dialog-dismiss"
    DialogButtonRole.Neutral -> "dialog-neutral"
    DialogButtonRole.Confirm, DialogButtonRole.Destructive -> "dialog-confirm"
}

/** A dialog button by [role]. [filled] gives an M3 `Button` (only the text-page footer uses it). */
@Composable
fun AbDialogButton(
    label: String,
    role: DialogButtonRole,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val look = dialogButtonLook(role, isPureMonochrome(), scheme)
    val tagged = modifier
        .testTag(role.testTagName())
        .semantics { dialogButtonRole = role }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = tagged,
            colors = ButtonDefaults.buttonColors(
                containerColor = look.color,
                contentColor = if (role == DialogButtonRole.Destructive) scheme.onError else scheme.onPrimary,
            ),
        ) { Text(label, fontWeight = look.weight) }
    } else {
        TextButton(
            onClick = onClick,
            modifier = tagged,
            colors = ButtonDefaults.textButtonColors(contentColor = look.color),
        ) { Text(label, fontWeight = look.weight) }
    }
}
