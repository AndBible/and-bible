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

import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentForegroundService

/**
 * Handles LLM-related dialogs: prompt selection, specify-before-run, and regeneration.
 * Extracted from MainBibleActivity and BibleJavascriptInterface to avoid bloating those classes.
 */
class LlmDialogHelper(private val activity: MainBibleActivity) {

    /**
     * Show LLM prompt selector dialog.
     */
    fun showPromptSelector(selection: Selection, context: PromptContext = PromptContext.VERSE_SELECTION) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val prompts = PromptRepository.promptsForContext(context)
            val promptNames = prompts.map { it.name }

            launch(Dispatchers.Main) {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.select_llm_prompt)
                    .setItems(promptNames.toTypedArray()) { _, which ->
                        val selectedPrompt = prompts[which]
                        if (selectedPrompt.specifyBeforeRun) {
                            showSpecifyBeforeRunDialog(selectedPrompt, selection)
                        } else {
                            executePrompt(selectedPrompt, selection)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    /**
     * Show dialog for the user to specify a task before running the prompt.
     * The prompt template is not shown — only an empty text field for the specification.
     */
    private fun showSpecifyBeforeRunDialog(prompt: AgentPrompt, selection: Selection) {
        val editText = EditText(activity).apply {
            setHint(R.string.specify_before_run_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            addView(editText)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.specify_before_run_title)
            .setView(layout)
            .setPositiveButton(R.string.okay) { _, _ ->
                val specification = editText.text.toString().trim()
                if (specification.isEmpty()) return@setPositiveButton
                executePrompt(prompt, selection, userSpecification = specification)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Show regenerate dialog with optional additional instructions and keep previous checkbox.
     */
    fun showRegenerateDialog(pageId: IdType, bibleView: BibleView) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val editText = EditText(activity).apply {
            setHint(R.string.ai_regenerate_instructions_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        val keepPreviousCheckBox = CheckBox(activity).apply {
            setText(R.string.ai_regenerate_keep_previous)
        }
        layout.addView(editText)
        layout.addView(keepPreviousCheckBox)

        AlertDialog.Builder(activity)
            .setTitle(R.string.ai_regenerate_title)
            .setView(layout)
            .setPositiveButton(R.string.ai_document_regenerate) { _, _ ->
                val instructions = editText.text.toString().trim().ifEmpty { null }
                val keepPrevious = keepPreviousCheckBox.isChecked

                activity.lifecycleScope.launch {
                    bibleView.loadDocument(
                        ErrorDocument(
                            activity.getString(R.string.ai_document_regenerating),
                            ErrorSeverity.NORMAL
                        )
                    )
                }
                val workspaceId = activity.windowControl.windowRepository.id
                AgentForegroundService.startRegenerate(
                    context = activity,
                    pageId = pageId,
                    workspaceId = workspaceId,
                    targetWindowId = bibleView.window.id,
                    additionalInstructions = instructions,
                    keepPrevious = keepPrevious
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Execute a prompt via the foreground service so it continues in the background.
     */
    fun executePrompt(prompt: AgentPrompt, selection: Selection, userSpecification: String? = null) {
        val workspaceId = activity.windowControl.windowRepository.id
        AgentForegroundService.startAgent(
            context = activity,
            promptId = prompt.id,
            selection = selection,
            workspaceId = workspaceId,
            userSpecification = userSpecification
        )
    }
}
