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
import android.app.AlertDialog
import android.graphics.Typeface
import android.util.TypedValue
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsActivityBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.settings.PreferenceStore
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.ApiFormat
import net.bible.service.llm.DynamicModelService
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmProcessingService
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.ModelPricing
import net.bible.service.llm.ProviderTier
import net.bible.service.llm.getApiKey
import net.bible.service.llm.removeApiKey
import net.bible.service.llm.setApiKey

class AiConnectionSettingsActivity : ActivityBase() {
    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        title = getString(R.string.ai_connection_settings)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, AiConnectionSettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

class AiConnectionSettingsFragment : PreferenceFragmentCompat() {

    private val settings get() = CommonUtils.aiSettings
    private val dao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    private lateinit var gettingStartedPref: Preference
    private lateinit var providersCategory: PreferenceCategory
    private lateinit var addProviderPref: Preference
    private lateinit var modelsCategory: PreferenceCategory
    private lateinit var addModelPref: Preference
    private lateinit var behaviorCategory: PreferenceCategory
    private lateinit var manageToolPermissionsPref: Preference
    private lateinit var manageAiDocumentsPref: Preference
    private lateinit var commentaryMaxResponsePref: Preference
    private lateinit var maxIterationsPref: Preference
    private lateinit var usageCategory: PreferenceCategory
    private lateinit var usageSummaryPref: Preference
    private lateinit var resetUsagePref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.ai_connection_settings, rootKey)

        gettingStartedPref = preferenceScreen.findPreference("ai_getting_started")!!
        providersCategory = preferenceScreen.findPreference("ai_providers_category")!!
        addProviderPref = preferenceScreen.findPreference("ai_add_provider")!!
        modelsCategory = preferenceScreen.findPreference("ai_models_category")!!
        addModelPref = preferenceScreen.findPreference("ai_add_model")!!
        behaviorCategory = preferenceScreen.findPreference("ai_behavior_category")!!
        manageToolPermissionsPref = preferenceScreen.findPreference("manage_tool_permissions")!!
        manageAiDocumentsPref = preferenceScreen.findPreference("manage_ai_documents")!!
        commentaryMaxResponsePref = preferenceScreen.findPreference("commentary_max_response_chars")!!
        maxIterationsPref = preferenceScreen.findPreference("agent_max_iterations")!!
        usageCategory = preferenceScreen.findPreference("ai_usage_category")!!
        usageSummaryPref = preferenceScreen.findPreference("llm_usage_summary")!!
        resetUsagePref = preferenceScreen.findPreference("llm_reset_usage")!!

