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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.service.common.htmlToSpan
import net.bible.service.llm.DynamicModelService
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProcessingService
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.setApiKey

/**
 * Easy Setup wizard dialogs for AI settings fragments.
 * Provides a simple 3-step flow for new users to configure AI.
 */

internal data class RecommendedSetup(
    val provider: LlmProvider,
    val modelId: String,
    val description: String,
    val badge: String? = null,
)

internal fun AiSettingsFragmentBase.getRecommendedSetups() = listOf(
    RecommendedSetup(LlmProvider.GEMINI, "gemini-3-flash-preview",
        getString(R.string.easy_setup_gemini_desc), getString(R.string.easy_setup_free_tier)),
    RecommendedSetup(LlmProvider.ANTHROPIC, "claude-haiku-4-5",
        getString(R.string.easy_setup_anthropic_desc)),
    RecommendedSetup(LlmProvider.OPENAI, "gpt-5.4-mini",
        getString(R.string.easy_setup_openai_desc)),
)

/** Step 1: Choose a recommended provider+model */
internal fun AiSettingsFragmentBase.showEasySetupStep1() {
    val setups = getRecommendedSetups()
    val items = setups.map { setup ->
        val base = "${setup.provider.displayName} — ${setup.description}"
        if (setup.badge != null) "$base (${setup.badge})" else base
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
internal fun AiSettingsFragmentBase.showEasySetupStep2(setup: RecommendedSetup) {
    val context = requireContext()
    val (scrollView, layout) = createDialogLayout()

    val instructionHtml = getString(R.string.easy_setup_api_key_instructions) +
        "<br><a href=\"${setup.provider.apiKeyUrl}\">${setup.provider.displayName}</a>"
    val instructionView = TextView(context).apply {
        text = htmlToSpan(instructionHtml)
        movementMethod = LinkMovementMethod.getInstance()
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = (8 * resources.displayMetrics.density).toInt()
        layoutParams = params
    }
    layout.addView(instructionView)

    val apiKeyInput = EditText(context).apply {
        hint = getString(R.string.ai_provider_api_key)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }
    layout.addView(apiKeyInput)

    val statusText = TextView(context).apply {
        visibility = View.GONE
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        .setNeutralButton(R.string.easy_setup_test_connection, null)
        .setNegativeButton(R.string.cancel, null)
        .show()

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
internal fun AiSettingsFragmentBase.performEasySetup(setup: RecommendedSetup, apiKey: String) {
    lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            val providerConfig = LlmProviderConfig(
                providerType = setup.provider.name,
                displayName = setup.provider.displayName,
                orderNumber = dao.getCount(),
            )
            dao.insert(providerConfig)
            providerConfig.setApiKey(apiKey)

            val configuredModel = LlmConfiguredModel.create(
                providerConfigId = providerConfig.id,
                modelId = setup.modelId,
            )
            modelDao.insert(configuredModel)
            settings.defaultModelId = configuredModel.id

            if (setup.provider.supportsDynamicModels) {
                val fetchKey = if (setup.provider.modelsEndpointPublic) "" else apiKey
                DynamicModelService.fetchModels(setup.provider.endpoint, fetchKey, setup.provider.name)
            }
        }
        ABEventBus.post(AppSettingsUpdated())
        refreshAll()
        showEasySetupStep3()
    }
}

/** Step 3: Done! */
internal fun AiSettingsFragmentBase.showEasySetupStep3() {
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.easy_setup_done_title)
        .setMessage(R.string.easy_setup_done_message)
        .setPositiveButton(R.string.okay, null)
        .show()
}
