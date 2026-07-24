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

package net.bible.sharedui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedui.strings.LocalStrings

/**
 * The runtime agent tool-permission prompt — Compose port of `Dialogs.agentPermissionDialog`.
 *
 * Classic uses a custom-view `AlertDialog` with FIVE stacked buttons (an `AlertDialog` only supports
 * three), so this renders the five choices as a vertical `TextButton` column in the dialog body and
 * gives the `AlertDialog` no `confirmButton` content of its own.
 *
 * [AgentPermissionChoice.ALLOW_ALWAYS] is reported straight through: the second "are you sure"
 * confirmation stays in `AgentExecutor.showPermissionDialog` (its existing `Dialogs.simpleQuestion`
 * call), which both avoids double-prompting and keeps that step identical on the classic and Compose
 * paths.
 */
@Composable
fun AgentPermissionDialog(
    request: AgentPermissionRequest,
    onChoice: (AgentPermissionChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    // Bind to a local before the null-check: smart-casting a public property declared in another
    // module (AgentPermissionRequest lives in :sharedCore) is not valid Kotlin/Native.
    val action = request.actionDescription

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.agentPermissionTitle) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (action != null) {
                        strings.agentPermissionMessageWithAction(action)
                    } else {
                        strings.agentPermissionMessage(request.toolDisplayName, request.toolDescription)
                    }
                )
                AgentPermissionChoiceRows(
                    toolDisplayName = request.toolDisplayName,
                    onChoice = onChoice,
                )
            }
        },
        confirmButton = {},
    )
}

/**
 * The five choice rows, factored out so a golden test can capture them without depending on
 * dialog-window rendering.
 */
@Composable
fun AgentPermissionChoiceRows(
    toolDisplayName: String,
    onChoice: (AgentPermissionChoice) -> Unit,
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { onChoice(AgentPermissionChoice.ALLOW) }) {
            Text(strings.permissionAllowOnce)
        }
        TextButton(onClick = { onChoice(AgentPermissionChoice.ALLOW_FOR_SESSION) }) {
            Text(strings.permissionAllowForSession)
        }
        TextButton(onClick = { onChoice(AgentPermissionChoice.ALLOW_ALL_SESSION) }) {
            Text(strings.permissionAllowAllSession)
        }
        TextButton(onClick = { onChoice(AgentPermissionChoice.ALLOW_ALWAYS) }) {
            Text(strings.permissionAllowAlways(toolDisplayName))
        }
        TextButton(onClick = { onChoice(AgentPermissionChoice.DENY) }) {
            Text(strings.permissionDeny)
        }
    }
}
