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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.RecommendedSetupVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.strings.LocalStrings

/** The 3 steps of the easy-setup wizard, in order. */
enum class EasySetupStep { PICK, ENTER_KEY, DONE }

/** Result of a "Test connection" attempt in the [EasySetupStep.ENTER_KEY] step. */
sealed class EasySetupTestResult {
    data object Success : EasySetupTestResult()
    data class Failure(val message: String) : EasySetupTestResult()
}

/**
 * UI-flow state for [EasySetupWizard]. Owned by the host (Task 10's controller) — a plain,
 * immutable snapshot the composable renders from; no logic lives here beyond the tiny derived
 * helpers below. [setups] (`LlmProviderService.recommendedSetups()`) is threaded through unchanged
 * across all steps so the [EasySetupStep.PICK] step's [AbListChoiceDialog] has something to show.
 */
data class EasySetupState(
    val step: EasySetupStep,
    val setups: List<RecommendedSetupVd>,
    val selectedSetupId: String? = null,
    val apiKey: String = "",
    val testing: Boolean = false,
    val testResult: EasySetupTestResult? = null,
) {
    val selectedSetup: RecommendedSetupVd? get() = setups.find { it.id == selectedSetupId }

    /** Continue/OK (step 2) is enabled only once a non-blank key has been entered (classic parity
     *  with `EasySetupDialogs.showEasySetupStep2`'s `okButton.isEnabled` text watcher). */
    val canContinue: Boolean get() = apiKey.isNotBlank()

    companion object {
        /** The wizard's opening state: step 1, nothing picked/entered yet. */
        fun initial(setups: List<RecommendedSetupVd>) = EasySetupState(step = EasySetupStep.PICK, setups = setups)
    }
}

/**
 * The easy-setup wizard: 3 steps, each an M3 [AlertDialog], driven entirely by [state] (stateless —
 * this composable holds no state of its own). Mirrors the classic `EasySetupDialogs.kt` flow (the
 * disclaimer gate itself is enforced by the host BEFORE this wizard is shown, per the task brief):
 *
 * - [EasySetupStep.PICK]: an [AbListChoiceDialog] listing [EasySetupState.setups] by label; picking
 *   one invokes [onPick] with its id — the host resolves the transition to [EasySetupStep.ENTER_KEY].
 * - [EasySetupStep.ENTER_KEY]: an API-key field + a "Test connection" action (shows a small spinner
 *   while [EasySetupState.testing], then the success/failure message from
 *   [EasySetupState.testResult]). The confirm button ("OK", which triggers [onConfirm] — the host
 *   performs the setup and advances to [EasySetupStep.DONE] on success) is enabled only when
 *   [EasySetupState.canContinue]; so is "Test connection" (there is nothing to test with a blank key,
 *   same guard the classic dialog used).
 * - [EasySetupStep.DONE]: a done message with a single "OK" button that calls [onDismiss] to close
 *   the wizard.
 *
 * [onDismiss] also backs every step's cancel action — there is no "back" step, same as the classic
 * dialogs (each an independent `AlertDialog`, cancelling just closes it).
 */
@Composable
fun EasySetupWizard(
    state: EasySetupState,
    onPick: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onTest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    when (state.step) {
        EasySetupStep.PICK -> AbListChoiceDialog(
            title = strings.easySetupTitle,
            choices = state.setups.map { SettingsItem.Choice(it.id, it.label) },
            selectedValue = state.selectedSetupId ?: "",
            onSelect = onPick,
            onDismiss = onDismiss,
        )
        EasySetupStep.ENTER_KEY -> {
            val setup = state.selectedSetup
            if (setup != null) {
                EasySetupKeyDialog(
                    state = state,
                    setup = setup,
                    onKeyChange = onKeyChange,
                    onTest = onTest,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                )
            }
        }
        EasySetupStep.DONE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(strings.easySetupDoneTitle) },
            text = { Text(strings.easySetupDoneMessage) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(strings.okay) } },
        )
    }
}

/** Step 2: enter the API key + test the connection. Extracted so [EasySetupWizard] stays a plain
 *  `when` dispatch and this step's non-trivial layout doesn't crowd it. */
@Composable
private fun EasySetupKeyDialog(
    state: EasySetupState,
    setup: RecommendedSetupVd,
    onKeyChange: (String) -> Unit,
    onTest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${strings.easySetupEnterApiKey} — ${setup.label}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val apiKeyUrl = setup.apiKeyUrl
                if (apiKeyUrl != null) {
                    Text(
                        text = "${strings.apiKeyInstructionsPrefix} $apiKeyUrl",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { uriHandler.openUri(apiKeyUrl) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onKeyChange,
                    singleLine = true,
                    label = { Text(strings.providerApiKeyLabel) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val result = state.testResult
                when {
                    state.testing -> {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.easySetupTesting, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    result is EasySetupTestResult.Success -> {
                        Spacer(Modifier.height(8.dp))
                        Text(strings.easySetupSuccess, style = MaterialTheme.typography.bodySmall)
                    }
                    result is EasySetupTestResult.Failure -> {
                        Spacer(Modifier.height(8.dp))
                        Text(strings.easySetupFailed(result.message), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canContinue) { Text(strings.okay) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onTest, enabled = state.canContinue && !state.testing) {
                    Text(strings.easySetupTestConnection)
                }
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        },
    )
}
