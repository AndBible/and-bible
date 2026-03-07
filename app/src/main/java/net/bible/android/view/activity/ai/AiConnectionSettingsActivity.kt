/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
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
import net.bible.service.llm.getApiKey
import net.bible.service.llm.removeApiKey
import net.bible.service.llm.setApiKey
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry

class AiConnectionSettingsActivity : ActivityBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
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

    private val settings get() = CommonUtils.settings
    private val dao get() = DatabaseContainer.instance.llmProcessingDb.llmProviderConfigDao()

    private lateinit var gettingStartedPref: Preference
    private lateinit var providersCategory: PreferenceCategory
    private lateinit var addProviderPref: Preference
    private lateinit var behaviorCategory: PreferenceCategory
    private lateinit var llmModeCategory: PreferenceCategory
    private lateinit var manageToolPermissionsPref: Preference
    private lateinit var usageCategory: PreferenceCategory
    private lateinit var usageSummaryPref: Preference
    private lateinit var resetUsagePref: Preference
    private lateinit var manageCachePref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.ai_connection_settings, rootKey)

        gettingStartedPref = preferenceScreen.findPreference("ai_getting_started")!!
        providersCategory = preferenceScreen.findPreference("ai_providers_category")!!
        addProviderPref = preferenceScreen.findPreference("ai_add_provider")!!
        behaviorCategory = preferenceScreen.findPreference("ai_behavior_category")!!
        llmModeCategory = preferenceScreen.findPreference("ai_llm_mode_category")!!
        manageToolPermissionsPref = preferenceScreen.findPreference("manage_tool_permissions")!!
        usageCategory = preferenceScreen.findPreference("ai_usage_category")!!
        usageSummaryPref = preferenceScreen.findPreference("llm_usage_summary")!!
        resetUsagePref = preferenceScreen.findPreference("llm_reset_usage")!!
        manageCachePref = preferenceScreen.findPreference("llm_manage_cache")!!

        setupGettingStarted()
        setupAddProvider()
        setupToolPermissions()
        setupUsage()
        setupCacheManagement()
        refreshProviderList()
        updateVisibility()
    }

    override fun onResume() {
        super.onResume()
        if (::manageCachePref.isInitialized) {
            updateCacheSummary()
        }
        refreshProviderList()
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
        llmModeCategory.isVisible = hasProviders && settings.llmModeExperimentalEnabled
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
                        append(getString(R.string.ai_provider_api_key_masked, suffix))
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
        // All providers. For non-CUSTOM: only show if not already added.
        val availableProviders = LlmProvider.entries.filter {
            it == LlmProvider.CUSTOM || it.name !in existingTypes
        }

        val names = availableProviders.map {
            if (it == LlmProvider.CUSTOM) getString(R.string.llm_provider_custom) else it.displayName
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_provider_select_type)
            .setItems(names) { _, which ->
                val provider = availableProviders[which]
                showEditProviderDialog(null, provider)
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
    )

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
                        confirmDeleteProvider(config!!)
                    }
                }
            }
            .show()

        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.isEnabled = fields.apiKeyInput.text.toString().trim().isNotBlank()
        fields.apiKeyInput.addTextChangedListener(object : android.text.TextWatcher {
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
                val currentFormat = config?.apiFormat ?: "OPENAI"
                val idx = formats.indexOf(currentFormat)
                if (idx >= 0) setSelection(idx)
            }.also { addLabeledField(layout, getString(R.string.ai_provider_api_format), it) }
        } else null

        val models = provider.models.toMutableList()
        val currentModel = config?.defaultModel ?: ""
        if (currentModel.isNotBlank() && currentModel !in models) {
            models.add(0, currentModel)
        }
        models.add(getString(R.string.llm_custom_model))

        val modelSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, models).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val idx = if (currentModel.isNotBlank()) models.indexOf(currentModel).coerceAtLeast(0) else 0
            setSelection(idx)
        }
        addLabeledField(layout, getString(R.string.ai_provider_default_model), modelSpinner)

        val customModelInput = EditText(context).apply {
            hint = getString(R.string.llm_custom_model_dialog_message)
            inputType = InputType.TYPE_CLASS_TEXT
            visibility = View.GONE
        }
        layout.addView(customModelInput)

        val customPricingLabel = TextView(context).apply {
            text = getString(R.string.llm_custom_pricing_label)
            setTextAppearance(android.R.style.TextAppearance_Small)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
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

        val defaultCheckBox = if (!isNew && !config!!.isDefault) {
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
        )
    }

    private fun setupModelPricingListeners(fields: ProviderDialogFields) {
        fun updateCustomPricingVisibility() {
            val pos = fields.modelSpinner.selectedItemPosition
            val modelName = if (pos == fields.models.size - 1) {
                fields.customModelInput.text.toString().trim()
            } else if (pos >= 0 && pos < fields.models.size - 1) {
                fields.models[pos]
            } else ""
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

        fields.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fields.customModelInput.visibility = if (position == fields.models.size - 1) View.VISIBLE else View.GONE
                updateCustomPricingVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        fields.customModelInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateCustomPricingVisibility() }
        })

        updateCustomPricingVisibility()
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
        val apiFormat = fields.apiFormatSpinner?.selectedItem?.toString()
        val makeDefault = fields.defaultCheckBox?.isChecked == true

        val selectedModelPosition = fields.modelSpinner.selectedItemPosition
        val selectedModel = if (selectedModelPosition == fields.models.size - 1) {
            fields.customModelInput.text.toString().trim().takeIf { it.isNotEmpty() }
        } else if (selectedModelPosition >= 0 && selectedModelPosition < fields.models.size - 1) {
            fields.models[selectedModelPosition]
        } else null

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
                    )
                    dao.insert(newConfig)
                    newConfig.setApiKey(apiKey)
                    LlmPricing.setCustomPricing(customInputPrice, customOutputPrice, newConfig.id)
                } else {
                    if (makeDefault) {
                        dao.clearDefault()
                        val all = dao.all()
                        var order = 1
                        for (c in all) {
                            if (c.id == config!!.id) continue
                            if (c.orderNumber != order) dao.update(c.copy(orderNumber = order))
                            order++
                        }
                    }
                    val updated = config!!.copy(
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
                append("<br><br><b>${getString(R.string.ai_getting_started_providers)}</b><br><br>")
                append("<b>${getString(R.string.ai_getting_started_gemini)}</b><br>")
                append("<a href=\"https://aistudio.google.com/apikey\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_openai)}</b><br>")
                append("<a href=\"https://platform.openai.com/api-keys\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_anthropic)}</b><br>")
                append("<a href=\"https://console.anthropic.com/settings/keys\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_xai)}</b><br>")
                append("<a href=\"https://console.x.ai/\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_mistral)}</b><br>")
                append("<a href=\"https://console.mistral.ai/api-keys\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_deepseek)}</b><br>")
                append("<a href=\"https://platform.deepseek.com/api_keys\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_groq)}</b><br>")
                append("<a href=\"https://console.groq.com/keys\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_alibaba)}</b><br>")
                append("<a href=\"https://bailian.console.alibabacloud.com/?apiKey=1#/api-key\">$getKey</a><br><br>")
                append("<b>${getString(R.string.ai_getting_started_openrouter)}</b><br>")
                append("<a href=\"https://openrouter.ai/keys\">$getKey</a><br><br>")
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
            showToolPermissionsDialog()
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

    private fun showToolPermissionsDialog() {
        val tools = ToolRegistry.getPermissionTools()
        if (tools.isEmpty()) return

        val allowed = settings.permanentlyAllowedTools
        val denied = settings.permanentlyDeniedTools

        val items = tools.map { tool ->
            val displayName = ToolRegistry.getDisplayName(tool)
            val status = when (tool.name) {
                in allowed -> getString(R.string.permission_status_allowed)
                in denied -> getString(R.string.permission_status_denied)
                else -> getString(R.string.permission_status_default)
            }
            "$displayName — $status"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.manage_tool_permissions_title)
            .setItems(items) { _, which ->
                showToolPermissionOptionDialog(tools[which])
            }
            .setNeutralButton(R.string.reset_all_permissions) { _, _ ->
                settings.permanentlyAllowedTools = emptySet()
                settings.permanentlyDeniedTools = emptySet()
                updateToolPermissionsSummary()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showToolPermissionOptionDialog(tool: Tool) {
        val displayName = ToolRegistry.getDisplayName(tool)
        val options = arrayOf(
            getString(R.string.permission_option_default),
            getString(R.string.permission_option_always_allow),
            getString(R.string.permission_option_always_deny)
        )

        val currentIndex = when (tool.name) {
            in settings.permanentlyAllowedTools -> 1
            in settings.permanentlyDeniedTools -> 2
            else -> 0
        }

        AlertDialog.Builder(requireContext())
            .setTitle(displayName)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val toolName = tool.name
                when (which) {
                    0 -> {
                        settings.permanentlyAllowedTools -= toolName
                        settings.permanentlyDeniedTools -= toolName
                    }
                    1 -> {
                        settings.permanentlyAllowedTools += toolName
                        settings.permanentlyDeniedTools -= toolName
                    }
                    2 -> {
                        settings.permanentlyDeniedTools += toolName
                        settings.permanentlyAllowedTools -= toolName
                    }
                }
                updateToolPermissionsSummary()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                        LlmCostTracker.reset() // Also reset legacy
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

        // Also add legacy usage
        val legacyUsage = LlmCostTracker.getCumulativeUsage()
        totalInput += legacyUsage.inputTokens
        totalOutput += legacyUsage.outputTokens
        totalCost += LlmCostTracker.getCumulativeCost()

        if (totalInput == 0L && totalOutput == 0L) {
            usageSummaryPref.summary = getString(R.string.llm_usage_summary_default)
        } else {
            val costStr = LlmCostTracker.formatCost(totalCost)
            usageSummaryPref.summary = getString(R.string.llm_usage_summary_format, totalInput, totalOutput, costStr)
        }
    }

    private fun setupCacheManagement() {
        manageCachePref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), LlmCacheActivity::class.java))
            true
        }
        updateCacheSummary()
    }

    private fun updateCacheSummary() {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                DatabaseContainer.instance.llmProcessingDb.llmProcessingDao().getCacheStats()
            }
            if (stats.entryCount > 0) {
                val sizeStr = formatSize(stats.totalSize)
                manageCachePref.summary = "${getString(R.string.llm_cache_management_summary)} (${stats.entryCount}, $sizeStr)"
            } else {
                manageCachePref.summary = getString(R.string.llm_cache_management_summary)
            }
        }
    }

    private fun formatSize(bytes: Long): String =
        Formatter.formatShortFileSize(requireContext(), bytes)
}
