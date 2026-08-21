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
package net.bible.sharedui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/** Owns the working text; [onValueChange] reports every keystroke so a sheet host can enable its
 *  own confirm affordance. The initial value arrives pre-selected, as classic's EditText.selectAll().
 *
 *  Its working `TextFieldValue` MUST be `remember(initial)`, never a bare `remember`: the dialog
 *  could use a bare one safely because every dialog was a fresh composition, but one bottom sheet is
 *  reused across pages, so a bare `remember` would show the previous row's text in the next row's
 *  editor. */
@Composable
fun AbTextInputContent(
    initial: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
    masked: Boolean = false,
    modifier: Modifier = Modifier,
    extraContent: @Composable (() -> Unit)? = null,
) {
    var value by remember(initial) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
    }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; onValueChange(it.text) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (masked) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    masked -> KeyboardType.Password
                    numeric -> KeyboardType.Number
                    else -> KeyboardType.Text
                },
            ),
        )
        extraContent?.invoke()
    }
}

/**
 * A titled AlertDialog wrapping a single-line text field, returning the current value on confirm.
 * The initial value is pre-selected (parity with the classic EditText.selectAll() name dialogs), so
 * typing replaces it. [extraContent] lets a caller add a control below the field (e.g. a type picker).
 * Set [numeric] to surface a number keyboard (used by the settings framework for numeric TextInputRows).
 * Set [masked] to obscure the entered characters (password field) via [PasswordVisualTransformation] and
 * a password keyboard (used by the settings framework for masked TextInputRows).
 * Shared component (promoted from MyDocuments); reused by WorkspaceSelector new/rename/clone.
 */
@Composable
fun AbTextInputDialog(
    title: String,
    initial: String,
    confirmText: String,
    dismissText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
    numeric: Boolean = false,
    masked: Boolean = false,
) {
    var current by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AbTextInputContent(
                initial = initial,
                onValueChange = { current = it },
                numeric = numeric,
                masked = masked,
                extraContent = extraContent,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
