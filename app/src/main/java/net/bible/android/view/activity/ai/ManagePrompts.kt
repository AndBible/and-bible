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

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.DefaultPrompts

/**
 * Activity for managing AI prompts.
 * Shows a list of prompts and allows creating, editing, and deleting them.
 */
class ManagePrompts : ListActivityBase(R.menu.manage_prompts_options_menu) {

    private val prompts = mutableListOf<AgentPrompt>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manage_prompts)

        buildActivityComponent().inject(this)

        title = getString(R.string.manage_prompts)

        loadPrompts()
    }

    override fun onResume() {
        super.onResume()
        loadPrompts()
    }

    private fun loadPrompts() {
        lifecycleScope.launch {
            val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()

            // Initialize default prompts if needed (thread-safe)
            withContext(Dispatchers.IO) {
                DefaultPrompts.initializeIfNeeded()
            }

            val loadedPrompts = withContext(Dispatchers.IO) {
                dao.allPrompts()
            }

            prompts.clear()
            prompts.addAll(loadedPrompts)

            listAdapter = PromptListAdapter()
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val prompt = prompts[position]
        editPrompt(prompt)
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
        super.onCreateOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.new_prompt -> {
                createNewPrompt()
                true
            }
            else -> super.onOptionsItemSelected(item)
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

            val nameView = view.findViewById<TextView>(R.id.promptName)
            val descriptionView = view.findViewById<TextView>(R.id.promptDescription)
            val contextsView = view.findViewById<TextView>(R.id.promptContexts)

            nameView.text = prompt.name
            descriptionView.text = prompt.description ?: ""
            descriptionView.visibility = if (prompt.description.isNullOrEmpty()) View.GONE else View.VISIBLE

            val contextNames = prompt.showIn.map { context ->
                when (context) {
                    net.bible.service.llm.PromptContext.TEXT_DISPLAY_SETTINGS ->
                        getString(R.string.prompt_context_text_display_settings)
                    net.bible.service.llm.PromptContext.VERSE_SELECTION ->
                        getString(R.string.prompt_context_verse_selection)
                    net.bible.service.llm.PromptContext.TEXT_SELECTION ->
                        getString(R.string.prompt_context_text_selection)
                    net.bible.service.llm.PromptContext.WINDOW_MENU ->
                        getString(R.string.prompt_context_window_menu)
                    net.bible.service.llm.PromptContext.WORKSPACE_MENU ->
                        getString(R.string.prompt_context_workspace_menu)
                    net.bible.service.llm.PromptContext.NOTE_EDITOR ->
                        getString(R.string.prompt_context_note_editor)
                }
            }
            contextsView.text = contextNames.joinToString(", ")

            return view
        }
    }
}
