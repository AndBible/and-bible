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
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentTool
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentSessionManager

private const val TAG = "LlmDialogHelper"

/**
 * Handles LLM-related dialogs: prompt selection, custom prompt entry, and regeneration.
 * Extracted from MainBibleActivity and BibleJavascriptInterface to avoid bloating those classes.
 */
class LlmDialogHelper(private val activity: MainBibleActivity) {

    private var pendingCustomPromptSelection: Selection? = null

    val promptEditLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val selection = pendingCustomPromptSelection
        pendingCustomPromptSelection = null
        if (result.resultCode == android.app.Activity.RESULT_OK && selection != null) {
            val promptIdStr = result.data?.getStringExtra(PromptEditActivity.RESULT_PROMPT_ID)
            if (promptIdStr != null) {
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val prompt = PromptRepository.promptById(IdType(promptIdStr))
                    if (prompt != null) {
                        AgentSessionManager.executePrompt(prompt, selection)
                    }
                }
            }
        }
    }

    /**
     * Show LLM prompt selector dialog with a "Custom prompt…" option at the end.
     */
    fun showPromptSelector(selection: Selection, context: PromptContext = PromptContext.VERSE_SELECTION) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val prompts = PromptRepository.promptsForContext(context)
            val promptNames = prompts.map { it.name }.toMutableList()
            promptNames.add(activity.getString(R.string.custom_prompt))

            launch(Dispatchers.Main) {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.select_llm_prompt)
                    .setItems(promptNames.toTypedArray()) { _, which ->
                        if (which < prompts.size) {
                            val selectedPrompt = prompts[which]
                            if (selectedPrompt.editBeforeRun) {
                                showEditBeforeRunDialog(selectedPrompt, selection)
                            } else {
                                executePrompt(selectedPrompt, selection)
                            }
                        } else {
                            showCustomPromptDialog(selection, context)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    /**
     * Show dialog for entering a custom prompt, with option to save it.
     */
    fun showCustomPromptDialog(selection: Selection, context: PromptContext) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val editText = EditText(activity).apply {
            setHint(R.string.custom_prompt_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
        }
        val saveCheckBox = CheckBox(activity).apply {
            setText(R.string.save_as_new_prompt)
        }
        layout.addView(editText)
        layout.addView(saveCheckBox)

        AlertDialog.Builder(activity)
            .setTitle(R.string.custom_prompt)
            .setView(layout)
            .setPositiveButton(R.string.okay) { _, _ ->
                val template = editText.text.toString().trim()
                if (template.isEmpty()) return@setPositiveButton

                if (saveCheckBox.isChecked) {
                    launchPromptEditForExecution(template, selection, context)
                } else {
                    val transientPrompt = AgentPrompt(
                        name = template.take(50),
                        promptTemplate = template,
                        showIn = setOf(context),
                        deniedTools = setOf(AgentTool.FINISH_WITHOUT_DOCUMENT)
                    )
                    executePrompt(transientPrompt, selection)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Show dialog to edit prompt text before execution.
     * Pre-fills with the prompt's template and offers a "Save changes" checkbox.
     */
    private fun showEditBeforeRunDialog(prompt: AgentPrompt, selection: Selection) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val editText = EditText(activity).apply {
            setText(prompt.promptTemplate)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
        }
        val saveCheckBox = CheckBox(activity).apply {
            setText(R.string.save_prompt_changes)
            isChecked = false
            if (BuiltInPrompts.isBuiltIn(prompt.id)) {
                visibility = View.GONE
            }
        }
        layout.addView(editText)
        layout.addView(saveCheckBox)

        AlertDialog.Builder(activity)
            .setTitle(R.string.edit_prompt_before_run_title)
            .setView(layout)
            .setPositiveButton(R.string.okay) { _, _ ->
                val editedTemplate = editText.text.toString().trim()
                if (editedTemplate.isEmpty()) return@setPositiveButton

                if (saveCheckBox.isChecked && !BuiltInPrompts.isBuiltIn(prompt.id)) {
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        prompt.promptTemplate = editedTemplate
                        PromptRepository.updatePrompt(prompt)
                    }
                    executePrompt(prompt, selection)
                } else {
                    val transientPrompt = prompt.copy(promptTemplate = editedTemplate)
                    executePrompt(transientPrompt, selection)
                }
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
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val success = AgentSessionManager.regenerateAIDocument(
                        pageId,
                        targetWindowId = bibleView.window.id,
                        additionalInstructions = instructions,
                        keepPrevious = keepPrevious
                    )
                    if (!success) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Execute a prompt with proper error handling and session tracking.
     */
    fun executePrompt(prompt: AgentPrompt, selection: Selection) {
        val workspaceId = activity.windowControl.windowRepository.id
        val job = activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                AgentSessionManager.executePrompt(prompt, selection)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "LLM prompt execution failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
        AgentSessionManager.getOrCreateSession(workspaceId).job = job
    }

    private fun launchPromptEditForExecution(template: String, selection: Selection, context: PromptContext) {
        pendingCustomPromptSelection = selection
        val intent = Intent(activity, PromptEditActivity::class.java).apply {
            putExtra(PromptEditActivity.EXTRA_PROMPT_TEMPLATE, template)
            putExtra(PromptEditActivity.EXTRA_EXECUTE_AFTER_SAVE, true)
            putExtra(PromptEditActivity.EXTRA_DEFAULT_CONTEXT, context.name)
        }
        promptEditLauncher.launch(intent)
    }
}
