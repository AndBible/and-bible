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
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
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
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.PermissionMode

/**
 * Activity for creating and editing AI prompts.
 * Opens in read-only mode for built-in prompts, with a "Copy to customize" option.
 */
class PromptEditActivity : ActivityBase() {

    private var prompt: AgentPrompt? = null
    private var isNewPrompt = true
    private var isBuiltIn = false
    private var initialName = ""
    private var initialDescription = ""
    private var initialTemplate = ""
    private var initialShowIn = emptySet<PromptContext>()
    private var initialStrictContextMatching = true
    private var initialPermissionModeIndex = 0
    private var initialAllowedTools: Set<String>? = null
    private var initialDeniedTools: Set<String>? = null
    private var initialProviderSpinnerIndex = 0
    private var initialModelOverrideSpinnerIndex = 0
    private var initialModelOverrideCustomText = ""

    private var currentAllowedTools: MutableSet<String> = mutableSetOf()
    private var currentDeniedTools: MutableSet<String> = mutableSetOf()
    private var hasToolPermissionOverrides = false

    private lateinit var nameEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var templateEdit: EditText
    private lateinit var checkVerseSelection: CheckBox
    private lateinit var checkTextSelection: CheckBox
    private lateinit var checkWindowMenu: CheckBox
    private lateinit var checkWorkspaceMenu: CheckBox
    private lateinit var checkNoteEditor: CheckBox
    private lateinit var checkStrictContextMatching: CheckBox
    private lateinit var permissionModeSpinner: Spinner
    private lateinit var providerOverrideSpinner: Spinner
    private lateinit var modelOverrideSpinner: Spinner
    private lateinit var modelOverrideCustomInput: EditText
    private lateinit var toolPermissionsButton: Button

    /** Provider config IDs corresponding to spinner positions: null = default */
    private var providerOverrideValues: List<IdType?> = listOf(null)
    private var providerConfigs: List<LlmProviderConfig> = emptyList()

    /** Model values corresponding to spinner positions: null = default, string = model name, CUSTOM_SENTINEL = custom */
    private var modelOverrideValues: List<String?> = listOf(null)

    companion object {
        const val EXTRA_PROMPT_ID = "prompt_id"
        private const val CUSTOM_SENTINEL = "\u0000custom"
    }

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

