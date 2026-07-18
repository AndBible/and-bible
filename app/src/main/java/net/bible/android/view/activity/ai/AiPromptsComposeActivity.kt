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

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.ScreenSettings
import net.bible.service.llm.PromptCsvUtils
import net.bible.service.llm.PromptRepository
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.sharedcore.ai.AiPromptsController
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiPromptsScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Compose host for the AI prompt manager — the new-path twin of classic [AiSettingsActivity]'s
 * prompt-list half. Wires the shared [AiPromptsController] over [PromptService] and renders
 * [AiPromptsScreen].
 *
 * Navigation (`onOpenPrompt`/`onNewPrompt`/`onOpenConnectionSettings`) stays host-side, as the
 * controller intends. They route via [ScreenLauncher] to [Screen.PromptEdit] / [Screen.AiConnectionSettings],
 * so both honor the `use_compose_ui` flag independently of this host.
 *
 * CSV import/export and the help dialog also stay host-side — they need Android SAF (`awaitIntent`)
 * and resource-backed dialogs the shared layer can't own. The flows are ported verbatim from classic
 * [AiSettingsActivity] (`exportPrompts`/`importPrompts`: the editable-vs-addon chooser, the
 * `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT` intents, and the result Toasts/error dialogs),
 * finishing with [PromptService.refresh]. [onResume] calls [PromptService.refresh] for parity with
 * classic's pull-refresh (a child PromptEdit save posts `AppSettingsUpdated`, but some changes — CSV,
 * add-on installs — need an explicit re-query).
 *
 * Debug-only "reset all AI settings" is intentionally NOT surfaced here: the shared [AiPromptsScreen]
 * overflow exposes new/category/connection/export/import/help only, matching the intended prompt
 * manager surface; the classic reset-all lives on in [AiSettingsActivity].
 */
class AiPromptsComposeActivity : ActivityBase() {
    private val service: PromptService by inject()

    private val controller by lazy {
        AiPromptsController(
            service = service,
            scope = lifecycleScope,
            onOpenPrompt = { promptId ->
                startActivity(ScreenLauncher.intentFor(this, Screen.PromptEdit)
                    .putExtra(PromptEditActivity.EXTRA_PROMPT_ID, promptId))
            },
            onNewPrompt = { startActivity(ScreenLauncher.intentFor(this, Screen.PromptEdit)) },
            onOpenConnectionSettings = { ScreenLauncher.open(this, Screen.AiConnectionSettings) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val configured by controller.configured.collectAsState()
                    val groups by controller.groups.collectAsState()
                    val showHidden by controller.showHidden.collectAsState()

                    AiPromptsScreen(
                        configured = configured,
                        groups = groups,
                        showHidden = showHidden,
                        onUp = { finish() },
                        onOpenPrompt = controller::onOpenPrompt,
                        onNewPrompt = controller::onNewPrompt,
                        onToggleFavorite = controller::onToggleFavorite,
                        onSetPromptHidden = controller::onSetPromptHidden,
                        onSetCategoryHidden = controller::onSetCategoryHidden,
                        onDeletePrompt = controller::onDeletePrompt,
                        onDeleteCategory = controller::onDeleteCategory,
                        onMovePrompt = controller::onMovePrompt,
                        onMoveCategory = controller::onMoveCategory,
                        onCreateCategory = controller::onCreateCategory,
                        onRenameCategory = controller::onRenameCategory,
                        onSetShowHidden = controller::onSetShowHidden,
                        onOpenConnectionSettings = controller::onOpenConnectionSettings,
                        onImportCsv = { lifecycleScope.launch { importPrompts() } },
                        onExportCsv = { lifecycleScope.launch { exportPrompts() } },
                        onHelp = { showHelp() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Parity with classic's pull-based refresh: a child PromptEdit save posts AppSettingsUpdated,
        // but CSV imports / add-on installs done here (or elsewhere) need an explicit re-query.
        service.refresh()
    }

    private fun showHelp() {
        CommonUtils.showHelpDialog(
            activity = this,
            titleResId = R.string.help,
            messageResId = R.string.help_ai_settings_text,
            helpPath = "ai.html",
        )
    }

    // --- CSV export/import (ported verbatim from classic AiSettingsActivity) -----------------------

    private suspend fun exportPrompts() {
        try {
            val dao = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
            val userPrompts = withContext(Dispatchers.IO) { dao.allPrompts() }

            if (userPrompts.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_prompts_to_export), Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                putExtra(Intent.EXTRA_TITLE, "ai_prompts_$timestamp.csv")
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            PromptCsvUtils.exportPromptsToCsv(outputStream, userPrompts)
                        } ?: throw IllegalArgumentException("Could not open output stream")
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.prompts_csv_export_success, userPrompts.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting prompts to CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.csv_export_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importPrompts() {
        val options = arrayOf(
            getString(R.string.import_prompts_editable),
            getString(R.string.import_prompts_addon),
        )
        val installAsAddon = suspendCancellableCoroutine<Boolean?> { cont ->
            AlertDialog.Builder(this)
                .setTitle(R.string.import_prompts_csv)
                .setItems(options) { _, which -> cont.resume(which == 1) }
                .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        } ?: return

        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    if (installAsAddon) {
                        installCsvAsAddon(uri)
                    } else {
                        importCsvAsEditable(uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing prompts from CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.csv_import_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importCsvAsEditable(uri: Uri) {
        val importResult = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                PromptCsvUtils.importPromptsFromCsv(inputStream)
            } ?: throw IllegalArgumentException("Could not open input stream")
        }

        if (importResult.errors > 0) {
            val message =
                getString(R.string.csv_import_errors, importResult.created, importResult.updated, importResult.errors) +
                    "\n\n" + importResult.errorMessages.take(5).joinToString("\n") +
                    if (importResult.errorMessages.size > 5) "\n..." else ""

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_prompts_csv))
                .setMessage(message)
                .setPositiveButton(R.string.okay, null)
                .show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.csv_import_success, importResult.created, importResult.updated),
                Toast.LENGTH_SHORT
            ).show()
        }

        service.refresh()
    }

    private suspend fun installCsvAsAddon(uri: Uri) {
        val displayName = contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        } ?: "prompts.csv"

        withContext(Dispatchers.IO) {
            val outDir = File(SharedConstants.modulesDir, "prompts")
            outDir.mkdirs()
            val outFile = File(outDir, displayName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            addCsvPromptBook(outFile)
            AndBibleAddons.clearCaches()
        }

        PromptRepository.clearAddonCache()
        Toast.makeText(this, R.string.install_zip_successfull, Toast.LENGTH_SHORT).show()
        service.refresh()
    }

    companion object {
        private const val TAG = "AiPromptsCompose"
    }
}
