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
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentForegroundService
import net.bible.service.sword.mydocument.MyDocumentBookManager

/**
 * Handles LLM-related dialogs: prompt selection, specify-before-run, and regeneration.
 * Extracted from MainBibleActivity and BibleJavascriptInterface to avoid bloating those classes.
 */
class LlmDialogHelper(private val activity: MainBibleActivity) {

    /** Current prompt selector dialog, kept as field so it can be dismissed on favorite toggle. */
    private var dialog: AlertDialog? = null

    /**
     * Show LLM prompt selector dialog with collapsible category groups.
     * Category expand/collapse state is persisted in local SharedPreferences.
     */
    fun showPromptSelector(selection: Selection, context: PromptContext = PromptContext.VERSE_SELECTION, documentCategory: DocumentCategory? = null) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val grouped = PromptRepository.promptsForContextGrouped(context, documentCategory)
            val favoriteIds = PromptRepository.favoritePromptIds()

            data class PromptGroup(val categoryName: String, val categoryId: IdType?, val prompts: List<AgentPrompt>, val isFavorites: Boolean = false)
            val groups = mutableListOf<PromptGroup>()

            // Collect all prompts for favorites virtual group
            val allPrompts = grouped.values.flatten()
            val favoritePrompts = allPrompts.filter { it.id in favoriteIds }
            if (favoritePrompts.isNotEmpty()) {
                groups.add(PromptGroup(activity.getString(R.string.prompt_category_favorites), PromptRepository.FAVORITES_CATEGORY_ID, favoritePrompts, isFavorites = true))
            }

            // Uncategorized prompts
            grouped[null]?.let { groups.add(PromptGroup(activity.getString(R.string.prompt_category_uncategorized), null, it)) }

            // Then categorized
            for ((category, prompts) in grouped) {
                if (category == null) continue
                groups.add(PromptGroup(category.name, category.id, prompts))
            }

            if (groups.all { it.prompts.isEmpty() }) return@launch

