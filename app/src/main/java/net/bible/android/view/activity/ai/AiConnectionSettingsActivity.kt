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
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsActivityBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.settings.PreferenceStore
import net.bible.service.llm.LlmCostTracker

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

class AiConnectionSettingsFragment : AiSettingsFragmentBase() {

    private lateinit var disclaimerWarningPref: Preference
    private lateinit var gettingStartedPref: Preference
    private lateinit var providersModelsCat: PreferenceCategory
    private lateinit var providersShortcutPref: Preference
    private lateinit var modelsShortcutPref: Preference
    private lateinit var behaviorCategory: PreferenceCategory
    private lateinit var manageToolPermissionsPref: Preference
    private lateinit var manageAiDocumentsPref: Preference
    private lateinit var aiLanguagePref: Preference
    private lateinit var commentaryMaxResponsePref: Preference
    private lateinit var maxIterationsPref: Preference
    private lateinit var askModelBeforeRunPref: SwitchPreferenceCompat
    private lateinit var usageCategory: PreferenceCategory
    private lateinit var usageSummaryPref: Preference
    private lateinit var resetUsagePref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.ai_connection_settings, rootKey)

        disclaimerWarningPref = preferenceScreen.findPreference("ai_disclaimer_warning")!!
        gettingStartedPref = preferenceScreen.findPreference("ai_getting_started")!!
        providersModelsCat = preferenceScreen.findPreference("ai_providers_models_category")!!
        providersShortcutPref = preferenceScreen.findPreference("ai_providers_shortcut")!!
        modelsShortcutPref = preferenceScreen.findPreference("ai_models_shortcut")!!
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

        // Set initial visibility before first render to avoid layout animation
        val hasProviders = dao.getCount() > 0
        gettingStartedPref.isVisible = !hasProviders
        modelsShortcutPref.isVisible = hasProviders
        behaviorCategory.isVisible = hasProviders
        usageCategory.isVisible = hasProviders

        setupDisclaimerWarning()
        setupGettingStarted()
        setupProviderShortcut()
        setupModelShortcut()
        setupToolPermissions()
        setupDocumentFilter()
        setupAiLanguage()
        setupCommentaryMaxResponse()
        setupMaxIterations()
        setupAskModelBeforeRun()
        setupUsage()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        updateToolPermissionsSummary()
        updateDocumentFilterSummary()
    }

    override fun refreshAll() {
        val providers = dao.all()
        val hasProviders = providers.isNotEmpty()

        providersShortcutPref.summary = if (hasProviders) {
            providers.joinToString(", ") { it.displayName }
        } else {
            getString(R.string.ai_providers_summary_none)
        }
        updateModelsSummary()

        gettingStartedPref.isVisible = !hasProviders
        modelsShortcutPref.isVisible = hasProviders
        behaviorCategory.isVisible = hasProviders
        usageCategory.isVisible = hasProviders
        if (hasProviders) updateUsageSummary()
    }

    private fun setupDisclaimerWarning() {
        disclaimerWarningPref.setOnPreferenceClickListener {
            showDisclaimerInfoDialog()
            true
        }
    }

    private fun setupGettingStarted() {
        gettingStartedPref.setOnPreferenceClickListener {
            ensureDisclaimerAccepted { showEasySetupStep1() }
            true
        }
    }

    private fun setupProviderShortcut() {
        providersShortcutPref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AiProvidersActivity::class.java))
            true
        }
    }

    private fun setupModelShortcut() {
        modelsShortcutPref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AiModelsActivity::class.java))
            true
        }
    }

    private fun updateModelsSummary() {
        val defaultModelId = settings.defaultModelId
        val models = modelDao.all().sortedByDescending { it.id == defaultModelId }
        modelsShortcutPref.summary = if (models.isEmpty()) {
            getString(R.string.ai_models_summary_none)
        } else {
            models.joinToString(", ") { model ->
                if (model.id == defaultModelId) "★ ${model.modelId}" else model.modelId
            }
        }
    }

    // ---- Behavior settings ----

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
                else if (currentTag != null) languages.size - 1
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

    // ---- Usage ----

    private fun setupUsage() {
        updateUsageSummary()
        resetUsagePref.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.llm_reset_usage_confirm_title)
                .setMessage(R.string.llm_reset_usage_confirm_message)
                .setPositiveButton(R.string.okay) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
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
