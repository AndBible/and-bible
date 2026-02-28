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
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.settings.PreferenceStore
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.llm.LlmProvider
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

    private lateinit var gettingStartedPref: Preference
    private lateinit var providerPref: ListPreference
    private lateinit var apiKeyPref: EditTextPreference
    private lateinit var endpointPref: EditTextPreference
    private lateinit var modelCategory: PreferenceCategory
    private lateinit var modelPref: ListPreference
    private lateinit var behaviorCategory: PreferenceCategory
    private lateinit var manageToolPermissionsPref: Preference

    companion object {
        private const val CUSTOM_MODEL_SENTINEL = "__custom__"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.ai_connection_settings, rootKey)

        gettingStartedPref = preferenceScreen.findPreference("ai_getting_started")!!
        providerPref = preferenceScreen.findPreference("llm_provider")!!
        apiKeyPref = preferenceScreen.findPreference("llm_api_key")!!
        endpointPref = preferenceScreen.findPreference("llm_endpoint")!!
        modelCategory = preferenceScreen.findPreference("ai_model_category")!!
        modelPref = preferenceScreen.findPreference("llm_model")!!
        behaviorCategory = preferenceScreen.findPreference("ai_behavior_category")!!
        manageToolPermissionsPref = preferenceScreen.findPreference("manage_tool_permissions")!!

        // Migrate: if provider is empty but endpoint is set, detect provider from endpoint
        if (settings.llmProvider.isBlank() && settings.llmEndpoint.isNotBlank()) {
            val detected = LlmProvider.fromEndpoint(settings.llmEndpoint)
            settings.llmProvider = detected.name
        }

        setupGettingStarted()
        setupProvider()
        setupApiKey()
        setupModel()
        setupToolPermissions()
        updateVisibility()
    }

    private fun currentProvider(): LlmProvider? {
        val name = settings.llmProvider
        if (name.isBlank()) return null
        return try {
            LlmProvider.valueOf(name)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun hasApiKey(): Boolean = settings.llmApiKey.isNotBlank()

    /**
     * Progressive disclosure:
     * - Provider is always visible
     * - Getting started is visible only until provider is selected
     * - API key is visible once provider is selected
     * - Endpoint is visible only for Custom provider + after API key
     * - Everything else is visible once API key is entered
     */
    private fun updateVisibility() {
        val provider = currentProvider()
        val providerSelected = provider != null
        val apiKeyEntered = hasApiKey()
        val configured = providerSelected && apiKeyEntered

        apiKeyPref.isVisible = providerSelected
        endpointPref.isVisible = configured && provider == LlmProvider.CUSTOM

        // Update API key summary with detected provider
        if (apiKeyEntered) {
            val detected = LlmProvider.fromApiKey(settings.llmApiKey)
            val base = getString(R.string.llm_api_key_summary)
            apiKeyPref.summary = if (detected != null) {
                "$base\n\n${getString(R.string.llm_api_key_detected_provider, detected.displayName)}"
            } else {
                base
            }
        } else {
            apiKeyPref.summary = getString(R.string.llm_api_key_summary)
        }
        modelCategory.isVisible = configured
        behaviorCategory.isVisible = configured

        // Update provider summary
        if (provider != null) {
            val base = getString(R.string.llm_provider_summary)
            val current = getString(R.string.llm_provider_current, provider.displayName)
            val endpoint = settings.llmEndpoint
            providerPref.summary = "$base\n\n$current\n$endpoint"
        } else {
            providerPref.summary = getString(R.string.llm_provider_summary)
        }

        if (providerSelected) {
            updateModelList(provider!!)
        }
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

    private fun setupProvider() {
        val providers = LlmProvider.entries.toTypedArray()
        providerPref.entries = providers.map { it.displayName }.toTypedArray()
        providerPref.entryValues = providers.map { it.name }.toTypedArray()

        // Set current value if exists
        val current = currentProvider()
        if (current != null) {
            providerPref.value = current.name
        }

        providerPref.setOnPreferenceChangeListener { _, newValue ->
            val providerName = newValue as String
            val provider = try {
                LlmProvider.valueOf(providerName)
            } catch (_: IllegalArgumentException) {
                LlmProvider.CUSTOM
            }

            settings.llmProvider = provider.name

            // Update endpoint for non-custom providers
            if (provider != LlmProvider.CUSTOM) {
                settings.llmEndpoint = provider.endpoint
                endpointPref.text = provider.endpoint
            }

            // Reset model to default (empty = use provider's default)
            settings.llmModel = ""

            updateVisibility()
            true
        }
    }

    private fun setupApiKey() {
        apiKeyPref.setOnPreferenceChangeListener { _, newValue ->
            val apiKey = newValue as? String ?: ""
            // Save immediately so updateVisibility sees the new value
            settings.llmApiKey = apiKey

            // Auto-detect provider from key prefix if it doesn't match current
            val detected = LlmProvider.fromApiKey(apiKey)
            if (detected != null && detected != currentProvider()) {
                settings.llmProvider = detected.name
                providerPref.value = detected.name
                settings.llmEndpoint = detected.endpoint
                endpointPref.text = detected.endpoint
                settings.llmModel = ""
            }

            updateVisibility()
            true
        }
    }

    private fun setupModel() {
        modelPref.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as String
            if (value == CUSTOM_MODEL_SENTINEL) {
                showCustomModelDialog()
                false // don't save the sentinel
            } else {
                modelPref.summary = value
                true
            }
        }
    }

    private fun updateModelList(provider: LlmProvider) {
        val modelEntries = mutableListOf<String>()
        val modelValues = mutableListOf<String>()

        for (model in provider.models) {
            modelEntries.add(model)
            modelValues.add(model)
        }

        // If current model is not in the provider's list, add it (backward compat)
        val currentModel = settings.llmModel
        if (currentModel.isNotBlank() && currentModel !in modelValues) {
            modelEntries.add(0, currentModel)
            modelValues.add(0, currentModel)
        }

        // Add "Custom..." option
        modelEntries.add(getString(R.string.llm_custom_model))
        modelValues.add(CUSTOM_MODEL_SENTINEL)

        modelPref.entries = modelEntries.toTypedArray()
        modelPref.entryValues = modelValues.toTypedArray()

        // Set current value and summary
        if (currentModel.isNotBlank() && currentModel in modelValues) {
            modelPref.value = currentModel
            modelPref.summary = currentModel
        } else if (provider.models.isNotEmpty()) {
            modelPref.value = provider.models.first()
            modelPref.summary = provider.models.first()
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

    private fun showCustomModelDialog() {
        val editText = EditText(requireContext()).apply {
            setText(settings.llmModel)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.llm_custom_model_dialog_title)
            .setMessage(R.string.llm_custom_model_dialog_message)
            .setView(editText)
            .setPositiveButton(R.string.okay) { _, _ ->
                val customModel = editText.text.toString().trim()
                if (customModel.isNotBlank()) {
                    settings.llmModel = customModel
                    val provider = currentProvider()
                    if (provider != null) updateModelList(provider)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