        setupGettingStarted()
        setupAddProvider()
        setupAddModel()
        setupToolPermissions()
        setupDocumentFilter()
        setupCommentaryMaxResponse()
        setupMaxIterations()
        setupUsage()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        updateToolPermissionsSummary()
        updateDocumentFilterSummary()
    }

    private fun refreshAll() {
        refreshProviderList()
        refreshModelList()
        updateVisibility()
    }

    private fun hasAnyProvider(): Boolean = dao.getCount() > 0

    /**
     * Progressive disclosure:
     * - Getting started: visible until at least one provider is added
     * - Add provider: always visible
     * - Behavior/Usage: visible once at least one provider exists
     */
    private fun updateVisibility() {
        val hasProviders = hasAnyProvider()
        gettingStartedPref.isVisible = !hasProviders
        modelsCategory.isVisible = hasProviders
        behaviorCategory.isVisible = hasProviders
        usageCategory.isVisible = hasProviders

        if (hasProviders) updateUsageSummary()
    }

    private fun setupAddModel() {
        addModelPref.setOnPreferenceClickListener {
            showAddModelDialogWithProviderSelection()
            true
        }
    }

    /** Add model dialog that first asks the user to select a provider. */
    private fun showAddModelDialogWithProviderSelection() {
        val providers = dao.all()
        if (providers.isEmpty()) return
        if (providers.size == 1) {
            // Only one provider — skip selection
            showAddModelDialog(providers.first()) { refreshModelList() }
        } else {
            val items = providers.map { it.displayName }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.model_select_provider)
                .setItems(items) { _, which ->
                    showAddModelDialog(providers[which]) { refreshModelList() }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun refreshModelList() {
        // Remove all dynamic model prefs (keep "ai_add_model")
        val toRemove = mutableListOf<Preference>()
        for (i in 0 until modelsCategory.preferenceCount) {
            val pref = modelsCategory.getPreference(i)
            if (pref.key != "ai_add_model") {
                toRemove.add(pref)
            }
        }
        toRemove.forEach { modelsCategory.removePreference(it) }

        val allModels = modelDao.all()
        val providerConfigs = dao.all().associateBy { it.id }
        val defaultModelId = settings.defaultModelId

        for (model in allModels) {
            val provider = providerConfigs[model.providerConfigId]
            val pref = Preference(requireContext()).apply {
                key = "model_${model.id}"
                title = if (model.id == defaultModelId) "★ ${model.modelId}" else model.modelId
                summary = buildString {
                    append(provider?.displayName ?: "?")
                    append(" — ")
                    append(model.modelId)
                    if (model.inputPricePerMillion > 0 || model.outputPricePerMillion > 0) {
                        append(" (${LlmCostTracker.formatPriceCompact(model.inputPricePerMillion)}/${LlmCostTracker.formatPriceCompact(model.outputPricePerMillion)})")
                    }
                    val cost = LlmCostTracker.getCumulativeCost(model.id)
                    if (cost > 0) {
                        append("\n")
                        append(LlmCostTracker.formatCost(cost))
                    }
                }
                setOnPreferenceClickListener {
                    showEditModelDialog(model) { refreshModelList() }
                    true
                }
            }
            // Insert before "Add model" button
            modelsCategory.addPreference(pref)
            pref.order = addModelPref.order - 1
        }
    }

    private fun refreshProviderList() {
        // Remove all dynamic provider prefs (keep "add_provider")
        val toRemove = mutableListOf<Preference>()
        for (i in 0 until providersCategory.preferenceCount) {
            val pref = providersCategory.getPreference(i)
            if (pref.key != "ai_add_provider" && pref.key != "ai_getting_started") {
                toRemove.add(pref)
            }
        }
        toRemove.forEach { providersCategory.removePreference(it) }

        val configs = dao.all()
        for (config in configs) {
            val pref = Preference(requireContext()).apply {
                key = "provider_${config.id}"
                title = config.displayName
                val apiKey = config.getApiKey()
                summary = if (apiKey.isNotBlank()) {
                    val suffix = apiKey.takeLast(4)
                    getString(R.string.ai_provider_api_key_masked, getString(R.string.ai_provider_api_key), suffix)
                } else {
                    getString(R.string.ai_provider_api_key_not_set)
                }
                setOnPreferenceClickListener {
                    showEditProviderDialog(config)
                    true
                }
            }
            // Insert before the "add provider" pref
            val addProviderIndex = providersCategory.preferenceCount - 1
            providersCategory.addPreference(pref)
            pref.order = addProviderIndex
        }

        // Re-order: add provider pref at the end
        addProviderPref.order = providersCategory.preferenceCount
        updateVisibility()
    }


    private fun confirmDeleteProvider(config: LlmProviderConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_provider_delete)
            .setMessage(getString(R.string.ai_provider_delete_confirm, config.displayName))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        config.removeApiKey()
                        // CASCADE will delete LlmConfiguredModel rows, which may clear defaultModelId
                        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
                        val deletedModelIds = modelDao.getByProvider(config.id).map { it.id }.toSet()
                        dao.delete(config)
                        // If the global default model was one of the deleted models, clear it
                        val currentDefault = settings.defaultModelId
                        if (currentDefault != null && currentDefault in deletedModelIds) {
                            // Pick first remaining configured model as new default
                            val remaining = modelDao.all()
                            settings.defaultModelId = remaining.firstOrNull()?.id
                        }
                    }
                    Toast.makeText(requireContext(), R.string.ai_provider_deleted, Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun setupAddProvider() {
        addProviderPref.setOnPreferenceClickListener {
            showAddProviderTypeDialog()
            true
        }
    }

    private fun showAddProviderTypeDialog() {
        val existingTypes = dao.all().map { it.providerType }.toSet()
        val availableProviders = LlmProvider.entries.filter {
            it == LlmProvider.CUSTOM || it.name !in existingTypes
        }

        // Build flat list with tier headers as null entries
        val items = mutableListOf<Pair<String, LlmProvider?>>() // displayName to provider (null = header)
        for (tier in ProviderTier.entries) {
            val inTier = availableProviders.filter { it.tier == tier }
            if (inTier.isEmpty()) continue
            if (tier == ProviderTier.RECOMMENDED) {
                items.add(getString(R.string.ai_provider_tier_recommended) to null)
            } else if (tier == ProviderTier.COMMUNITY) {
                items.add(getString(R.string.ai_provider_tier_community) to null)
            }
            for (p in inTier) {
                val name = if (p == LlmProvider.CUSTOM) getString(R.string.llm_provider_custom) else p.displayName
                items.add(name to p)
            }
        }

        val adapter = object : ArrayAdapter<String>(
            requireContext(), android.R.layout.simple_list_item_1, items.map { it.first }
        ) {
            override fun isEnabled(position: Int) = items[position].second != null
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view as TextView
                if (items[position].second == null) {
                    tv.setTypeface(null, Typeface.BOLD)
                    tv.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                } else {
                    tv.setTypeface(null, Typeface.NORMAL)
                    tv.setTextColor(resources.getColor(android.R.color.primary_text_light, null))
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }
                return view
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_provider_select_type)
            .setAdapter(adapter) { _, which ->
                items[which].second?.let { provider ->
                    showEditProviderDialog(null, provider)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private data class ProviderDialogFields(
        val nameInput: EditText,
        val apiKeyInput: EditText,
        val endpointInput: EditText?,
        val apiFormatSpinner: Spinner?,
    )

    /**
     * Show a dialog to create or edit a provider config.
     * If config is null, creates a new one of the given providerType.
     */
    private fun showEditProviderDialog(config: LlmProviderConfig?, providerType: LlmProvider? = null) {
        val isNew = config == null
        val provider = providerType ?: config!!.resolveProvider()
        val isCustom = provider == LlmProvider.CUSTOM

        val (scrollView, layout) = createDialogLayout()

        val fields = buildProviderDialogLayout(layout, config, provider, isCustom)

        val dialogTitle = if (isNew) getString(R.string.ai_add_provider) else getString(R.string.ai_provider_edit)

        val dialog = AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setView(scrollView)
            .setPositiveButton(R.string.okay) { _, _ ->
                saveProviderConfig(config, provider, isCustom, fields)
            }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                if (!isNew) {
                    setNeutralButton(R.string.ai_provider_delete) { dlg, _ ->
                        dlg.dismiss()
                        confirmDeleteProvider(config)
                    }
                }
            }
            .show()

        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.isEnabled = fields.apiKeyInput.text.toString().trim().isNotBlank()
        fields.apiKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                okButton.isEnabled = s?.toString()?.trim()?.isNotBlank() == true
            }
        })
    }

    private fun buildProviderDialogLayout(
        layout: LinearLayout,
        config: LlmProviderConfig?,
        provider: LlmProvider,
        isCustom: Boolean,
    ): ProviderDialogFields {
        val context = requireContext()

        val nameInput = EditText(context).apply {
            hint = getString(R.string.ai_provider_name_hint)
            setText(config?.displayName ?: if (isCustom) "" else provider.displayName)
            isEnabled = isCustom
            inputType = InputType.TYPE_CLASS_TEXT
        }
        addLabeledField(layout, getString(R.string.ai_provider_name), nameInput)

        val apiKeyInput = EditText(context).apply {
            hint = getString(R.string.ai_provider_api_key)
            setText(config?.getApiKey() ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        addLabeledField(layout, getString(R.string.ai_provider_api_key), apiKeyInput)

        if (provider.apiKeyUrl != null) {
            val linkView = TextView(context).apply {
                text = htmlToSpan("<a href=\"${provider.apiKeyUrl}\">${getString(R.string.easy_setup_api_key_instructions)} ${provider.displayName}</a>")
                movementMethod = LinkMovementMethod.getInstance()
            }
            layout.addView(linkView)
        }

        val endpointInput = if (isCustom) {
            EditText(context).apply {
                hint = getString(R.string.ai_provider_endpoint_hint)
                setText(config?.endpoint ?: "")
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }.also { addLabeledField(layout, getString(R.string.ai_provider_endpoint), it) }
        } else null

        val apiFormatSpinner = if (isCustom) {
            Spinner(context).apply {
                val formats = ApiFormat.entries.map { it.name }.toTypedArray()
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, formats).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val currentFormat = (config?.apiFormat ?: ApiFormat.OPENAI).name
                val idx = formats.indexOf(currentFormat)
                if (idx >= 0) setSelection(idx)
            }.also { addLabeledField(layout, getString(R.string.ai_provider_api_format), it) }
        } else null

        return ProviderDialogFields(
            nameInput = nameInput,
            apiKeyInput = apiKeyInput,
            endpointInput = endpointInput,
            apiFormatSpinner = apiFormatSpinner,
        )
    }

    private fun saveProviderConfig(
        config: LlmProviderConfig?,
        provider: LlmProvider,
        isCustom: Boolean,
        fields: ProviderDialogFields,
    ) {
        val isNew = config == null
        val displayName = fields.nameInput.text.toString().trim().ifEmpty { provider.displayName }
        val apiKey = fields.apiKeyInput.text.toString().trim()
        val endpoint = fields.endpointInput?.text?.toString()?.trim()
        val apiFormat = fields.apiFormatSpinner?.selectedItem?.toString()?.let {
            try { ApiFormat.valueOf(it) } catch (_: IllegalArgumentException) { null }
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (isNew) {
                    val newConfig = LlmProviderConfig(
                        providerType = provider.name,
                        displayName = displayName,
                        endpoint = if (isCustom) endpoint else null,
                        apiFormat = if (isCustom) apiFormat else null,
                        orderNumber = dao.getCount(),
                    )
                    dao.insert(newConfig)
                    newConfig.setApiKey(apiKey)

                    // Fetch dynamic models in the background
                    if (provider.supportsDynamicModels) {
                        val fetchKey = if (provider.modelsEndpointPublic) "" else apiKey
                        DynamicModelService.fetchModels(provider.endpoint, fetchKey, provider.name)
                    }
                } else {
                    val updated = config.copy(
                        displayName = displayName,
                        endpoint = if (isCustom) endpoint else config.endpoint,
                        apiFormat = if (isCustom) apiFormat else config.apiFormat,
                    )
                    dao.update(updated)
                    updated.setApiKey(apiKey)
                }
            }
            Toast.makeText(requireContext(), R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
            refreshAll()
        }
    }

    // ---- Model management dialogs ----

    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    /**
     * Show dialog to add a new model to a provider.
     * For dynamic providers (OpenRouter), shows category→model two-step selection.
     */
    private fun showAddModelDialog(providerConfig: LlmProviderConfig, onDone: () -> Unit) {
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
            val filtered = if (category == null) availableModels
                else availableModels.filter { it.category.equals(category, ignoreCase = true) }
            val sorted = filtered.sortedBy { m ->
                m.pricing?.let { it.inputPerMillion + it.outputPerMillion } ?: Double.MAX_VALUE
            }
            modelIds.clear()
            modelIds.addAll(sorted.map { it.id })
            // Add "Custom…" option at end
            val customLabel = getString(R.string.llm_custom_model)
            val displayLabels = sorted.map { m ->
                formatModelWithPricing(m.id, m.pricing)
            } + customLabel
            modelSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, displayLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
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
                // Known model — show read-only pricing summary
                pricingSummary.text = getString(R.string.model_pricing_summary, LlmCostTracker.formatPriceCompact(knownPricing.inputPerMillion), LlmCostTracker.formatPriceCompact(knownPricing.outputPerMillion))
                pricingSummary.visibility = View.VISIBLE
                inputPriceInput.visibility = View.GONE
                outputPriceInput.visibility = View.GONE
            } else {
                // Unknown model — show editable price fields
                pricingSummary.visibility = View.GONE
                inputPriceInput.visibility = View.VISIBLE
                outputPriceInput.visibility = View.VISIBLE
            }
        }

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onModelSelected()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        categorySpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = if (position == 0) null
                    else categorySpinner.selectedItem?.toString()?.lowercase()
                updateModelSpinner(selectedCategory)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Trigger initial auto-fill
        onModelSelected()

        AlertDialog.Builder(context)
            .setTitle(R.string.add_model_title)
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
                        // If this is the first configured model globally, set it as default
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
    private fun showEditModelDialog(model: LlmConfiguredModel, onDone: () -> Unit) {
        val context = requireContext()
        val (scrollView, layout) = createDialogLayout()

        // Model ID (read-only)
        val modelIdView = TextView(context).apply {
            text = model.modelId
            setTextAppearance(android.R.style.TextAppearance_Small)
        }
        addLabeledField(layout, getString(R.string.llm_openrouter_model), modelIdView)

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

        // Default model checkbox
        val isCurrentDefault = model.id == settings.defaultModelId
        val defaultCheckBox = CheckBox(context).apply {
            text = getString(R.string.model_set_default)
            isChecked = isCurrentDefault
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
                            // Unchecked default — clear it
                            settings.defaultModelId = null
                        }
                    }
                    Toast.makeText(requireContext(), R.string.model_saved, Toast.LENGTH_SHORT).show()
                    refreshAll()
                    onDone()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.ai_provider_delete) { _, _ ->
                confirmDeleteModel(model) {
                    refreshAll()
                    onDone()
                }
            }
            .show()
    }

    private fun confirmDeleteModel(model: LlmConfiguredModel, onDone: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_provider_delete)
            .setMessage(getString(R.string.model_delete_confirm, model.displayName))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        modelDao.delete(model)
                        // If this was the default model, clear or reassign
                        if (settings.defaultModelId == model.id) {
                            val remaining = modelDao.all()
                            settings.defaultModelId = remaining.firstOrNull()?.id
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

    /** Format a model name with pricing info for display, e.g. "claude-sonnet-4 ($3.00/$15.00)". */
    private fun formatModelWithPricing(modelId: String, pricing: ModelPricing?): String {
        if (pricing == null) return modelId
        val input = LlmCostTracker.formatPriceCompact(pricing.inputPerMillion)
        val output = LlmCostTracker.formatPriceCompact(pricing.outputPerMillion)
        return "$modelId ($input/$output)"
    }

    /** Create a ScrollView containing a padded vertical LinearLayout for dialog content. */
    private fun createDialogLayout(): Pair<ScrollView, LinearLayout> {
        val context = requireContext()
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        scrollView.addView(layout)
        return scrollView to layout
    }

    private fun addLabeledField(layout: LinearLayout, label: String, field: View) {
        val density = resources.displayMetrics.density
        val labelView = TextView(requireContext()).apply {
            text = label
            setTextAppearance(android.R.style.TextAppearance_Small)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (8 * density).toInt()
            layoutParams = params
        }
        layout.addView(labelView)

        val fieldParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        fieldParams.bottomMargin = (4 * density).toInt()
        field.layoutParams = fieldParams
        layout.addView(field)
    }

    // ---- Easy Setup wizard ----

    private data class RecommendedSetup(
        val provider: LlmProvider,
        val modelId: String,
        val description: String,
        val badge: String? = null,
    )

    private fun getRecommendedSetups() = listOf(
        RecommendedSetup(LlmProvider.GEMINI, "gemini-2.5-flash",
            getString(R.string.easy_setup_gemini_desc), getString(R.string.easy_setup_free_tier)),
        RecommendedSetup(LlmProvider.ANTHROPIC, "claude-haiku-4-5",
            getString(R.string.easy_setup_anthropic_desc)),
        RecommendedSetup(LlmProvider.OPENAI, "gpt-4o-mini",
            getString(R.string.easy_setup_openai_desc)),
    )

    private fun setupGettingStarted() {
        gettingStartedPref.setOnPreferenceClickListener {
            showEasySetupStep1()
            true
        }
    }

    /** Step 1: Choose a recommended provider+model */
    private fun showEasySetupStep1() {
        val setups = getRecommendedSetups()
        val items = setups.map { setup ->
            "${setup.provider.displayName} — ${setup.description}"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.easy_setup_title)
            .setItems(items) { _, which ->
                showEasySetupStep2(setups[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Step 2: Enter API key and test connection */
    private fun showEasySetupStep2(setup: RecommendedSetup) {
        val context = requireContext()
        val (scrollView, layout) = createDialogLayout()

        // Instructions with link
        val instructionHtml = getString(R.string.easy_setup_api_key_instructions) +
            "<br><a href=\"${setup.provider.apiKeyUrl}\">${setup.provider.displayName}</a>"
        val instructionView = TextView(context).apply {
            text = htmlToSpan(instructionHtml)
            movementMethod = LinkMovementMethod.getInstance()
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = params
        }
        layout.addView(instructionView)

        val apiKeyInput = EditText(context).apply {
            hint = getString(R.string.ai_provider_api_key)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        layout.addView(apiKeyInput)

        // Status text for test results
        val statusText = TextView(context).apply {
            visibility = View.GONE
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = params
        }
        layout.addView(statusText)

        val dialog = AlertDialog.Builder(context)
            .setTitle(getString(R.string.easy_setup_enter_api_key) + " — " + setup.provider.displayName)
            .setView(scrollView)
            .setPositiveButton(R.string.okay) { _, _ ->
                val apiKey = apiKeyInput.text.toString().trim()
                if (apiKey.isNotBlank()) {
                    performEasySetup(setup, apiKey)
                }
            }
            .setNeutralButton(R.string.easy_setup_test_connection, null) // Override below to prevent dismiss
            .setNegativeButton(R.string.cancel, null)
            .show()

        // Override neutral button to test without dismissing
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isBlank()) return@setOnClickListener
            statusText.text = getString(R.string.easy_setup_testing)
            statusText.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        LlmProcessingService.testApiConnection(setup.provider, setup.modelId, apiKey)
                    }
                    statusText.text = getString(R.string.easy_setup_success)
                } catch (e: Exception) {
                    statusText.text = getString(R.string.easy_setup_failed, e.message ?: getString(R.string.unknown_error))
                }
            }
        }

        // Enable OK only when API key is entered
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.isEnabled = false
        apiKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                okButton.isEnabled = s?.toString()?.trim()?.isNotBlank() == true
            }
        })
    }

    /** Create provider + model + set default from easy setup selection. */
    private fun performEasySetup(setup: RecommendedSetup, apiKey: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Create provider config
                val providerConfig = LlmProviderConfig(
                    providerType = setup.provider.name,
                    displayName = setup.provider.displayName,
                    orderNumber = dao.getCount(),
                )
                dao.insert(providerConfig)
                providerConfig.setApiKey(apiKey)

                // Create configured model
                val configuredModel = LlmConfiguredModel.create(
                    providerConfigId = providerConfig.id,
                    modelId = setup.modelId,
                )
                modelDao.insert(configuredModel)
                settings.defaultModelId = configuredModel.id

                // Fetch dynamic model list in background (implicit, no user prompt)
                if (setup.provider.supportsDynamicModels) {
                    val fetchKey = if (setup.provider.modelsEndpointPublic) "" else apiKey
                    DynamicModelService.fetchModels(setup.provider.endpoint, fetchKey, setup.provider.name)
                }
            }
            refreshAll()
            showEasySetupStep3()
        }
    }

    /** Step 3: Done! */
    private fun showEasySetupStep3() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.easy_setup_done_title)
            .setMessage(R.string.easy_setup_done_message)
            .setPositiveButton(R.string.okay, null)
            .show()
    }

    private fun setupToolPermissions() {
        updateToolPermissionsSummary()
        manageToolPermissionsPref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), GlobalToolPermissionsActivity::class.java))
            true
        }
    }

    private fun updateToolPermissionsSummary() {
        val allowed = settings.permanentlyAllowedTools.size
        val denied = settings.permanentlyDeniedTools.size
        val total = allowed + denied
        manageToolPermissionsPref.summary = if (total > 0) {
            getString(R.string.manage_tool_permissions_summary) + " ($total)"
        } else {
            getString(R.string.manage_tool_permissions_summary)
        }
    }

    private fun setupDocumentFilter() {
        updateDocumentFilterSummary()
        manageAiDocumentsPref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AiDocumentFilterActivity::class.java))
            true
        }
    }

    private fun updateDocumentFilterSummary() {
        val excludedCount = settings.aiExcludedDocuments.size
        manageAiDocumentsPref.summary = if (excludedCount > 0) {
            getString(R.string.ai_document_filter_summary_count, excludedCount)
        } else {
            getString(R.string.ai_document_filter_summary)
        }
    }

    private fun setupCommentaryMaxResponse() {
        updateCommentaryMaxResponseSummary()
        commentaryMaxResponsePref.setOnPreferenceClickListener {
            val ctx = requireContext()
            val input = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.commentaryMaxResponseTokens.toString())
                selectAll()
            }
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
                addView(TextView(ctx).apply {
                    text = getString(R.string.commentary_max_response_dialog_message)
                    setPadding(0, 0, 0, pad / 2)
                })
                addView(input)
            }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.commentary_max_response_dialog_title)
                .setView(container)
                .setPositiveButton(R.string.okay) { _, _ ->
                    val value = input.text.toString().toIntOrNull() ?: 0
                    settings.commentaryMaxResponseTokens = maxOf(0, value)
                    updateCommentaryMaxResponseSummary()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    private fun updateCommentaryMaxResponseSummary() {
        val value = settings.commentaryMaxResponseTokens
        commentaryMaxResponsePref.summary = if (value <= 0) {
            getString(R.string.commentary_max_response_no_limit)
        } else {
            getString(R.string.commentary_max_response_value, "%,d".format(value))
        }
    }

    private fun setupMaxIterations() {
        updateMaxIterationsSummary()
        maxIterationsPref.setOnPreferenceClickListener {
            val ctx = requireContext()
            val input = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.maxIterations.toString())
                selectAll()
            }
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, 0)
                addView(TextView(ctx).apply {
                    text = getString(R.string.agent_max_iterations_summary)
                    setPadding(0, 0, 0, pad / 2)
                })
                addView(input)
            }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.agent_max_iterations_title)
                .setView(container)
                .setPositiveButton(R.string.okay) { _, _ ->
                    val value = input.text.toString().toIntOrNull() ?: 10
                    settings.maxIterations = maxOf(0, value)
                    updateMaxIterationsSummary()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    private fun updateMaxIterationsSummary() {
        val value = settings.maxIterations
        maxIterationsPref.summary = if (value <= 0) {
            getString(R.string.agent_max_iterations_summary) + " (unlimited)"
        } else {
            getString(R.string.agent_max_iterations_summary) + " ($value)"
        }
    }

    private fun setupUsage() {
        updateUsageSummary()
        resetUsagePref.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.llm_reset_usage_confirm_title)
                .setMessage(R.string.llm_reset_usage_confirm_message)
                .setPositiveButton(R.string.okay) { _, _ ->
                    // Reset all configured models' usage
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
                            for (model in modelDao.all()) {
                                LlmCostTracker.reset(model.id)
                            }
                        }
                        refreshAll()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    private fun updateUsageSummary() {
        val totalUsage = LlmCostTracker.getTotalUsage()
        if (totalUsage.totalTokens == 0L) {
            usageSummaryPref.summary = getString(R.string.llm_usage_summary_default)
        } else {
            val costStr = LlmCostTracker.formatCost(LlmCostTracker.getTotalCost())
            usageSummaryPref.summary = getString(R.string.llm_usage_summary_format,
                totalUsage.inputTokens, totalUsage.outputTokens, costStr)
        }
    }

}
