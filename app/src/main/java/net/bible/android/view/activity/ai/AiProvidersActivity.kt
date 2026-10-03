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
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsActivityBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.android.view.activity.settings.PreferenceStore
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.llm.ApiFormat
import net.bible.service.llm.DynamicModelService
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.ProviderTier
import net.bible.service.llm.getApiKey
import net.bible.service.llm.removeApiKey
import net.bible.service.llm.setApiKey

class AiProvidersActivity : ActivityBase() {
    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.ai_providers_category)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, AiProvidersFragment())
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ai_providers_options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.add_provider -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.settings_container) as? AiProvidersFragment
                fragment?.addProvider()
                true
            }
            R.id.show_help -> {
                CommonUtils.showHelpDialog(
                    activity = this,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_providers_text,
                    helpPath = "ai.html#choosing-a-provider",
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class AiProvidersFragment : AiSettingsFragmentBase() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.ai_providers_settings, rootKey)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun refreshAll() {
        refreshProviderList()
    }

    fun addProvider() {
        ensureDisclaimerAccepted { showAddProviderTypeDialog() }
    }

    private fun refreshProviderList() {
        preferenceScreen.removeAll()

        val configs = dao.all()
        for (config in configs) {
            val pref = Preference(requireContext()).apply {
                key = "provider_${config.id}"
                title = config.displayName
                icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_cloud_24)
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
            preferenceScreen.addPreference(pref)
        }

    }

    private fun showAddProviderTypeDialog() {
        val context = requireContext()
        val existingTypes = dao.all().map { it.providerType }.toSet()
        val allAvailableProviders = LlmProvider.entries.filter {
            it == LlmProvider.CUSTOM || it.name !in existingTypes
        }

        val items = mutableListOf<Pair<String, LlmProvider?>>()
        val listView = ListView(context)

        fun buildItems(showUnsupported: Boolean) {
            items.clear()
            val filtered = if (showUnsupported) allAvailableProviders
            else allAvailableProviders.filter { it.tier == ProviderTier.RECOMMENDED }
            for (tier in ProviderTier.entries) {
                val inTier = filtered.filter { it.tier == tier }
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
                context, android.R.layout.simple_list_item_1, items.map { it.first }
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
            listView.adapter = adapter
        }

        val hasUnsupported = allAvailableProviders.any { it.tier != ProviderTier.RECOMMENDED }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        if (hasUnsupported) {
            val checkBox = CheckBox(context).apply {
                text = getString(R.string.show_also_unsupported_providers)
                isChecked = false
                setOnCheckedChangeListener { _, isChecked -> buildItems(isChecked) }
            }
            layout.addView(checkBox)
        }

        layout.addView(listView)
        buildItems(false)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.ai_provider_select_type)
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .show()

        listView.setOnItemClickListener { _, _, which, _ ->
            items[which].second?.let { provider ->
                dialog.dismiss()
                showEditProviderDialog(null, provider)
            }
        }
    }

    private data class ProviderDialogFields(
        val nameInput: EditText,
        val apiKeyInput: EditText,
        val endpointInput: EditText?,
        val apiFormatSpinner: Spinner?,
    )

    private fun showEditProviderDialog(config: LlmProviderConfig?, providerType: LlmProvider? = null) {
        val isNew = config == null
        val provider = providerType ?: config!!.resolveProvider()
        val isCustom = provider == LlmProvider.CUSTOM

        val (scrollView, layout) = createDialogLayout()

        val fields = buildProviderDialogLayout(layout, config, provider, isCustom)

        val dialogTitle = if (isNew) getString(R.string.ai_add_provider) else getString(R.string.ai_provider_edit)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(scrollView)
            .setPositiveButton(R.string.okay) { _, _ ->
                saveProviderConfig(config, provider, isCustom, fields)
            }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                if (!isNew) {
                    setNeutralButton(R.string.delete) { dlg, _ ->
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
            }.also {
                addLabeledField(layout, getString(R.string.ai_provider_endpoint), it)
                layout.addView(TextView(context).apply {
                    text = getString(R.string.ai_provider_endpoint_description)
                    setTextAppearance(android.R.style.TextAppearance_Small)
                })
            }
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
            if (isNew) ABEventBus.post(AppSettingsUpdated())
            Toast.makeText(requireContext(), R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
            refreshAll()
        }
    }

    private fun confirmDeleteProvider(config: LlmProviderConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.ai_provider_delete_confirm, config.displayName))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        config.removeApiKey()
                        val deletedModelIds = modelDao.getByProvider(config.id).map { it.id }.toSet()
                        dao.delete(config)
                        val currentDefault = settings.defaultModelId
                        if (currentDefault != null && currentDefault in deletedModelIds) {
                            val remaining = modelDao.all()
                            settings.defaultModelId = remaining.firstOrNull()?.id
                        }
                    }
                    ABEventBus.post(AppSettingsUpdated())
                    Toast.makeText(requireContext(), R.string.ai_provider_deleted, Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
