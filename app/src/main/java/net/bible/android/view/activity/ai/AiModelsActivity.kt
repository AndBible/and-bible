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
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsActivityBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.settings.PreferenceStore
import net.bible.service.common.CommonUtils
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmProvider

class AiModelsActivity : ActivityBase() {
    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.ai_models_category)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, AiModelsFragment())
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ai_models_options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.add_model -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.settings_container) as? AiModelsFragment
                fragment?.addModel()
                true
            }
            R.id.show_help -> {
                CommonUtils.showHelpDialog(
                    activity = this,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_models_text,
                    helpPath = "ai.html#available-models",
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class AiModelsFragment : AiSettingsFragmentBase() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.ai_models_settings, rootKey)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun refreshAll() {
        refreshModelList()
    }

    fun addModel() {
        showAddModelDialogWithProviderSelection()
    }

    private fun showAddModelDialogWithProviderSelection() {
        val providers = dao.all()
        if (providers.isEmpty()) return
        if (providers.size == 1) {
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
        preferenceScreen.removeAll()

        val defaultModelId = settings.defaultModelId
        val allModels = modelDao.all().sortedByDescending { it.id == defaultModelId }
        val providerConfigs = dao.all().associateBy { it.id }

        for (model in allModels) {
            val provider = providerConfigs[model.providerConfigId]
            val isSupported = LlmProvider.isModelSupported(model.modelId)
            val pref = Preference(requireContext()).apply {
                key = "model_${model.id}"
                title = buildString {
                    if (model.id == defaultModelId) append("★ ")
                    append(model.modelId)
                    if (isSupported) append(" ✓")
                }
                icon = AppCompatResources.getDrawable(requireContext(), R.drawable.icon_robot)
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
            preferenceScreen.addPreference(pref)
        }
    }
}
