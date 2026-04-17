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
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.service.llm.DynamicModelService
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.ModelPricing

/**
 * Model management dialogs for AI settings fragments.
 * Extracted to keep the fragment classes focused on preference setup.
 */

/** Format a model name with pricing info for display, e.g. "✓ claude-sonnet-4 ($3.00/$15.00)". */
internal fun formatModelWithPricing(modelId: String, pricing: ModelPricing?): String {
    val prefix = if (LlmProvider.isModelSupported(modelId)) "✓ " else ""
    if (pricing == null) return "$prefix$modelId"
    val input = LlmCostTracker.formatPriceCompact(pricing.inputPerMillion)
    val output = LlmCostTracker.formatPriceCompact(pricing.outputPerMillion)
    return "$prefix$modelId ($input/$output)"
}

/**
 * Show dialog to add a new model to a provider.
 * For dynamic providers (OpenRouter), shows category→model two-step selection.
 */
internal fun AiSettingsFragmentBase.showAddModelDialog(providerConfig: LlmProviderConfig, onDone: () -> Unit) {
    val context = requireContext()
    val provider = providerConfig.resolveProvider()
    val (scrollView, layout) = createDialogLayout()

    // Model selection: load available models (dynamic cache → enum fallback)
    val enumModels = provider.modelPricing.map { (id, pricing) ->
        DynamicModelService.DynamicModel(id, id, pricing)
    }
    val availableModels = if (provider.supportsDynamicModels) {
        DynamicModelService.getCachedModels(provider.name) ?: enumModels
    } else enumModels

    val hasUnsupportedModels = availableModels.any { !LlmProvider.isModelSupported(it.id) }
    val hasSupportedModels = availableModels.any { LlmProvider.isModelSupported(it.id) }
    var showUnsupported = !hasSupportedModels // default: show only supported if any exist

    // Category spinner (if models have "/" prefixes like OpenRouter)
    var categorySpinner: Spinner? = null
    val hasCategories = availableModels.any { it.id.contains('/') }
    if (hasCategories) {
        categorySpinner = Spinner(context)
        val categories = mutableListOf(getString(R.string.llm_openrouter_category_all))
        categories.addAll(
            availableModels.asSequence().map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                .map { it.replaceFirstChar { c -> c.uppercaseChar() } }.toList()
        )
        categorySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        addLabeledField(layout, getString(R.string.llm_openrouter_category), categorySpinner)
    }

    // Model spinner
    val modelSpinner = Spinner(context)
    val modelIds = mutableListOf<String>()

    fun updateModelSpinner(category: String?) {
        val filtered = availableModels
            .filter { category == null || it.category.equals(category, ignoreCase = true) }
            .filter { showUnsupported || LlmProvider.isModelSupported(it.id) }
        val sorted = filtered.sortedBy { m ->
            m.pricing?.let { it.inputPerMillion + it.outputPerMillion } ?: Double.MAX_VALUE
        }
        modelIds.clear()
        modelIds.addAll(sorted.map { it.id })
        val customLabel = getString(R.string.llm_custom_model)
        val displayLabels = sorted.map { m -> formatModelWithPricing(m.id, m.pricing) } + customLabel
        modelSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, displayLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }
    // "Show also unsupported" checkbox (only if both supported and unsupported models exist)
    if (hasUnsupportedModels && hasSupportedModels) {
        val unsupportedCheckBox = CheckBox(context).apply {
            text = getString(R.string.show_also_unsupported_models)
            isChecked = showUnsupported
            setOnCheckedChangeListener { _, isChecked ->
                showUnsupported = isChecked
                val selectedCategory = categorySpinner?.let {
                    if (it.selectedItemPosition == 0) null else it.selectedItem?.toString()?.lowercase()
                }
                updateModelSpinner(selectedCategory)
            }
        }
        layout.addView(unsupportedCheckBox)
    }

    updateModelSpinner(null)
    addLabeledField(layout, getString(R.string.llm_openrouter_model), modelSpinner)

    // Custom model ID input
    val customModelInput = EditText(context).apply {
        hint = getString(R.string.llm_custom_model_dialog_message)
        inputType = InputType.TYPE_CLASS_TEXT
        visibility = View.GONE
    }
    layout.addView(customModelInput)

    // Pricing: read-only summary for known models, editable for custom
    val pricingSummary = TextView(context).apply { visibility = View.GONE }
    addLabeledField(layout, getString(R.string.llm_custom_input_price_title), pricingSummary)

    val inputPriceInput = EditText(context).apply {
        hint = getString(R.string.llm_custom_input_price_title)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        visibility = View.GONE
    }
    layout.addView(inputPriceInput)
    val outputPriceInput = EditText(context).apply {
        hint = getString(R.string.llm_custom_output_price_title)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        visibility = View.GONE
    }
    layout.addView(outputPriceInput)

    // Update pricing display when model selection changes
    fun onModelSelected() {
        val pos = modelSpinner.selectedItemPosition
        val isCustom = pos >= modelIds.size
        customModelInput.visibility = if (isCustom) View.VISIBLE else View.GONE

        val selectedId = if (!isCustom && pos >= 0 && pos < modelIds.size) modelIds[pos] else null
        val knownPricing = selectedId?.let { id ->
            availableModels.find { it.id == id }?.pricing ?: LlmProvider.findPricing(id)
        }
        if (knownPricing != null) {
            pricingSummary.text = getString(R.string.model_pricing_summary, LlmCostTracker.formatPriceCompact(knownPricing.inputPerMillion), LlmCostTracker.formatPriceCompact(knownPricing.outputPerMillion))
            pricingSummary.visibility = View.VISIBLE
            inputPriceInput.visibility = View.GONE
            outputPriceInput.visibility = View.GONE
        } else {
            pricingSummary.visibility = View.GONE
            inputPriceInput.visibility = View.VISIBLE
            outputPriceInput.visibility = View.VISIBLE
        }
    }

    modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onModelSelected() }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
    categorySpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val selectedCategory = if (position == 0) null else categorySpinner.selectedItem?.toString()?.lowercase()
            updateModelSpinner(selectedCategory)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
    onModelSelected()

    AlertDialog.Builder(context)
        .setTitle(R.string.add_model)
        .setView(scrollView)
        .setPositiveButton(R.string.okay) { _, _ ->
            val pos = modelSpinner.selectedItemPosition
            val isCustom = pos >= modelIds.size
            val modelId = if (isCustom) {
                customModelInput.text.toString().trim()
            } else if (pos >= 0) {
                modelIds[pos]
            } else return@setPositiveButton
            if (modelId.isBlank()) return@setPositiveButton

            val orderNumber = modelDao.getByProvider(providerConfig.id).size
            val knownPricing = LlmPricing.isKnownModel(modelId)
            val newModel = if (knownPricing) {
                LlmConfiguredModel.create(providerConfig.id, modelId, orderNumber)
            } else {
                LlmConfiguredModel(
                    providerConfigId = providerConfig.id,
                    modelId = modelId,
                    orderNumber = orderNumber,
                    inputPricePerMillion = inputPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                    outputPricePerMillion = outputPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                )
            }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    modelDao.insert(newModel)
                    if (settings.defaultModelId == null) {
                        settings.defaultModelId = newModel.id
                    }
                }
                Toast.makeText(requireContext(), R.string.model_saved, Toast.LENGTH_SHORT).show()
                refreshAll()
                onDone()
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