            launch(Dispatchers.Main) {
                val settings = CommonUtils.settings

                val listView = ExpandableListView(activity).apply {
                    setPadding(0, 16, 0, 16)
                    setGroupIndicator(null)
                }

                val adapter = object : BaseExpandableListAdapter() {
                    override fun getGroupCount() = groups.size
                    override fun getChildrenCount(groupPosition: Int) = groups[groupPosition].prompts.size
                    override fun getGroup(groupPosition: Int) = groups[groupPosition]
                    override fun getChild(groupPosition: Int, childPosition: Int) = groups[groupPosition].prompts[childPosition]
                    override fun getGroupId(groupPosition: Int) = groupPosition.toLong()
                    override fun getChildId(groupPosition: Int, childPosition: Int) = childPosition.toLong()
                    override fun hasStableIds() = false
                    override fun isChildSelectable(groupPosition: Int, childPosition: Int) = true

                    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: activity.layoutInflater.inflate(
                            R.layout.manage_prompts_category_header, parent, false
                        )
                        val group = groups[groupPosition]
                        view.findViewById<TextView>(R.id.categoryName).text = group.categoryName
                        view.findViewById<TextView>(R.id.promptCount).text = group.prompts.size.toString()
                        view.findViewById<ImageView>(R.id.expandIndicator).setImageResource(
                            if (isExpanded) R.drawable.ic_arrow_drop_up_grey_24dp
                            else R.drawable.ic_arrow_drop_down_grey_24dp
                        )
                        return view
                    }

                    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: activity.layoutInflater.inflate(
                            R.layout.prompt_selector_item, parent, false
                        )
                        val prompt = groups[groupPosition].prompts[childPosition]
                        view.findViewById<TextView>(R.id.promptName).text = prompt.name
                        view.findViewById<TextView>(R.id.promptDescription).apply {
                            text = prompt.description ?: ""
                            visibility = if (prompt.description.isNullOrEmpty()) View.GONE else View.VISIBLE
                        }
                        val favoriteIcon = view.findViewById<ImageView>(R.id.favoriteIcon)
                        favoriteIcon.setImageResource(
                            if (prompt.id in favoriteIds) R.drawable.ic_star_filled_24
                            else R.drawable.ic_star_outline_24
                        )
                        favoriteIcon.setOnClickListener {
                            activity.lifecycleScope.launch(Dispatchers.IO) {
                                PromptRepository.toggleFavorite(prompt.id)
                                launch(Dispatchers.Main) {
                                    // Rebuild the dialog with updated favorites
                                    dialog?.dismiss()
                                    showPromptSelector(selection, context, documentCategory)
                                }
                            }
                        }
                        return view
                    }
                }

                listView.setAdapter(adapter)

                // Restore expand/collapse state (per-context); favorites always expanded
                var anyExpanded = false
                for (i in groups.indices) {
                    if (groups[i].isFavorites) {
                        listView.expandGroup(i)
                        anyExpanded = true
                        continue
                    }
                    val key = collapsedPrefKey(context, groups[i].categoryId)
                    if (!settings.getBoolean(key, false)) {
                        listView.expandGroup(i)
                        anyExpanded = true
                    }
                }
                if (!anyExpanded && groups.isNotEmpty()) {
                    listView.expandGroup(0)
                }

                // Persist expand/collapse changes (skip favorites group)
                listView.setOnGroupExpandListener { groupPosition ->
                    if (!groups[groupPosition].isFavorites) {
                        val key = collapsedPrefKey(context, groups[groupPosition].categoryId)
                        settings.setBoolean(key, false)
                    }
                }
                listView.setOnGroupCollapseListener { groupPosition ->
                    if (!groups[groupPosition].isFavorites) {
                        val key = collapsedPrefKey(context, groups[groupPosition].categoryId)
                        settings.setBoolean(key, true)
                    }
                }

                dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.select_llm_prompt)
                    .setView(listView)
                    .setNegativeButton(R.string.cancel, null)
                    .show()

                listView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
                    val selectedPrompt = groups[groupPosition].prompts[childPosition]
                    dialog?.dismiss()
                    if (selectedPrompt.specifyBeforeRun) {
                        showSpecifyBeforeRunDialog(selectedPrompt, selection)
                    } else {
                        maybeAskModel(selectedPrompt, selection, userSpecification = null)
                    }
                    true
                }
            }
        }
    }

    private fun collapsedPrefKey(context: PromptContext, categoryId: IdType?): String =
        "llm_cat_collapsed_${context.name}_${categoryId ?: "uncategorized"}"

    /**
     * Show dialog for the user to specify a task before running the prompt.
     * The prompt template is not shown — only an empty text field for the specification.
     */
    internal fun showSpecifyBeforeRunDialog(prompt: AgentPrompt, selection: Selection) {
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
                maybeAskModel(prompt, selection, userSpecification = specification)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * If the global "ask model before run" setting is enabled and the prompt does not have
     * an explicit model override, show a model selection dialog. Otherwise proceed directly.
     */
    internal fun maybeAskModel(prompt: AgentPrompt, selection: Selection, userSpecification: String?) {
        if (CommonUtils.aiSettings.askModelBeforeRun && prompt.configuredModelId == null) {
            showModelSelectionDialog(prompt, selection, userSpecification)
        } else {
            executePrompt(prompt, selection, userSpecification = userSpecification)
        }
    }

    /**
     * Show a dialog listing all configured models with an option to set the selected model
     * as default for this prompt. The user picks one, then prompt execution proceeds with
     * that model override.
     */
    private fun showModelSelectionDialog(prompt: AgentPrompt, selection: Selection, userSpecification: String?) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
            val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
            val defaultModelId = CommonUtils.aiSettings.defaultModelId

            val models = modelDao.all().sortedByDescending { it.id == defaultModelId }
            val providers = providerDao.all().associateBy { it.id }

            val displayNames = models.map { model ->
                val providerName = providers[model.providerConfigId]?.displayName ?: "?"
                val suffix = if (model.id == defaultModelId) " ★" else ""
                val prefix = if (LlmProvider.isModelSupported(model.modelId)) "✓ " else ""
                "$prefix${model.modelId} — $providerName$suffix"
            }

            launch(Dispatchers.Main) {
                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val listView = ListView(activity)
                listView.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, displayNames)
                val checkBox = CheckBox(activity).apply {
                    setText(R.string.set_default_model_for_prompt)
                    setPadding(48, 16, 48, 16)
                }
                layout.addView(listView)
                layout.addView(checkBox)

                val dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.select_model_before_run_title)
                    .setView(layout)
                    .setNegativeButton(R.string.cancel, null)
                    .show()

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedModelId = models[position].id
                    if (checkBox.isChecked) {
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            if (PromptRepository.isBuiltIn(prompt.id)) {
                                PromptRepository.setBuiltinPromptModelOverride(prompt.id, selectedModelId)
                            } else {
                                prompt.configuredModelId = selectedModelId
                                DatabaseContainer.instance.aiSettingsDb.agentPromptDao().update(prompt)
                            }
                        }
                    }
                    dialog.dismiss()
                    executePrompt(prompt, selection, userSpecification = userSpecification, modelOverrideId = selectedModelId)
                }
            }
        }
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
        val freshRunCheckBox = CheckBox(activity).apply {
            setText(R.string.ai_regenerate_fresh_run)
        }
        layout.addView(editText)
        layout.addView(keepPreviousCheckBox)
        layout.addView(freshRunCheckBox)

        AlertDialog.Builder(activity)
            .setTitle(R.string.ai_regenerate_title)
            .setView(layout)
            .setPositiveButton(R.string.ai_document_regenerate) { _, _ ->
                val instructions = editText.text.toString().trim().ifEmpty { null }
                val keepPrevious = keepPreviousCheckBox.isChecked
                val freshRun = freshRunCheckBox.isChecked

                startRegenerateWithModelCheck(pageId, bibleView, instructions, keepPrevious, freshRun)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Check if model selection is needed before starting regeneration.
     * Looks up the prompt associated with the page to check for configuredModelId.
     */
    private fun startRegenerateWithModelCheck(
        pageId: IdType, bibleView: BibleView,
        instructions: String?, keepPrevious: Boolean, freshRun: Boolean
    ) {
        if (!CommonUtils.aiSettings.askModelBeforeRun) {
            startRegenerate(pageId, bibleView, instructions, keepPrevious, freshRun, modelOverrideId = null)
            return
        }

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val page = MyDocumentBookManager.getAIDocumentPage(pageId)
            val promptId = page?.sourcePromptId
            val prompt = promptId?.let { PromptRepository.promptById(it) }

            launch(Dispatchers.Main) {
                if (prompt?.configuredModelId != null) {
                    startRegenerate(pageId, bibleView, instructions, keepPrevious, freshRun, modelOverrideId = null)
                } else {
                    showModelSelectionForRegenerate(pageId, bibleView, instructions, keepPrevious, freshRun)
                }
            }
        }
    }

    private fun showModelSelectionForRegenerate(
        pageId: IdType, bibleView: BibleView,
        instructions: String?, keepPrevious: Boolean, freshRun: Boolean
    ) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
            val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
            val defaultModelId = CommonUtils.aiSettings.defaultModelId

            val models = modelDao.all().sortedByDescending { it.id == defaultModelId }
            val providers = providerDao.all().associateBy { it.id }

            val displayNames = models.map { model ->
                val providerName = providers[model.providerConfigId]?.displayName ?: "?"
                val suffix = if (model.id == defaultModelId) " ★" else ""
                val prefix = if (LlmProvider.isModelSupported(model.modelId)) "✓ " else ""
                "$prefix${model.modelId} — $providerName$suffix"
            }

            launch(Dispatchers.Main) {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.select_model_before_run_title)
                    .setItems(displayNames.toTypedArray()) { _, which ->
                        val selectedModelId = models[which].id
                        startRegenerate(pageId, bibleView, instructions, keepPrevious, freshRun, modelOverrideId = selectedModelId)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun startRegenerate(
        pageId: IdType, bibleView: BibleView,
        instructions: String?, keepPrevious: Boolean, freshRun: Boolean,
        modelOverrideId: IdType?
    ) {
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
            keepPrevious = keepPrevious,
            freshRun = freshRun,
            modelOverrideId = modelOverrideId
        )
    }

    /**
     * Execute a prompt via the foreground service so it continues in the background.
     */
    fun executePrompt(prompt: AgentPrompt, selection: Selection, userSpecification: String? = null, modelOverrideId: IdType? = null) {
        val workspaceId = activity.windowControl.windowRepository.id
        AgentForegroundService.startAgent(
            context = activity,
            promptId = prompt.id,
            selection = selection,
            workspaceId = workspaceId,
            userSpecification = userSpecification,
            modelOverrideId = modelOverrideId
        )
    }

}
