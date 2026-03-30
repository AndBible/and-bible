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
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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

    internal val settings get() = CommonUtils.aiSettings
    internal val dao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    private lateinit var gettingStartedPref: Preference
    private lateinit var providersCategory: PreferenceCategory
    private lateinit var addProviderPref: Preference
    private lateinit var modelsCategory: PreferenceCategory
    private lateinit var addModelPref: Preference
    private lateinit var behaviorCategory: PreferenceCategory
    private lateinit var manageToolPermissionsPref: Preference
    private lateinit var manageAiDocumentsPref: Preference
    private lateinit var aiLanguagePref: Preference
    private lateinit var commentaryMaxResponsePref: Preference
    private lateinit var maxIterationsPref: Preference
    private lateinit var askModelBeforeRunPref: CheckBoxPreference
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
        aiLanguagePref = preferenceScreen.findPreference("ai_language")!!
        commentaryMaxResponsePref = preferenceScreen.findPreference("commentary_max_response_chars")!!
        maxIterationsPref = preferenceScreen.findPreference("agent_max_iterations")!!
        askModelBeforeRunPref = preferenceScreen.findPreference("ask_model_before_run")!!
        usageCategory = preferenceScreen.findPreference("ai_usage_category")!!
        usageSummaryPref = preferenceScreen.findPreference("llm_usage_summary")!!
        resetUsagePref = preferenceScreen.findPreference("llm_reset_usage")!!

        setupGettingStarted()
        setupAddProvider()
        setupAddModel()
        setupToolPermissions()
        setupDocumentFilter()
        setupAiLanguage()
        setupCommentaryMaxResponse()
        setupMaxIterations()
        setupAskModelBeforeRun()
        setupUsage()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        updateToolPermissionsSummary()
        updateDocumentFilterSummary()
    }

    internal fun refreshAll() {
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

        val defaultModelId = settings.defaultModelId
        val allModels = modelDao.all().sortedByDescending { it.id == defaultModelId }
        val providerConfigs = dao.all().associateBy { it.id }

        for ((index, model) in allModels.withIndex()) {
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
            // Insert before "Add model" button, preserving sorted order
            modelsCategory.addPreference(pref)
            pref.order = addModelPref.order - allModels.size + index
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
                val textColorAttr = TypedValue()
                if (items[position].second == null) {
                    tv.setTypeface(null, Typeface.BOLD)
                    context.theme.resolveAttribute(android.R.attr.textColorSecondary, textColorAttr, true)
                    tv.setTextColor(context.getColor(textColorAttr.resourceId))
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                } else {
                    tv.setTypeface(null, Typeface.NORMAL)
                    context.theme.resolveAttribute(android.R.attr.textColorPrimary, textColorAttr, true)
                    tv.setTextColor(context.getColor(textColorAttr.resourceId))
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

    internal val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    // Model dialogs: showAddModelDialog, showEditModelDialog, confirmDeleteModel → ModelDialogs.kt

    /** Create a ScrollView containing a padded vertical LinearLayout for dialog content. */
    internal fun createDialogLayout(): Pair<ScrollView, LinearLayout> {
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

    internal fun addLabeledField(layout: LinearLayout, label: String, field: View) {
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

    // Easy Setup wizard: showEasySetupStep1/2/3, performEasySetup → EasySetupDialogs.kt

    private fun setupGettingStarted() {
        gettingStartedPref.setOnPreferenceClickListener {
            showEasySetupStep1()
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

    /** Sentinel value used to identify the "Custom…" entry in the language picker. */
    private val CUSTOM_LANGUAGE_TAG = "\u0000custom"

    private fun setupAiLanguage() {
        updateAiLanguageSummary()
        aiLanguagePref.setOnPreferenceClickListener {
            val currentTag = settings.aiLanguage
            val descriptions = resources.getStringArray(R.array.prefs_interface_locale_descriptions)
            val codes = resources.getStringArray(R.array.prefs_interface_locale_values)
            val languages = mutableListOf<Pair<String?, String>>()
            languages.add(null to getString(R.string.ai_language_app_default, Locale.getDefault().displayLanguage))
            for (i in codes.indices) {
                val code = codes[i]
                if (code.isNotEmpty()) {
                    languages.add(code to descriptions[i])
                }
            }
            languages.add(CUSTOM_LANGUAGE_TAG to getString(R.string.ai_language_custom))

            val items = languages.map { it.second }.toTypedArray()
            val checkedIndex = languages.indexOfFirst { it.first == currentTag }.let {
                if (it >= 0) it
                else if (currentTag != null) languages.size - 1  // custom value → highlight "Custom…"
                else 0
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.ai_language_title)
                .setSingleChoiceItems(items, checkedIndex) { dialog, which ->
                    if (languages[which].first == CUSTOM_LANGUAGE_TAG) {
                        dialog.dismiss()
                        showCustomLanguageDialog()
                    } else {
                        settings.aiLanguage = languages[which].first
                        updateAiLanguageSummary()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    private fun showCustomLanguageDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.ai_language_custom_example)
            settings.aiLanguage?.let { setText(it) }
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(TextView(ctx).apply {
                text = getString(R.string.ai_language_custom_hint)
                setPadding(0, 0, 0, pad / 2)
            })
            addView(input)
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.ai_language_title)
            .setView(container)
            .setPositiveButton(R.string.okay) { _, _ ->
                val tag = input.text.toString().trim()
                settings.aiLanguage = tag.ifEmpty { null }
                updateAiLanguageSummary()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateAiLanguageSummary() {
        val tag = settings.aiLanguage
        aiLanguagePref.summary = if (tag == null) {
            getString(R.string.ai_language_app_default, Locale.getDefault().displayLanguage)
        } else {
            val locale = Locale.forLanguageTag(tag)
            val displayName = locale.getDisplayLanguage(locale)
            if (displayName.isNotEmpty() && displayName != tag) "$displayName ($tag)" else tag
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

    private fun setupAskModelBeforeRun() {
        askModelBeforeRunPref.isChecked = settings.askModelBeforeRun
        askModelBeforeRunPref.setOnPreferenceChangeListener { _, newValue ->
            settings.askModelBeforeRun = newValue as Boolean
            true
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
