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

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.ai.PromptEditController
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.PromptEditScreen
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the PromptEdit screen — the new-path twin of classic [PromptEditActivity].
 * Wires the shared [PromptEditController] over [PromptService] and renders [PromptEditScreen].
 *
 * **Extras (SAME names as classic, preserved as constants below even where this host has no
 * live setter for them today):** [EXTRA_PROMPT_ID] (String of [net.bible.android.database.IdType];
 * absent = new prompt), [EXTRA_PROMPT_TEMPLATE] (pre-fills a new prompt's template, e.g. from the
 * "customize this prompt" flow), [EXTRA_DEFAULT_CONTEXT] (a [net.bible.service.llm.PromptContext]
 * name to pre-check on a new prompt), [EXTRA_EXECUTE_AFTER_SAVE] (Boolean — see result parity below).
 * The controller itself resolves [EXTRA_PROMPT_ID]/[EXTRA_PROMPT_TEMPLATE]/[EXTRA_DEFAULT_CONTEXT]
 * via [PromptService.prompt]/[PromptService.newPromptData] (constructed once, in the `by lazy`
 * controller, reading `intent` at first access).
 *
 * **Result-className parity.** [saveAndMaybeFinish] mirrors classic `validateAndSave`'s tail exactly:
 * `controller.save()` returns the saved id (or null if [net.bible.sharedcore.ai.PromptEditController.canSave]
 * was false — defensive, the Save icon is already disabled in that case) and, ONLY when
 * [EXTRA_EXECUTE_AFTER_SAVE] is true, `setResult(RESULT_OK, Intent().putExtra(RESULT_PROMPT_ID, savedId))`
 * before `finish()` — same extra/result key names as classic, same guard (no result at all when the
 * caller didn't ask to execute-after-save).
 *
 * **Dirty-back.** [PromptEditScreen] itself gates the up-navigation icon behind an
 * [AbConfirmDialog] whenever `controller.isDirty` (see that screen's kdoc), calling this host's
 * `onBack` (= `finish()`) only once confirmed or when not dirty. The SAME gate is duplicated here
 * via [BackHandler] for the system back gesture/button (which `PromptEditScreen` cannot intercept
 * itself, being a plain composable, not an Activity) — checks `controller.isDirty` and shows an
 * equivalent host-level discard-confirm dialog before finishing.
 *
 * **View tools / Help** stay host-side, launching the CLASSIC [ToolInfoActivity] (Batch 9c task 10
 * ports its own Compose replacement) and the resource-backed help dialog respectively, both ported
 * verbatim from classic [PromptEditActivity].
 *
 * **Copy to customize** ([copyToCustomizeAndFinish]) mirrors classic exactly: on a non-null new id,
 * toast [R.string.prompt_copied], relaunch this SAME Compose host on the new id, and finish the
 * current instance.
 *
 * Reference data for the screen ([net.bible.sharedcore.ai.PromptCategoryVd] categories,
 * tool categories/[net.bible.sharedcore.ai.ToolVd]s, model choices, global tool-permission resolver)
 * is read once from [PromptService] via `remember` — it does not change over this screen's lifetime.
 */
class PromptEditComposeActivity : ActivityBase() {
    private val service: PromptService by inject()

    private val controller by lazy {
        PromptEditController(
            service = service,
            promptId = intent.getStringExtra(EXTRA_PROMPT_ID),
            template = intent.getStringExtra(EXTRA_PROMPT_TEMPLATE),
            defaultContext = intent.getStringExtra(EXTRA_DEFAULT_CONTEXT),
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
                    val strings = LocalStrings.current
                    val state by controller.state.collectAsState()
                    val tab by controller.tab.collectAsState()
                    val availableTabs by controller.availableTabs.collectAsState()
                    val isDirty by controller.isDirty.collectAsState()
                    val canSave by controller.canSave.collectAsState()

                    val categories = remember { service.categories() }
                    val toolsByCategory = remember { service.toolsByCategory() }
                    val modelChoices = remember { service.modelChoices() }

                    var showDiscardConfirm by remember { mutableStateOf(false) }
                    BackHandler {
                        if (isDirty) showDiscardConfirm = true else finish()
                    }

                    PromptEditScreen(
                        state = state,
                        tab = tab,
                        availableTabs = availableTabs,
                        disabledContexts = controller.disabledContexts,
                        hiddenAdvancedKeys = controller.hiddenAdvancedKeys,
                        isDirty = isDirty,
                        canSave = canSave,
                        isReadOnly = state.isReadOnly,
                        isBuiltIn = state.isBuiltIn,
                        isNew = controller.isNew,
                        categories = categories,
                        toolsByCategory = toolsByCategory,
                        modelChoices = modelChoices,
                        globalToolPermission = service::globalToolPermission,
                        onSelectTab = controller::selectTab,
                        onSetName = controller::setName,
                        onSetDescription = controller::setDescription,
                        onSetTemplate = controller::setTemplate,
                        onSetCategory = controller::setCategory,
                        onToggleContext = controller::toggleContext,
                        onSetBibleOnly = controller::setBibleOnly,
                        onSetTextTransformation = controller::setTextTransformation,
                        onSetPermissionMode = controller::setPermissionMode,
                        onSetToolPermission = controller::setToolPermission,
                        onResetToolPermissions = controller::resetToolPermissions,
                        onSetModelOverride = controller::setModelOverride,
                        onSetMaxIterations = controller::setMaxIterations,
                        onSetSwitch = controller::setSwitch,
                        onSave = { saveAndMaybeFinish() },
                        onDelete = { controller.delete(); finish() },
                        onCopyToCustomize = { copyToCustomizeAndFinish() },
                        onViewTools = { startActivity(Intent(this@PromptEditComposeActivity, ToolInfoActivity::class.java)) },
                        onHelp = { showHelp() },
                        onBack = { finish() },
                    )

                    if (showDiscardConfirm) {
                        AbConfirmDialog(
                            title = null,
                            message = strings.discardChangesConfirmation,
                            confirmText = strings.yes,
                            dismissText = strings.no,
                            onConfirm = { showDiscardConfirm = false; finish() },
                            onDismiss = { showDiscardConfirm = false },
                        )
                    }
                }
            }
        }
    }

    /** Mirrors classic `validateAndSave`'s tail — see class kdoc "Result-className parity". */
    private fun saveAndMaybeFinish() {
        val savedId = controller.save() ?: return
        if (intent.getBooleanExtra(EXTRA_EXECUTE_AFTER_SAVE, false)) {
            setResult(RESULT_OK, Intent().putExtra(RESULT_PROMPT_ID, savedId))
        }
        finish()
    }

    /** Mirrors classic `copyToCustomize` verbatim (toast + relaunch on the new id + finish). */
    private fun copyToCustomizeAndFinish() {
        val newId = controller.copyToCustomize() ?: return
        Toast.makeText(this, R.string.prompt_copied, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, PromptEditComposeActivity::class.java).putExtra(EXTRA_PROMPT_ID, newId))
        finish()
    }

    private fun showHelp() {
        CommonUtils.showHelpDialog(
            activity = this,
            titleResId = R.string.help,
            messageResId = R.string.help_prompt_edit_text,
            helpPath = "ai.html#custom-prompts",
        )
    }

    companion object {
        const val EXTRA_PROMPT_ID = "prompt_id"
        const val EXTRA_PROMPT_TEMPLATE = "prompt_template"
        const val EXTRA_EXECUTE_AFTER_SAVE = "execute_after_save"
        const val EXTRA_DEFAULT_CONTEXT = "default_context"
        const val RESULT_PROMPT_ID = "result_prompt_id"
    }
}