    private val providerConfigDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.prompt_edit)

        buildActivityComponent().inject(this)

        nameEdit = findViewById(R.id.promptName)
        descriptionEdit = findViewById(R.id.promptDescription)
        templateEdit = findViewById(R.id.promptTemplate)
        checkVerseSelection = findViewById(R.id.checkVerseSelection)
        checkTextSelection = findViewById(R.id.checkTextSelection)
        checkWindowMenu = findViewById(R.id.checkWindowMenu)
        checkWorkspaceMenu = findViewById(R.id.checkWorkspaceMenu)
        checkNoteEditor = findViewById(R.id.checkNoteEditor)
        checkStrictContextMatching = findViewById(R.id.checkStrictContextMatching)
        permissionModeSpinner = findViewById(R.id.permissionModeSpinner)
        providerOverrideSpinner = findViewById(R.id.providerOverrideSpinner)
        modelOverrideSpinner = findViewById(R.id.modelOverrideSpinner)
        modelOverrideCustomInput = findViewById(R.id.modelOverrideCustomInput)
        toolPermissionsButton = findViewById(R.id.btnPromptToolPermissions)
        toolPermissionsButton.setOnClickListener { launchToolPermissions() }
        updateToolPermissionsButtonText()

        val entries = resources.getStringArray(R.array.prompt_permission_mode_entries)
        permissionModeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        setupProviderOverrideSpinner()
        setupModelOverrideSpinner()

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
            val loadedPrompt = withContext(Dispatchers.IO) {
                PromptRepository.promptById(id)
            }

            if (loadedPrompt != null) {
                prompt = loadedPrompt
                isBuiltIn = BuiltInPrompts.isBuiltIn(loadedPrompt.id)

                if (isBuiltIn) {
                    title = getString(R.string.built_in_prompt)
                    setReadOnlyMode()
                } else {
                    title = getString(R.string.edit_prompt)
                }

                populateFields(loadedPrompt)
                captureInitialState()
                invalidateOptionsMenu()
            } else {
                finish()
            }
        }
    }

    /**
     * Disable all input fields for built-in (read-only) prompts.
     */
    private fun setReadOnlyMode() {
        nameEdit.isEnabled = false
        descriptionEdit.isEnabled = false
        templateEdit.isEnabled = false
        checkVerseSelection.isEnabled = false
        checkTextSelection.isEnabled = false
        checkWindowMenu.isEnabled = false
        checkWorkspaceMenu.isEnabled = false
        checkNoteEditor.isEnabled = false
        checkStrictContextMatching.isEnabled = false
        permissionModeSpinner.isEnabled = false
        providerOverrideSpinner.isEnabled = false
        modelOverrideSpinner.isEnabled = false
        modelOverrideCustomInput.isEnabled = false
        toolPermissionsButton.isEnabled = false

        // Show a notice that this is a built-in prompt
        val noticeView = findViewById<TextView>(R.id.builtInNotice)
        noticeView?.visibility = View.VISIBLE
    }

    private fun populateFields(prompt: AgentPrompt) {
        nameEdit.setText(prompt.name)
        descriptionEdit.setText(prompt.description ?: "")
        templateEdit.setText(prompt.promptTemplate)

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

        setProviderOverride(prompt.providerConfigId)
        setModelOverride(prompt.modelOverride)
    }

    private fun collectShowIn(): Set<PromptContext> {
        val contexts = mutableSetOf<PromptContext>()
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
        initialProviderSpinnerIndex = providerOverrideSpinner.selectedItemPosition
        initialModelOverrideSpinnerIndex = modelOverrideSpinner.selectedItemPosition
        initialModelOverrideCustomText = modelOverrideCustomInput.text.toString()
    }

    private fun isDirty(): Boolean {
        if (isBuiltIn) return false
        return nameEdit.text.toString() != initialName ||
            descriptionEdit.text.toString() != initialDescription ||
            templateEdit.text.toString() != initialTemplate ||
            collectShowIn() != initialShowIn ||
            checkStrictContextMatching.isChecked != initialStrictContextMatching ||
            permissionModeSpinner.selectedItemPosition != initialPermissionModeIndex ||
            currentToolAllowed != initialAllowedTools ||
            currentToolDenied != initialDeniedTools ||
            providerOverrideSpinner.selectedItemPosition != initialProviderSpinnerIndex ||
            modelOverrideSpinner.selectedItemPosition != initialModelOverrideSpinnerIndex ||
            modelOverrideCustomInput.text.toString() != initialModelOverrideCustomText
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

    private fun validateAndSave() {
        if (isBuiltIn) return

        val name = nameEdit.text.toString().trim()
        val template = templateEdit.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.prompt_name_required, Toast.LENGTH_SHORT).show()
            nameEdit.requestFocus()
            return
        }

        if (template.isEmpty()) {
            Toast.makeText(this, R.string.prompt_template_required, Toast.LENGTH_SHORT).show()
            templateEdit.requestFocus()
            return
        }

        val description = descriptionEdit.text.toString().trim().takeIf { it.isNotEmpty() }
        val showIn = collectShowIn()
        val strictContextMatching = checkStrictContextMatching.isChecked
        val selectedPermissionMode = permissionModeValues[permissionModeSpinner.selectedItemPosition]
        val allowedTools = currentToolAllowed
        val deniedTools = currentToolDenied
        val selectedProviderConfigId = getSelectedProviderConfigId()
        val selectedModelOverride = getSelectedModelOverride()

        lifecycleScope.launch {
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
                        modelOverride = selectedModelOverride,
                        providerConfigId = selectedProviderConfigId,
                    )
                    PromptRepository.insertPrompt(newPrompt)
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
                        it.modelOverride = selectedModelOverride
                        it.providerConfigId = selectedProviderConfigId
                        PromptRepository.updatePrompt(it)
                    }
                }
            }

            finish()
        }
    }

    private fun deletePrompt() {
        if (isBuiltIn) return
        val currentPrompt = prompt ?: return

        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_prompt_confirmation, currentPrompt.name))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        PromptRepository.deletePrompt(currentPrompt)
                    }
                    finish()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /**
     * Copy the current prompt (built-in or user) to create a new editable user prompt.
     */
    private fun copyToCustomize() {
        val currentPrompt = prompt ?: return

        lifecycleScope.launch {
            val newId = withContext(Dispatchers.IO) {
                PromptRepository.copyPrompt(currentPrompt.id)
            }

            if (newId != null) {
                Toast.makeText(this@PromptEditActivity, R.string.prompt_copied, Toast.LENGTH_SHORT).show()
                // Open the new copy for editing
                val intent = Intent(this@PromptEditActivity, PromptEditActivity::class.java)
                intent.putExtra(EXTRA_PROMPT_ID, newId.toString())
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.prompt_edit_options_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (isBuiltIn) {
            // Built-in: hide save/delete, show copy
            menu.findItem(R.id.save_prompt)?.isVisible = false
            menu.findItem(R.id.delete_prompt)?.isVisible = false
            menu.findItem(R.id.copy_to_customize)?.isVisible = true
        } else {
            // User prompt: show save, show delete for existing, hide copy
            menu.findItem(R.id.save_prompt)?.isVisible = true
            menu.findItem(R.id.delete_prompt)?.isVisible = !isNewPrompt
            menu.findItem(R.id.copy_to_customize)?.isVisible = !isNewPrompt
        }
        return super.onPrepareOptionsMenu(menu)
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
            R.id.copy_to_customize -> {
                copyToCustomize()
                true
            }
            R.id.view_tools -> {
                startActivity(Intent(this, ToolInfoActivity::class.java))
                true
            }
            R.id.prompt_help -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.andbible.org/en/latest/ai.html")))
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

    // ---- Provider override spinner ----

    private fun setupProviderOverrideSpinner() {
        providerConfigs = providerConfigDao.all()

        val displayEntries = mutableListOf<String>()
        val values = mutableListOf<IdType?>()

        // First entry: "Default"
        val defaultConfig = providerConfigs.find { it.isDefault }
        val defaultSuffix = if (defaultConfig != null) " (${defaultConfig.displayName})" else ""
        displayEntries.add(getString(R.string.prompt_provider_default) + defaultSuffix)
        values.add(null)

        // Then all configured providers
        for (config in providerConfigs) {
            displayEntries.add(config.displayName)
            values.add(config.id)
        }

        providerOverrideValues = values
        providerOverrideSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayEntries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        providerOverrideSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // When provider changes, update model spinner to show that provider's models
                updateModelOverrideSpinnerForProvider()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setProviderOverride(providerConfigId: IdType?) {
        val idx = if (providerConfigId == null) {
            0
        } else {
            providerOverrideValues.indexOf(providerConfigId).coerceAtLeast(0)
        }
        providerOverrideSpinner.setSelection(idx)
    }

    private fun getSelectedProviderConfigId(): IdType? =
        providerOverrideValues.getOrNull(providerOverrideSpinner.selectedItemPosition)

    // ---- Model override spinner ----

    private fun setupModelOverrideSpinner() {
        updateModelOverrideSpinnerForProvider()
    }

    private fun updateModelOverrideSpinnerForProvider() {
        val selectedProviderConfigId = getSelectedProviderConfigId()
        val providerConfig = selectedProviderConfigId?.let { id -> providerConfigs.find { it.id == id } }
            ?: providerConfigs.find { it.isDefault }

        val provider = providerConfig?.resolveProvider()

        val globalModel = providerConfig?.resolveDefaultModel() ?: ""
        val defaultSuffix = " (${getString(R.string.prompt_model_default)})"
        val customLabel = getString(R.string.prompt_model_custom)

        val displayEntries = mutableListOf<String>()
        val values = mutableListOf<String?>()
        for (model in provider?.models ?: emptyList()) {
            if (model == globalModel) {
                displayEntries.add(model + defaultSuffix)
                values.add(null)  // null = use global/provider default
            } else {
                displayEntries.add(model)
                values.add(model)
            }
        }
        // If global model wasn't in the provider list, add it at the top
        if (null !in values) {
            displayEntries.add(0, (globalModel.ifEmpty { "default" }) + defaultSuffix)
            values.add(0, null)
        }
        displayEntries.add(customLabel)
        values.add(CUSTOM_SENTINEL)

        // Preserve current selection if possible
        val currentSelection = if (::modelOverrideSpinner.isInitialized && modelOverrideValues.isNotEmpty()) {
            modelOverrideValues.getOrNull(modelOverrideSpinner.selectedItemPosition)
        } else null

        modelOverrideValues = values
        modelOverrideSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayEntries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Restore selection
        if (currentSelection != null) {
            val idx = values.indexOf(currentSelection)
            if (idx >= 0) modelOverrideSpinner.setSelection(idx)
        }

        modelOverrideSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                modelOverrideCustomInput.visibility =
                    if (modelOverrideValues[position] == CUSTOM_SENTINEL) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setModelOverride(modelOverride: String?) {
        val idx = if (modelOverride == null) {
            0  // Default
        } else {
            val found = modelOverrideValues.indexOf(modelOverride)
            if (found >= 0) {
                found  // Known model
            } else {
                // Custom model not in the list
                modelOverrideCustomInput.setText(modelOverride)
                modelOverrideValues.indexOf(CUSTOM_SENTINEL)
            }
        }
        modelOverrideSpinner.setSelection(idx.coerceAtLeast(0))
    }

    private fun getSelectedModelOverride(): String? {
        return when (val value = modelOverrideValues[modelOverrideSpinner.selectedItemPosition]) {
            null -> null  // Default
            CUSTOM_SENTINEL -> modelOverrideCustomInput.text.toString().trim().takeIf { it.isNotEmpty() }
            else -> value
        }
    }
}
