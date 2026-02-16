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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository

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
                val prompt = prompts[position]
                if (BuiltInPrompts.isBuiltIn(prompt.id)) {
                    viewBuiltInPrompt(prompt)
                } else {
                    editPrompt(prompt)
                }
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

    private fun viewBuiltInPrompt(prompt: AgentPrompt) {
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
        settings.llmConfirmBeforeCall = true
        settings.llmDebounceMs = 1000

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
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
                    PromptContext.TEXT_DISPLAY_SETTINGS ->
                        getString(R.string.prompt_context_text_display_settings)
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
}
