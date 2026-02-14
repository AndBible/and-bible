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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import net.bible.service.llm.agent.PermissionMode

/**
 * Activity for creating and editing AI prompts.
 */
class PromptEditActivity : ActivityBase() {

    companion object {
        const val EXTRA_PROMPT_ID = "prompt_id"
    }

    private var prompt: AgentPrompt? = null
    private var isNewPrompt = true
    private var initialName = ""
    private var initialDescription = ""
    private var initialTemplate = ""
    private var initialShowIn = emptySet<PromptContext>()
    private var initialStrictContextMatching = true
    private var initialPermissionModeIndex = 0
    private var initialAllowedTools: Set<String>? = null
    private var initialDeniedTools: Set<String>? = null

    private var currentAllowedTools: MutableSet<String> = mutableSetOf()
    private var currentDeniedTools: MutableSet<String> = mutableSetOf()
    private var hasToolPermissionOverrides = false

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
    private lateinit var permissionModeSpinner: Spinner
    private lateinit var toolPermissionsButton: Button

    private val toolPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val allowed = data?.getStringArrayListExtra(PromptToolPermissionsActivity.EXTRA_ALLOWED_TOOLS)
            val denied = data?.getStringArrayListExtra(PromptToolPermissionsActivity.EXTRA_DENIED_TOOLS)
            if (allowed != null && denied != null) {
                currentAllowedTools = allowed.toMutableSet()
                currentDeniedTools = denied.toMutableSet()
                hasToolPermissionOverrides = currentAllowedTools.isNotEmpty() || currentDeniedTools.isNotEmpty()
                updateToolPermissionsButtonText()
            }
        }
    }

    /** Maps spinner position to PermissionMode? (null = use default) */
    private val permissionModeValues: Array<PermissionMode?> = arrayOf(
        null,
        PermissionMode.ALWAYS_ASK,
        PermissionMode.ASK_ONCE_PER_RUN,
        PermissionMode.ALLOW_ALL,
        PermissionMode.DENY_ALL,
    )

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
        permissionModeSpinner = findViewById(R.id.permissionModeSpinner)
        toolPermissionsButton = findViewById(R.id.btnPromptToolPermissions)
        toolPermissionsButton.setOnClickListener { launchToolPermissions() }
        updateToolPermissionsButtonText()

        val entries = resources.getStringArray(R.array.prompt_permission_mode_entries)
        permissionModeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val promptIdStr = intent.getStringExtra(EXTRA_PROMPT_ID)
        if (promptIdStr != null) {
            isNewPrompt = false
            loadPrompt(IdType.fromString(promptIdStr))
        } else {
            isNewPrompt = true
            title = getString(R.string.new_prompt)
            // Set default value for strictContextMatching on new prompts
            checkStrictContextMatching.isChecked = true
            captureInitialState()
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
                captureInitialState()
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
        permissionModeSpinner.setSelection(permissionModeValues.indexOf(prompt.permissionMode).coerceAtLeast(0))

        hasToolPermissionOverrides = prompt.allowedTools != null || prompt.deniedTools != null
        currentAllowedTools = prompt.allowedTools?.toMutableSet() ?: mutableSetOf()
        currentDeniedTools = prompt.deniedTools?.toMutableSet() ?: mutableSetOf()
        updateToolPermissionsButtonText()
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

    private fun captureInitialState() {
        initialName = nameEdit.text.toString()
        initialDescription = descriptionEdit.text.toString()
        initialTemplate = templateEdit.text.toString()
        initialShowIn = collectShowIn()
        initialStrictContextMatching = checkStrictContextMatching.isChecked
        initialPermissionModeIndex = permissionModeSpinner.selectedItemPosition
        initialAllowedTools = if (hasToolPermissionOverrides) currentAllowedTools.toSet() else null
        initialDeniedTools = if (hasToolPermissionOverrides) currentDeniedTools.toSet() else null
    }

    private fun isDirty(): Boolean {
        return nameEdit.text.toString() != initialName ||
            descriptionEdit.text.toString() != initialDescription ||
            templateEdit.text.toString() != initialTemplate ||
            collectShowIn() != initialShowIn ||
            checkStrictContextMatching.isChecked != initialStrictContextMatching ||
            permissionModeSpinner.selectedItemPosition != initialPermissionModeIndex ||
            currentToolAllowed != initialAllowedTools ||
            currentToolDenied != initialDeniedTools
    }

    private val currentToolAllowed: Set<String>?
        get() = if (hasToolPermissionOverrides) currentAllowedTools.toSet() else null

    private val currentToolDenied: Set<String>?
        get() = if (hasToolPermissionOverrides) currentDeniedTools.toSet() else null

    private fun cancelOrConfirmDiscard() {
        if (isDirty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.discard_changes_confirmation)
                .setPositiveButton(R.string.yes) { _, _ -> finish() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            finish()
        }
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
        val selectedPermissionMode = permissionModeValues[permissionModeSpinner.selectedItemPosition]
        val allowedTools = currentToolAllowed
        val deniedTools = currentToolDenied

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
                        permissionMode = selectedPermissionMode,
                        allowedTools = allowedTools,
                        deniedTools = deniedTools,
                    )
                    dao.insert(newPrompt)
                } else {
                    prompt?.let {
                        it.name = name
                        it.description = description
                        it.promptTemplate = template
                        it.showIn = showIn
                        it.strictContextMatching = strictContextMatching
                        it.permissionMode = selectedPermissionMode
                        it.allowedTools = allowedTools
                        it.deniedTools = deniedTools
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
            android.R.id.home -> {
                cancelOrConfirmDiscard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        cancelOrConfirmDiscard()
    }

    private fun updateToolPermissionsButtonText() {
        val total = currentAllowedTools.size + currentDeniedTools.size
        val base = getString(R.string.prompt_tool_permissions)
        toolPermissionsButton.text = if (hasToolPermissionOverrides && total > 0) {
            "$base ($total)"
        } else {
            base
        }
    }

    private fun launchToolPermissions() {
        val intent = Intent(this, PromptToolPermissionsActivity::class.java).apply {
            putStringArrayListExtra(
                PromptToolPermissionsActivity.EXTRA_ALLOWED_TOOLS,
                ArrayList(currentAllowedTools)
            )
            putStringArrayListExtra(
                PromptToolPermissionsActivity.EXTRA_DENIED_TOOLS,
                ArrayList(currentDeniedTools)
            )
        }
        toolPermissionsLauncher.launch(intent)
    }
}
