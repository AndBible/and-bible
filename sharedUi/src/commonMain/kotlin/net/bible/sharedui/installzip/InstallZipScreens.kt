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
package net.bible.sharedui.installzip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.ProgressRow
import net.bible.sharedui.strings.LocalStrings

/**
 * commonMain-local render model for the InstallZip Compose UI. The Android host
 * (`InstallZipComposeActivity`, Plan B Task B3) maps Plan A's `InstallPhase`/`DecisionRequest`
 * (`net.bible.service.installzip`, androidMain-only) into this — those types are NOT referenced
 * here so this file stays buildable for both Android and iOS targets.
 *
 * Every field that reaches the user as text is either already fully resolved by the host (e.g.
 * [Progress.statusText], [FormatInfo.formatsText], [Error.message] — built from `R.string` values
 * the host has direct access to) or is raw data ([Overwrite.files], [StudyPadImport.statsText],
 * [ConfirmInstall.displayName]) that [InstallZipContent] itself turns into a sentence via
 * [LocalStrings], mirroring exactly which strings classic `InstallZip`/`ZipHandler` builds
 * host-side vs. which it hands to a shared question/dialog helper.
 */
sealed interface InstallUiState {
    /** A running job: [displayName] (nullable — not always known, e.g. `ACTION_SEND`), a
     *  host-resolved [statusText] (e.g. "Checking given file…" / "Extracting Zip file now…"),
     *  [percent] (null when not yet known — e.g. during `Inspecting`) and [indeterminate]. */
    data class Progress(
        val displayName: String?,
        val statusText: String,
        val percent: Int?,
        val indeterminate: Boolean,
    ) : InstallUiState

    /** Prelude / `ACTION_VIEW` confirmation: "Do you want to install module (name)?" */
    data class ConfirmInstall(val displayName: String?) : InstallUiState

    /** The no-action-intent prelude: supported-formats blurb, built host-side from the
     *  `choose_file`/`supported_formats`/`format_*` keys (classic `getFileFromUserAndInstall`). */
    data class FormatInfo(val formatsText: String) : InstallUiState

    /** Mid-install overwrite decision; [files] is the raw relative-path list (unjoined — the
     *  composable formats it, mirroring classic's `"\n" + files.joinToString("\n")`). */
    data class Overwrite(val files: List<String>) : InstallUiState

    /** StudyPad-export ask-back; [statsText] is the pre-built stats sentence fragment (classic
     *  `bookmarksDbStats(...)`), substituted into the same "do you want to install" question. */
    data class StudyPadImport(val statsText: String) : InstallUiState

    /** EPUB re-optimize confirmation (classic `CommonUtils.documentUpgradeConfirmation`). */
    data object EpubUpgrade : InstallUiState

    /** A terminal failure; [message] is the fully host-resolved message (already combines the
     *  `R.string` template + any argument — e.g. `install_failed_reason`/`invalid_module`). */
    data class Error(val message: String) : InstallUiState
}

/**
 * Stateless install UI: renders whichever screen/dialog [state] calls for. [onConfirm] is the
 * positive/proceed/yes action; [onDismiss] is the negative/cancel/no/dismiss action. The host
 * (`InstallZipComposeActivity`) wires these to `DocumentInstallService.resolveDecision(jobId, …)`
 * for the mid-flight decision states, and to its own prelude/finish logic for [InstallUiState.ConfirmInstall]/
 * [InstallUiState.FormatInfo]/[InstallUiState.Error].
 *
 * The root is wrapped in a [Surface] (fills the whole host screen) so it always carries
 * `MaterialTheme.colorScheme.surface` as its background — correct across dark/light/monochrome/e-ink.
 * This matters most for [InstallUiState.Progress] ([InstallProgressScreen] IS the host's entire
 * visible content, with nothing else underneath), but also gives every dialog state a themed
 * backdrop behind the `AlertDialog` popup (which already themes itself).
 */
@Composable
fun InstallZipContent(
    state: InstallUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is InstallUiState.Progress -> InstallProgressScreen(state)

            is InstallUiState.ConfirmInstall -> AbConfirmDialog(
                title = strings.areYouSure,
                message = strings.installDoYouWant(state.displayName ?: "?"),
                confirmText = strings.okay,
                dismissText = strings.cancel,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

            is InstallUiState.FormatInfo -> AbConfirmDialog(
                title = strings.installZipTitle,
                message = state.formatsText,
                confirmText = strings.proceed,
                dismissText = strings.cancel,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

            is InstallUiState.Overwrite -> AbConfirmDialog(
                title = strings.overwriteFilesTitle,
                message = strings.overwriteFiles("\n" + state.files.joinToString("\n")),
                confirmText = strings.yes,
                dismissText = strings.cancel,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

            is InstallUiState.StudyPadImport -> AbConfirmDialog(
                title = strings.areYouSure,
                message = strings.installDoYouWant(state.statsText),
                confirmText = strings.okay,
                dismissText = strings.cancel,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

            InstallUiState.EpubUpgrade -> AbConfirmDialog(
                title = strings.epubUpgradeTitle,
                message = strings.epubUpgradeMessage,
                confirmText = strings.yes,
                dismissText = strings.cancel,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

            is InstallUiState.Error -> AbErrorDialog(
                message = state.message,
                confirmText = strings.okay,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Full-screen progress display for a running install job. Unlike [net.bible.sharedui.components.AbLoadingOverlay]
 * (a scrim + spinner *over* other visible content), this state IS the entire host screen's content
 * while a job runs — there is nothing underneath to dim/block — so it renders a plain centered
 * column instead of a scrim. The bar itself is [ProgressRow], which already embeds the golden-safe
 * determinate/indeterminate split (`AbLoadingIndicator` under the hood) — never a bare
 * `LinearProgressIndicator`, per the Batch 11 Task 4 golden trap (Roborazzi `inspectionMode` does not
 * freeze `InfiniteTransition`, so a real indeterminate animation captures a bistable golden frame).
 */
@Composable
private fun InstallProgressScreen(state: InstallUiState.Progress, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.displayName != null) {
                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            ProgressRow(
                label = state.statusText,
                percent = state.percent ?: 0,
                indeterminate = state.indeterminate || state.percent == null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
