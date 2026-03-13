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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.PromptContext
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.PromptCsvUtils
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.removeApiKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for AI settings.
 * When LLM is not configured, shows a setup view with a configure button.
 * When LLM is configured, shows the prompt list with a gear icon for connection settings.
 */
class AiSettingsActivity : ActivityBase() {

    private val prompts = mutableListOf<AgentPrompt>()
    private lateinit var viewFlipper: ViewFlipper
    private var listView: ListView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manage_prompts)

        buildActivityComponent().inject(this)

        title = getString(R.string.ai_settings)

        viewFlipper = findViewById(R.id.viewFlipper)

        findViewById<Button>(R.id.configureConnectionButton).setOnClickListener {
            startActivity(Intent(this, AiConnectionSettingsActivity::class.java))
        }

        listView = findViewById(android.R.id.list)
        listView?.let { lv ->
            val emptyView = findViewById<View>(android.R.id.empty)
            lv.emptyView = emptyView
            lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                editPrompt(prompts[position])
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateView()
    }

    private fun updateView() {
        if (CommonUtils.settings.llmConfigured) {
            viewFlipper.displayedChild = 1
            loadPrompts()
        } else {
            viewFlipper.displayedChild = 0
        }
        invalidateOptionsMenu()
    }

    private fun loadPrompts() {
        lifecycleScope.launch {
            val loadedPrompts = withContext(Dispatchers.IO) {
                PromptRepository.allPrompts()
            }

            prompts.clear()
            prompts.addAll(loadedPrompts)

            listView?.adapter = PromptListAdapter()
        }
    }

    private fun editPrompt(prompt: AgentPrompt) {
        val intent = Intent(this, PromptEditActivity::class.java)
        intent.putExtra(PromptEditActivity.EXTRA_PROMPT_ID, prompt.id.toString())
        startActivity(intent)
    }

    private fun createNewPrompt() {
        val intent = Intent(this, PromptEditActivity::class.java)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.manage_prompts_options_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val configured = CommonUtils.settings.llmConfigured
        menu.findItem(R.id.new_prompt)?.isVisible = configured
        menu.findItem(R.id.reset_prompts)?.isVisible = configured
        menu.findItem(R.id.ai_connection_settings)?.isVisible = configured
        menu.findItem(R.id.reset_all_ai_settings)?.isVisible = configured
        menu.findItem(R.id.export_prompts_csv)?.isVisible = configured
        menu.findItem(R.id.import_prompts_csv)?.isVisible = configured
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.new_prompt -> {
                createNewPrompt()
                true
            }
            R.id.reset_prompts -> {
                confirmResetToDefaults()
                true
            }
            R.id.ai_connection_settings -> {
                startActivity(Intent(this, AiConnectionSettingsActivity::class.java))
                true
            }
            R.id.reset_all_ai_settings -> {
                confirmResetAllAiSettings()
                true
            }
            R.id.export_prompts_csv -> {
                lifecycleScope.launch { exportPrompts() }
                true
            }
            R.id.import_prompts_csv -> {
                lifecycleScope.launch { importPrompts() }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmResetToDefaults() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_prompts_confirm_title)
            .setMessage(R.string.reset_prompts_confirm_message)
            .setPositiveButton(R.string.okay) { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmResetAllAiSettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_all_ai_settings_confirm_title)
            .setMessage(R.string.reset_all_ai_settings_confirm_message)
            .setPositiveButton(R.string.okay) { _, _ ->
                resetAllAiSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resetAllAiSettings() {
        val settings = CommonUtils.settings
        settings.llmProvider = ""
        settings.llmApiKey = ""
        settings.llmEndpoint = ""
        settings.llmModel = ""

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
                for (config in providerDao.all()) {
                    config.removeApiKey()
                    LlmCostTracker.reset(config.id)
                }
                providerDao.deleteAll()

                PromptRepository.deleteAllUserPrompts()
            }
            updateView()
        }
    }

    private fun resetToDefaults() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                PromptRepository.deleteAllUserPrompts()
            }
            loadPrompts()
        }
    }

    private suspend fun exportPrompts() {
        try {
            val dao = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
            val userPrompts = withContext(Dispatchers.IO) { dao.allPrompts() }

            if (userPrompts.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_prompts_to_export), Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                putExtra(Intent.EXTRA_TITLE, "ai_prompts_$timestamp.csv")
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            PromptCsvUtils.exportPromptsToCsv(outputStream, userPrompts)
                        } ?: throw IllegalArgumentException("Could not open output stream")
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.prompts_csv_export_success, userPrompts.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting prompts to CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.prompts_csv_export_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importPrompts() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val importResult = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            PromptCsvUtils.importPromptsFromCsv(inputStream)
                        } ?: throw IllegalArgumentException("Could not open input stream")
                    }

                    if (importResult.errors > 0) {
                        val message =
                            getString(R.string.prompts_csv_import_errors, importResult.created, importResult.updated, importResult.errors) +
                                "\n\n" + importResult.errorMessages.take(5).joinToString("\n") +
                                if (importResult.errorMessages.size > 5) "\n..." else ""

                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.import_prompts_csv))
                            .setMessage(message)
                            .setPositiveButton(R.string.okay, null)
                            .show()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.prompts_csv_import_success, importResult.created, importResult.updated),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    loadPrompts()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing prompts from CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.prompts_csv_import_failed, e.message),
                exception = e
            )
        }
    }

    inner class PromptListAdapter : ArrayAdapter<AgentPrompt>(
        this,
        R.layout.manage_prompts_list_item,
        prompts
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.manage_prompts_list_item,
                parent,
                false
            )

            val prompt = prompts[position]
            val isBuiltIn = BuiltInPrompts.isBuiltIn(prompt.id)

            val nameView = view.findViewById<TextView>(R.id.promptName)
            val descriptionView = view.findViewById<TextView>(R.id.promptDescription)
            val contextsView = view.findViewById<TextView>(R.id.promptContexts)
            val builtInBadge = view.findViewById<TextView>(R.id.builtInBadge)

            nameView.text = prompt.name
            descriptionView.text = prompt.description ?: ""
            descriptionView.visibility = if (prompt.description.isNullOrEmpty()) View.GONE else View.VISIBLE

            if (builtInBadge != null) {
                builtInBadge.visibility = if (isBuiltIn) View.VISIBLE else View.GONE
            }

            val contextNames = prompt.showIn.map { context ->
                when (context) {
                    PromptContext.VERSE_SELECTION ->
                        getString(R.string.prompt_context_verse_selection)
                    PromptContext.TEXT_SELECTION ->
                        getString(R.string.prompt_context_text_selection)
                    PromptContext.WINDOW_MENU ->
                        getString(R.string.prompt_context_window_menu)
                    PromptContext.WORKSPACE_MENU ->
                        getString(R.string.prompt_context_workspace_menu)
                    PromptContext.NOTE_EDITOR ->
                        getString(R.string.prompt_context_note_editor)
                }
            }
            contextsView.text = contextNames.joinToString(", ")

            return view
        }
    }

    companion object {
        private const val TAG = "AiSettingsActivity"
    }
}
