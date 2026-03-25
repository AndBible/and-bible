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
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.ModelPricing
import net.bible.service.llm.OpenRouterModelService
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
        setupToolPermissions()
        setupDocumentFilter()
        setupCommentaryMaxResponse()
        setupMaxIterations()
        setupUsage()
        refreshProviderList()
        updateVisibility()
    }

    override fun onResume() {
        super.onResume()
        refreshProviderList()
        updateToolPermissionsSummary()
        updateDocumentFilterSummary()
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
        behaviorCategory.isVisible = hasProviders
        usageCategory.isVisible = hasProviders

        if (hasProviders) updateUsageSummary()
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
                val model = config.resolveDefaultModel()
                summary = buildString {
                    if (config.isDefault) {
                        append(getString(R.string.ai_provider_default_indicator))
                        append("\n")
                    }
                    if (apiKey.isNotBlank()) {
                        val suffix = apiKey.takeLast(4)
                        append(getString(R.string.ai_provider_api_key_masked, getString(R.string.ai_provider_api_key), suffix))
                    } else {
                        append(getString(R.string.ai_provider_api_key_not_set))
                    }
                    if (model.isNotBlank()) {
                        append("\n")
                        append(getString(R.string.ai_provider_model, model))
                    }
                    // Show per-provider cost
                    val cost = LlmCostTracker.getCumulativeCost(config.id)
                    if (cost > 0) {
                        append("\n")
                        append(LlmCostTracker.formatCost(cost))
                    }
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
                        LlmCostTracker.reset(config.id)
                        dao.delete(config)
                        // If we deleted the default, promote the first remaining config
                        if (config.isDefault) {
                            val remaining = dao.all()
                            if (remaining.isNotEmpty()) {
                                dao.update(remaining.first().copy(isDefault = true))
                            }
                        }
                    }
                    Toast.makeText(requireContext(), R.string.ai_provider_deleted, Toast.LENGTH_SHORT).show()
                    refreshProviderList()
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
        val modelSpinner: Spinner,
        val customModelInput: EditText,
        val customInputPriceInput: EditText,
        val customOutputPriceInput: EditText,
        val defaultCheckBox: CheckBox?,
        val models: MutableList<String>,
        // OpenRouter-specific fields
        val isOpenRouter: Boolean = false,
        val openRouterCategorySpinner: Spinner? = null,
        val openRouterModelSpinner: Spinner? = null,
        val openRouterModelIds: MutableList<String> = mutableListOf(),
        val openRouterModels: MutableList<OpenRouterModelService.OpenRouterModel> = mutableListOf(),
    ) {
        /** Get the selected model name, handling both normal and OpenRouter modes. */
        fun getSelectedModel(): String? {
            if (isOpenRouter && openRouterModelSpinner != null) {
                val pos = openRouterModelSpinner.selectedItemPosition
                return if (pos >= 0 && pos < openRouterModelIds.size) {
                    openRouterModelIds[pos]
                } else null
            }
            val pos = modelSpinner.selectedItemPosition
            return if (pos == models.size - 1) {
                customModelInput.text.toString().trim().takeIf { it.isNotEmpty() }
            } else if (pos >= 0 && pos < models.size - 1) {
                models[pos]
            } else null
        }
    }

    /**
     * Show a dialog to create or edit a provider config.
     * If config is null, creates a new one of the given providerType.
     */
    private fun showEditProviderDialog(config: LlmProviderConfig?, providerType: LlmProvider? = null) {
        val isNew = config == null
        val provider = providerType ?: config!!.resolveProvider()
        val isCustom = provider == LlmProvider.CUSTOM

        val context = requireContext()
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        scrollView.addView(layout)

        val fields = buildProviderDialogLayout(layout, config, provider, isCustom)
        setupModelPricingListeners(fields)

        // For OpenRouter, check if model list needs refreshing
        if (provider == LlmProvider.OPENROUTER) {
            maybeRefreshOpenRouterModels(fields)
        }

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
            override fun afterTextChanged(s: android.text.Editable?) {
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
        val isNew = config == null
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

        val isOpenRouter = provider == LlmProvider.OPENROUTER
        val models = provider.models.toMutableList()
        val currentModel = config?.defaultModel ?: ""
        if (currentModel.isNotBlank() && currentModel !in models) {
            models.add(0, currentModel)
        }
        if (!isOpenRouter) {
            models.add(getString(R.string.llm_custom_model))
        }

        // OpenRouter-specific UI: category spinner + model spinner + fetch button
        var openRouterCategorySpinner: Spinner? = null
        var openRouterModelSpinner: Spinner? = null
        val openRouterModelIds = mutableListOf<String>()
        val openRouterModels = mutableListOf<OpenRouterModelService.OpenRouterModel>()

        // Sort models by price (input+output), keep "Custom…" last
        val customLabel = if (!isOpenRouter) getString(R.string.llm_custom_model) else null
        val sortedModels = models.filter { it != customLabel }.sortedBy { modelId ->
            val pricing = LlmProvider.findPricing(modelId)
            pricing?.let { it.inputPerMillion + it.outputPerMillion } ?: Double.MAX_VALUE
        }.toMutableList()
        if (customLabel != null) sortedModels.add(customLabel)
        models.clear()
        models.addAll(sortedModels)

        // Display labels with pricing for standard providers
        val modelDisplayLabels = models.map { modelId ->
            val pricing = LlmProvider.findPricing(modelId)
            if (pricing != null) formatModelWithPricing(modelId, pricing) else modelId
        }
        val modelSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, modelDisplayLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val idx = if (currentModel.isNotBlank()) models.indexOf(currentModel).coerceAtLeast(0) else 0
            setSelection(idx)
        }

        val customModelInput: EditText

        if (isOpenRouter) {
            // Hide normal model spinner for OpenRouter
            modelSpinner.visibility = View.GONE

            // Load cached models if available
            val cached = OpenRouterModelService.getCachedModels()
            if (cached != null) {
                openRouterModels.addAll(cached)
            }

            // Category spinner
            openRouterCategorySpinner = Spinner(context)
            updateOpenRouterCategorySpinner(openRouterCategorySpinner, openRouterModels)
            addLabeledField(layout, getString(R.string.llm_openrouter_category), openRouterCategorySpinner)

            // Model spinner (filtered by category)
            val orModelSpinner = Spinner(context)
            val orModelIds = mutableListOf<String>()
            updateOpenRouterModelSpinner(orModelSpinner, orModelIds, openRouterModels, null, currentModel)
            addLabeledField(layout, getString(R.string.llm_openrouter_model), orModelSpinner)
            openRouterModelSpinner = orModelSpinner

            // Hidden customModelInput (unused for OpenRouter but needed by ProviderDialogFields)
            customModelInput = EditText(context).apply { visibility = View.GONE }
        } else {
            addLabeledField(layout, getString(R.string.ai_provider_default_model), modelSpinner)

            customModelInput = EditText(context).apply {
                hint = getString(R.string.llm_custom_model_dialog_message)
                inputType = InputType.TYPE_CLASS_TEXT
                visibility = View.GONE
            }
            layout.addView(customModelInput)
        }

        val customPricingLabel = TextView(context).apply {
            text = getString(R.string.llm_custom_pricing_label)
            setTextAppearance(android.R.style.TextAppearance_Small)
            setTypeface(typeface, Typeface.BOLD)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (12 * resources.displayMetrics.density).toInt()
            layoutParams = params
            visibility = View.GONE
        }
        layout.addView(customPricingLabel)

        val existingCustomPricing = config?.let { LlmPricing.getCustomPricing(it.id) }
        val customInputPriceInput = EditText(context).apply {
            hint = getString(R.string.llm_custom_input_price_title)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            existingCustomPricing?.let { setText(it.inputPerMillion.toString()) }
            visibility = View.GONE
        }
        layout.addView(customInputPriceInput)

        val customOutputPriceInput = EditText(context).apply {
            hint = getString(R.string.llm_custom_output_price_title)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            existingCustomPricing?.let { setText(it.outputPerMillion.toString()) }
            visibility = View.GONE
        }
        layout.addView(customOutputPriceInput)

        val defaultCheckBox = if (!isNew && !config.isDefault) {
            CheckBox(context).apply {
                text = getString(R.string.ai_provider_set_default)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = (12 * resources.displayMetrics.density).toInt()
                layoutParams = params
            }.also { layout.addView(it) }
        } else null

        return ProviderDialogFields(
            nameInput = nameInput,
            apiKeyInput = apiKeyInput,
            endpointInput = endpointInput,
            apiFormatSpinner = apiFormatSpinner,
            modelSpinner = modelSpinner,
            customModelInput = customModelInput,
            customInputPriceInput = customInputPriceInput,
            customOutputPriceInput = customOutputPriceInput,
            defaultCheckBox = defaultCheckBox,
            models = models,
            isOpenRouter = isOpenRouter,
            openRouterCategorySpinner = openRouterCategorySpinner,
            openRouterModelSpinner = openRouterModelSpinner,
            openRouterModelIds = openRouterModelIds,
            openRouterModels = openRouterModels,
        )
    }

    private fun setupModelPricingListeners(fields: ProviderDialogFields) {
        fun updateCustomPricingVisibility() {
            val modelName = if (fields.isOpenRouter) {
                val orSpinner = fields.openRouterModelSpinner
                val pos = orSpinner?.selectedItemPosition ?: -1
                if (pos >= 0 && pos < fields.openRouterModelIds.size) fields.openRouterModelIds[pos] else ""
            } else {
                val pos = fields.modelSpinner.selectedItemPosition
                if (pos == fields.models.size - 1) {
                    fields.customModelInput.text.toString().trim()
                } else if (pos >= 0 && pos < fields.models.size - 1) {
                    fields.models[pos]
                } else ""
            }
            val needsCustomPricing = modelName.isNotBlank() && !LlmPricing.isKnownModel(modelName)
            val vis = if (needsCustomPricing) View.VISIBLE else View.GONE
            // Update all pricing-related views (label is the sibling before customInputPriceInput)
            fields.customInputPriceInput.visibility = vis
            fields.customOutputPriceInput.visibility = vis
            val parent = fields.customInputPriceInput.parent as? LinearLayout
            if (parent != null) {
                val idx = parent.indexOfChild(fields.customInputPriceInput)
                if (idx > 0) parent.getChildAt(idx - 1).visibility = vis
            }
        }

        if (fields.isOpenRouter) {
            setupOpenRouterListeners(fields, ::updateCustomPricingVisibility)
        } else {
            fields.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    fields.customModelInput.visibility = if (position == fields.models.size - 1) View.VISIBLE else View.GONE
                    updateCustomPricingVisibility()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            fields.customModelInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { updateCustomPricingVisibility() }
            })
        }

        updateCustomPricingVisibility()
    }

    private fun setupOpenRouterListeners(fields: ProviderDialogFields, updatePricing: () -> Unit) {
        val categorySpinner = fields.openRouterCategorySpinner ?: return
        val orModelSpinner = fields.openRouterModelSpinner ?: return

        // Category spinner filters the model spinner
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = if (position == 0) null else categorySpinner.selectedItem?.toString()?.lowercase()
                val currentModel = fields.getSelectedModel() ?: ""
                updateOpenRouterModelSpinner(orModelSpinner, fields.openRouterModelIds, fields.openRouterModels, selectedCategory, currentModel)
                updatePricing()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Model spinner selection changes update pricing visibility
        orModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePricing()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private companion object {
        private const val PREF_OPENROUTER_AUTO_FETCH = "openrouter_auto_fetch_models"
    }

    /**
     * Check if OpenRouter model list needs fetching (no cache or >7 days old)
     * and prompt the user or fetch automatically based on preferences.
     */
    private fun maybeRefreshOpenRouterModels(fields: ProviderDialogFields) {
        val cacheAge = System.currentTimeMillis() - OpenRouterModelService.cacheTimestamp
        val needsRefresh = OpenRouterModelService.cacheTimestamp == 0L || cacheAge > 7 * 24 * 60 * 60 * 1000L

        if (!needsRefresh) return

        val autoFetch = CommonUtils.settings.getBoolean(PREF_OPENROUTER_AUTO_FETCH, false)
        if (autoFetch) {
            doFetchOpenRouterModels(fields)
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.llm_fetch_models_title)
                .setMessage(R.string.llm_fetch_models_message)
                .setPositiveButton(R.string.okay) { _, _ ->
                    doFetchOpenRouterModels(fields)
                }
                .setNeutralButton(R.string.llm_fetch_models_dont_ask) { _, _ ->
                    CommonUtils.settings.setBoolean(PREF_OPENROUTER_AUTO_FETCH, true)
                    doFetchOpenRouterModels(fields)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun doFetchOpenRouterModels(fields: ProviderDialogFields) {
        val currentModel = fields.getSelectedModel() ?: ""

        lifecycleScope.launch {
            val models = OpenRouterModelService.fetchModels()
            if (models != null) {
                fields.openRouterModels.clear()
                fields.openRouterModels.addAll(models)

                updateOpenRouterCategorySpinner(fields.openRouterCategorySpinner!!, models)
                updateOpenRouterModelSpinner(
                    fields.openRouterModelSpinner!!, fields.openRouterModelIds,
                    models, null, currentModel
                )

                Toast.makeText(requireContext(),
                    getString(R.string.llm_fetch_models_success, models.size),
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.llm_fetch_models_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOpenRouterCategorySpinner(spinner: Spinner, models: List<OpenRouterModelService.OpenRouterModel>) {
        val categories = mutableListOf(getString(R.string.llm_openrouter_category_all))
        categories.addAll(
            models.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                .map { it.replaceFirstChar { c -> c.uppercaseChar() } }
        )
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun updateOpenRouterModelSpinner(
        spinner: Spinner,
        modelIds: MutableList<String>,
        models: List<OpenRouterModelService.OpenRouterModel>,
        category: String?,
        currentModel: String
    ) {
        val filtered = if (category == null) models else models.filter { it.category == category }
        // Sort by price (input+output), cheapest first
        val sorted = filtered.sortedBy { model ->
            model.pricing?.let { it.inputPerMillion + it.outputPerMillion } ?: Double.MAX_VALUE
        }
        modelIds.clear()
        modelIds.addAll(sorted.map { it.id })
        // If no models fetched yet, ensure current model is in the list
        if (modelIds.isEmpty() && currentModel.isNotBlank()) {
            modelIds.add(currentModel)
        }
        // Display labels with pricing info
        val displayLabels = modelIds.map { id ->
            val pricing = filtered.find { it.id == id }?.pricing
                ?: OpenRouterModelService.getPricingForModel(id)
            formatModelWithPricing(id, pricing)
        }
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // Restore selection
        val idx = if (currentModel.isNotBlank()) modelIds.indexOf(currentModel).coerceAtLeast(0) else 0
        if (modelIds.isNotEmpty()) spinner.setSelection(idx)
    }

    /** Format a model name with pricing info for display in spinner, e.g. "claude-sonnet-4 ($3.00/$15.00)". */
    private fun formatModelWithPricing(modelId: String, pricing: ModelPricing?): String {
        if (pricing == null) return modelId
        val input = formatPriceCompact(pricing.inputPerMillion)
        val output = formatPriceCompact(pricing.outputPerMillion)
        return "$modelId ($input/$output)"
    }

    /** Format a per-million-token price compactly, e.g. "$3.00", "$0.15". */
    private fun formatPriceCompact(pricePerMillion: Double): String {
        return if (pricePerMillion < 0.01 && pricePerMillion > 0) {
            "< \$0.01"
        } else {
            "\$%.2f".format(pricePerMillion)
        }
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
        val makeDefault = fields.defaultCheckBox?.isChecked == true

        val selectedModel = fields.getSelectedModel()

        val customInputPrice = fields.customInputPriceInput.text.toString().toDoubleOrNull() ?: 0.0
        val customOutputPrice = fields.customOutputPriceInput.text.toString().toDoubleOrNull() ?: 0.0

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (isNew) {
                    val newConfig = LlmProviderConfig(
                        providerType = provider.name,
                        displayName = displayName,
                        endpoint = if (isCustom) endpoint else null,
                        apiFormat = if (isCustom) apiFormat else null,
                        defaultModel = selectedModel,
                        isDefault = dao.getCount() == 0,
                        orderNumber = dao.getCount(),
                        customInputPrice = customInputPrice,
                        customOutputPrice = customOutputPrice,
                    )
                    dao.insert(newConfig)
                    newConfig.setApiKey(apiKey)
                } else {
                    if (makeDefault) {
                        dao.clearDefault()
                        val all = dao.all()
                        var order = 1
                        for (c in all) {
                            if (c.id == config.id) continue
                            if (c.orderNumber != order) dao.update(c.copy(orderNumber = order))
                            order++
                        }
                    }
                    val updated = config.copy(
                        displayName = displayName,
                        endpoint = if (isCustom) endpoint else config.endpoint,
                        apiFormat = if (isCustom) apiFormat else config.apiFormat,
                        defaultModel = selectedModel,
                        isDefault = config.isDefault || makeDefault,
                        orderNumber = if (makeDefault) 0 else config.orderNumber,
                    )
                    dao.update(updated)
                    updated.setApiKey(apiKey)
                    LlmPricing.setCustomPricing(customInputPrice, customOutputPrice, config.id)
                }
            }
            Toast.makeText(requireContext(), R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
            refreshProviderList()
        }
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

    private fun setupGettingStarted() {
        gettingStartedPref.setOnPreferenceClickListener {
            val getKey = getString(R.string.ai_getting_started_get_api_key)
            val html = buildString {
                append(getString(R.string.ai_getting_started_intro))

                val tierHeaders = mapOf(
                    ProviderTier.RECOMMENDED to getString(R.string.ai_getting_started_recommended_providers),
                    ProviderTier.COMMUNITY to getString(R.string.ai_getting_started_community_providers),
                )
                for (tier in listOf(ProviderTier.RECOMMENDED, ProviderTier.COMMUNITY, ProviderTier.UNCATEGORIZED)) {
                    val providers = LlmProvider.entries.filter { it.tier == tier && it.apiKeyUrl != null }
                    if (providers.isEmpty()) continue
                    tierHeaders[tier]?.let { append("<br><br><b>$it</b><br><br>") } ?: append("<br>")
                    for (p in providers) {
                        append("<b>${p.displayName}</b><br>")
                        append("<a href=\"${p.apiKeyUrl}\">$getKey</a><br><br>")
                    }
                }
                append(getString(R.string.ai_getting_started_other))
            }
            val spanned = htmlToSpan(html)
            val d = AlertDialog.Builder(requireContext()).apply {
                setTitle(R.string.ai_getting_started_title)
                setMessage(spanned)
                setPositiveButton(R.string.okay, null)
                setCancelable(true)
            }.create()
            d.show()
            d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
            true
        }
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
                    // Reset all provider configs' usage
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            for (config in dao.all()) {
                                LlmCostTracker.reset(config.id)
                            }
                        }
                        updateUsageSummary()
                        refreshProviderList()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    private fun updateUsageSummary() {
        // Sum across all provider configs
        var totalInput = 0L
        var totalOutput = 0L
        var totalCost = 0.0

        for (config in dao.all()) {
            val usage = LlmCostTracker.getCumulativeUsage(config.id)
            totalInput += usage.inputTokens
            totalOutput += usage.outputTokens
            totalCost += LlmCostTracker.getCumulativeCost(config.id)
        }

        if (totalInput == 0L && totalOutput == 0L) {
            usageSummaryPref.summary = getString(R.string.llm_usage_summary_default)
        } else {
            val costStr = LlmCostTracker.formatCost(totalCost)
            usageSummaryPref.summary = getString(R.string.llm_usage_summary_format, totalInput, totalOutput, costStr)
        }
    }

}