/**
 * Show dialog to edit an existing configured model.
 */
internal fun AiSettingsFragmentBase.showEditModelDialog(model: LlmConfiguredModel, onDone: () -> Unit) {
    val context = requireContext()
    val (scrollView, layout) = createDialogLayout()

    val modelIdView = TextView(context).apply {
        text = model.modelId
        setTextAppearance(android.R.style.TextAppearance_Small)
    }
    addLabeledField(layout, getString(R.string.llm_openrouter_model), modelIdView)

    if (LlmProvider.isModelSupported(model.modelId)) {
        val supportedView = TextView(context).apply {
            text = getString(R.string.model_supported_badge)
            setTextAppearance(android.R.style.TextAppearance_Small)
        }
        layout.addView(supportedView)
    }

    // Pricing: read-only for known models, editable for custom
    val hasKnownPricing = LlmPricing.isKnownModel(model.modelId)
    var inputPriceInput: EditText? = null
    var outputPriceInput: EditText? = null
    if (hasKnownPricing) {
        val pricingSummary = TextView(context).apply {
            text = getString(R.string.model_pricing_summary, LlmCostTracker.formatPriceCompact(model.inputPricePerMillion), LlmCostTracker.formatPriceCompact(model.outputPricePerMillion))
        }
        addLabeledField(layout, getString(R.string.llm_custom_input_price_title), pricingSummary)
    } else {
        inputPriceInput = EditText(context).apply {
            setText(model.inputPricePerMillion.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        addLabeledField(layout, getString(R.string.llm_custom_input_price_title), inputPriceInput)
        outputPriceInput = EditText(context).apply {
            setText(model.outputPricePerMillion.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        addLabeledField(layout, getString(R.string.llm_custom_output_price_title), outputPriceInput)
    }

    val isCurrentDefault = model.id == settings.defaultModelId
    val defaultCheckBox = CheckBox(context).apply {
        text = getString(R.string.model_set_default)
        isChecked = isCurrentDefault
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = (12 * resources.displayMetrics.density).toInt()
        layoutParams = params
    }
    layout.addView(defaultCheckBox)

    AlertDialog.Builder(context)
        .setTitle(R.string.edit_model_title)
        .setView(scrollView)
        .setPositiveButton(R.string.okay) { _, _ ->
            val updated = if (hasKnownPricing) model else model.copy(
                inputPricePerMillion = inputPriceInput?.text.toString().toDoubleOrNull() ?: 0.0,
                outputPricePerMillion = outputPriceInput?.text.toString().toDoubleOrNull() ?: 0.0,
            )
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    modelDao.update(updated)
                    if (defaultCheckBox.isChecked) {
                        settings.defaultModelId = model.id
                    } else if (isCurrentDefault) {
                        settings.defaultModelId = null
                    }
                }
                Toast.makeText(requireContext(), R.string.model_saved, Toast.LENGTH_SHORT).show()
                refreshAll()
                onDone()
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .setNeutralButton(R.string.delete) { _, _ ->
            confirmDeleteModel(model) {
                refreshAll()
                onDone()
            }
        }
        .show()
}

internal fun AiSettingsFragmentBase.confirmDeleteModel(model: LlmConfiguredModel, onDone: () -> Unit) {
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.delete)
        .setMessage(getString(R.string.model_delete_confirm, model.displayName))
        .setPositiveButton(R.string.yes) { _, _ ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    modelDao.delete(model)
                    if (settings.defaultModelId == model.id) {
                        settings.defaultModelId = modelDao.all().firstOrNull()?.id
                    }
                }
                Toast.makeText(requireContext(), R.string.model_deleted, Toast.LENGTH_SHORT).show()
                refreshAll()
                onDone()
            }
        }
        .setNegativeButton(R.string.no, null)
        .show()
}
