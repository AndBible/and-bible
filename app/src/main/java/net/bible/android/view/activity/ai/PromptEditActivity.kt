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
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.PromptContext

/**
 * Activity for creating and editing AI prompts.
 */
class PromptEditActivity : ActivityBase() {

    companion object {
        const val EXTRA_PROMPT_ID = "prompt_id"
    }

    private var prompt: AgentPrompt? = null
    private var isNewPrompt = true

    private lateinit var nameEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var templateEdit: EditText
    private lateinit var checkTextDisplaySettings: CheckBox
    private lateinit var checkVerseSelection: CheckBox
    private lateinit var checkTextSelection: CheckBox
    private lateinit var checkWindowMenu: CheckBox
    private lateinit var checkWorkspaceMenu: CheckBox
    private lateinit var checkNoteEditor: CheckBox
    private lateinit var checkStrictContextMatching: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.prompt_edit)

        buildActivityComponent().inject(this)

        nameEdit = findViewById(R.id.promptName)
        descriptionEdit = findViewById(R.id.promptDescription)
        templateEdit = findViewById(R.id.promptTemplate)
        checkTextDisplaySettings = findViewById(R.id.checkTextDisplaySettings)
        checkVerseSelection = findViewById(R.id.checkVerseSelection)
        checkTextSelection = findViewById(R.id.checkTextSelection)
        checkWindowMenu = findViewById(R.id.checkWindowMenu)
        checkWorkspaceMenu = findViewById(R.id.checkWorkspaceMenu)
        checkNoteEditor = findViewById(R.id.checkNoteEditor)
        checkStrictContextMatching = findViewById(R.id.checkStrictContextMatching)

        val promptIdStr = intent.getStringExtra(EXTRA_PROMPT_ID)
        if (promptIdStr != null) {
            isNewPrompt = false
            loadPrompt(IdType.fromString(promptIdStr))
        } else {
            isNewPrompt = true
            title = getString(R.string.new_prompt)
            // Set default value for strictContextMatching on new prompts
            checkStrictContextMatching.isChecked = true
        }
    }

    private fun loadPrompt(id: IdType) {
        lifecycleScope.launch {
            val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
            val loadedPrompt = withContext(Dispatchers.IO) {
                dao.promptById(id)
            }

            if (loadedPrompt != null) {
                prompt = loadedPrompt
                title = getString(R.string.edit_prompt)
                populateFields(loadedPrompt)
            } else {
                finish()
            }
        }
    }

    private fun populateFields(prompt: AgentPrompt) {
        nameEdit.setText(prompt.name)
        descriptionEdit.setText(prompt.description ?: "")
        templateEdit.setText(prompt.promptTemplate)

        checkTextDisplaySettings.isChecked = PromptContext.TEXT_DISPLAY_SETTINGS in prompt.showIn
        checkVerseSelection.isChecked = PromptContext.VERSE_SELECTION in prompt.showIn
        checkTextSelection.isChecked = PromptContext.TEXT_SELECTION in prompt.showIn
        checkWindowMenu.isChecked = PromptContext.WINDOW_MENU in prompt.showIn
        checkWorkspaceMenu.isChecked = PromptContext.WORKSPACE_MENU in prompt.showIn
        checkNoteEditor.isChecked = PromptContext.NOTE_EDITOR in prompt.showIn
        checkStrictContextMatching.isChecked = prompt.strictContextMatching
    }

    private fun collectShowIn(): Set<PromptContext> {
        val contexts = mutableSetOf<PromptContext>()
        if (checkTextDisplaySettings.isChecked) contexts.add(PromptContext.TEXT_DISPLAY_SETTINGS)
        if (checkVerseSelection.isChecked) contexts.add(PromptContext.VERSE_SELECTION)
        if (checkTextSelection.isChecked) contexts.add(PromptContext.TEXT_SELECTION)
        if (checkWindowMenu.isChecked) contexts.add(PromptContext.WINDOW_MENU)
        if (checkWorkspaceMenu.isChecked) contexts.add(PromptContext.WORKSPACE_MENU)
        if (checkNoteEditor.isChecked) contexts.add(PromptContext.NOTE_EDITOR)
        return contexts
    }

    private fun validateAndSave(): Boolean {
        val name = nameEdit.text.toString().trim()
        val template = templateEdit.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.prompt_name_required, Toast.LENGTH_SHORT).show()
            nameEdit.requestFocus()
            return false
        }

        if (template.isEmpty()) {
            Toast.makeText(this, R.string.prompt_template_required, Toast.LENGTH_SHORT).show()
            templateEdit.requestFocus()
            return false
        }

        val description = descriptionEdit.text.toString().trim().takeIf { it.isNotEmpty() }
        val showIn = collectShowIn()
        val strictContextMatching = checkStrictContextMatching.isChecked

        lifecycleScope.launch {
            val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()

            withContext(Dispatchers.IO) {
                if (isNewPrompt) {
                    val newPrompt = AgentPrompt(
                        name = name,
                        description = description,
                        promptTemplate = template,
                        showIn = showIn,
                        strictContextMatching = strictContextMatching,
                    )
                    dao.insert(newPrompt)
                } else {
                    prompt?.let {
                        it.name = name
                        it.description = description
                        it.promptTemplate = template
                        it.showIn = showIn
                        it.strictContextMatching = strictContextMatching
                        dao.update(it)
                    }
                }
            }

            finish()
        }

        return true
    }

    private fun deletePrompt() {
        val currentPrompt = prompt ?: return

        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_prompt_confirmation, currentPrompt.name))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
                    withContext(Dispatchers.IO) {
                        dao.delete(currentPrompt)
                    }
                    finish()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.prompt_edit_options_menu, menu)
        menu.findItem(R.id.delete_prompt)?.isVisible = !isNewPrompt
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save_prompt -> {
                validateAndSave()
                true
            }
            R.id.delete_prompt -> {
                deletePrompt()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        validateAndSave()
    }
}
