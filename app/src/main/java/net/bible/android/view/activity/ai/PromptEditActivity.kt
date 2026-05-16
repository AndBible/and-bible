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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.PromptEditBinding
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.common.AiSettings
import net.bible.service.common.CommonUtils
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentTool
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.BuiltinPromptOverride
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.ModelPricing
import net.bible.service.llm.PromptCategory
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.PermissionMode

/**
 * Activity for creating and editing AI prompts.
 * Opens in read-only mode for built-in prompts, with a "Copy to customize" option.
 *
 * Organized into three tabs:
 * - Prompt: name, description, template, behavior options, context selection
 * - Permissions: permission mode and per-tool permission overrides
 * - Advanced: model override, cache settings, max iterations (PreferenceFragment)
 */
class PromptEditActivity : ActivityBase() {

    private var prompt: AgentPrompt? = null
    private var isNewPrompt = true
    private var isBuiltIn = false
    private var isReadOnly = false
    private var sourceModule: String? = null
    private var initialName = ""
    private var initialDescription = ""
    private var initialTemplate = ""
    private var initialShowIn = emptySet<PromptContext>()
    private var initialPermissionModeIndex = 0
    private var initialAllowedTools: Set<AgentTool>? = null
    private var initialDeniedTools: Set<AgentTool>? = null
    private var initialIsTextTransformation = false
    private var initialCategoryPosition = 0

    /** Category list for spinner: null entry at index 0 = "No category" */
    private var categoryList: List<PromptCategory?> = emptyList()

    /** Initial state snapshot for Advanced tab (from the in-memory DataStore) */
    private var initialAdvancedSnapshot = AdvancedDataStore.Snapshot()

    private lateinit var binding: PromptEditBinding
    private lateinit var listBuilder: ToolPermissionListBuilder
    internal lateinit var advancedDataStore: AdvancedDataStore
    private var advancedFragment: PromptAdvancedSettingsFragment? = null

    companion object {
        const val EXTRA_PROMPT_ID = "prompt_id"
        const val EXTRA_PROMPT_TEMPLATE = "prompt_template"
        const val EXTRA_EXECUTE_AFTER_SAVE = "execute_after_save"
        const val EXTRA_DEFAULT_CONTEXT = "default_context"
        const val RESULT_PROMPT_ID = "result_prompt_id"
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
        // Initialize before super.onCreate() because fragment restoration may trigger
        // onCreatePreferences() which accesses advancedDataStore.
        advancedDataStore = AdvancedDataStore()

        super.onCreate(savedInstanceState)
        binding = PromptEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildActivityComponent().inject(this)

        setupTabs()

        val entries = resources.getStringArray(R.array.prompt_permission_mode_entries)
        binding.permissionModeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        setupCategorySpinner()

        listBuilder = ToolPermissionListBuilder(this, binding.toolListContainer, ToolPermissionListBuilder.Mode.PROMPT)
        binding.btnResetToolPermissions.setOnClickListener { listBuilder.resetAll() }

        // Add the Advanced preferences fragment
        advancedFragment = PromptAdvancedSettingsFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.advancedContent, advancedFragment!!)
            .commit()

        val promptIdStr = intent.getStringExtra(EXTRA_PROMPT_ID)
        if (promptIdStr != null) {
            isNewPrompt = false
            loadPrompt(IdType.fromString(promptIdStr))
        } else {
            isNewPrompt = true
            title = getString(R.string.new_prompt)

            // Pre-fill template from custom prompt dialog
            intent.getStringExtra(EXTRA_PROMPT_TEMPLATE)?.let { template ->
                binding.promptTemplate.setText(template)
            }

            // Pre-check the context checkbox matching where the prompt was initiated
            intent.getStringExtra(EXTRA_DEFAULT_CONTEXT)?.let { contextName ->
                try {
                    when (PromptContext.valueOf(contextName)) {
                        PromptContext.VERSE_SELECTION -> binding.checkVerseSelection.isChecked = true
                        PromptContext.TEXT_SELECTION -> binding.checkTextSelection.isChecked = true
                        PromptContext.WINDOW_MENU -> binding.checkWindowMenu.isChecked = true
                        PromptContext.WORKSPACE_MENU -> binding.checkWorkspaceMenu.isChecked = true
                        PromptContext.NOTE_EDITOR -> binding.checkNoteEditor.isChecked = true
                    }
                } catch (_: IllegalArgumentException) { }
            }

            // Defaults for new prompt
            advancedDataStore.strictContextMatching = true
            advancedDataStore.modelOverrideId = null
            advancedDataStore.maxIterations = null

            binding.checkTextTransformation.setOnCheckedChangeListener { _, _ -> updateTextTransformationDependentState() }
            buildToolPermissions(emptySet(), emptySet())
            captureInitialState()
        }
    }

    private fun setupCategorySpinner() {
        val categories = PromptRepository.allCategories()
        categoryList = listOf(null) + categories
        val displayNames = categoryList.map { it?.name ?: getString(R.string.category_none) }
        binding.categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun selectCategoryInSpinner(categoryId: IdType?) {
        val index = if (categoryId == null) 0 else categoryList.indexOfFirst { it?.id == categoryId }.coerceAtLeast(0)
        binding.categorySpinner.setSelection(index)
    }

    private fun getSelectedCategoryId(): IdType? = categoryList.getOrNull(binding.categorySpinner.selectedItemPosition)?.id

    private fun setupTabs() {
        binding.tabLayout.apply {
            addTab(newTab().setText(R.string.prompt_tab_prompt))
            addTab(newTab().setText(R.string.prompt_tab_permissions))
            addTab(newTab().setText(R.string.prompt_tab_advanced))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    showTab(tab.position)
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
    }

    private fun showTab(position: Int) {
        val hasPermissionsTab = binding.tabLayout.tabCount > 2
        binding.promptContent.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.permissionsContent.visibility = if (hasPermissionsTab && position == 1) View.VISIBLE else View.GONE
        binding.advancedContent.visibility =
            if ((hasPermissionsTab && position == 2) || (!hasPermissionsTab && position == 1))
                View.VISIBLE else View.GONE
    }

    private fun loadPrompt(id: IdType) {
        lifecycleScope.launch {
            val loadedPrompt = withContext(Dispatchers.IO) {
                PromptRepository.promptById(id)
            }

            if (loadedPrompt != null) {
                prompt = loadedPrompt
                isBuiltIn = BuiltInPrompts.isBuiltIn(loadedPrompt.id)
                sourceModule = loadedPrompt.sourceModule
                isReadOnly = PromptRepository.isReadOnly(loadedPrompt.id)

                title = when {
                    isBuiltIn -> getString(R.string.built_in_prompt)
                    sourceModule != null -> getString(R.string.addon_prompt_badge, sourceModule)
                    else -> getString(R.string.edit_prompt)
                }

                populateFields(loadedPrompt)
                if (isReadOnly) setReadOnlyMode()
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
        binding.apply {
            promptName.isEnabled = false
            promptDescription.isEnabled = false
            promptTemplate.isEnabled = false
            checkVerseSelection.isEnabled = false
            checkTextSelection.isEnabled = false
            checkWindowMenu.isEnabled = false
            checkWorkspaceMenu.isEnabled = false
            checkNoteEditor.isEnabled = false
            checkBibleOnly.isEnabled = false
            checkTextTransformation.isEnabled = false
            categorySpinner.isEnabled = false
            permissionModeSpinner.isEnabled = false
            btnResetToolPermissions.visibility = View.GONE
            builtInNotice.text = if (sourceModule != null) {
                getString(R.string.addon_prompt_notice, sourceModule)
            } else {
                getString(R.string.built_in_prompt_notice)
            }
            builtInNotice.visibility = View.VISIBLE
        }
        listBuilder.setReadOnly()
        advancedFragment?.setReadOnly(keepModelEditable = isBuiltIn)
    }

    private fun buildToolPermissions(allowedTools: Set<AgentTool>, deniedTools: Set<AgentTool>) {
        listBuilder.build(
            allowedTools = allowedTools,
            deniedTools = deniedTools,
            globalAllowed = CommonUtils.aiSettings.permanentlyAllowedTools,
            globalDenied = CommonUtils.aiSettings.permanentlyDeniedTools,
        )
    }

    private fun populateFields(prompt: AgentPrompt) {
        binding.apply {
            promptName.setText(prompt.name)
            promptDescription.setText(prompt.description ?: "")
            promptTemplate.setText(prompt.promptTemplate)

            checkVerseSelection.isChecked = PromptContext.VERSE_SELECTION in prompt.showIn
            checkTextSelection.isChecked = PromptContext.TEXT_SELECTION in prompt.showIn
            checkWindowMenu.isChecked = PromptContext.WINDOW_MENU in prompt.showIn
            checkWorkspaceMenu.isChecked = PromptContext.WORKSPACE_MENU in prompt.showIn
            checkNoteEditor.isChecked = PromptContext.NOTE_EDITOR in prompt.showIn
            checkBibleOnly.isChecked = prompt.bibleOnly
            checkTextTransformation.isChecked = prompt.isTextTransformation
            updateBibleOnlyDependentState()
            updateTextTransformationDependentState()
            checkBibleOnly.setOnCheckedChangeListener { _, _ -> updateBibleOnlyDependentState() }
            checkTextTransformation.setOnCheckedChangeListener { _, _ -> updateTextTransformationDependentState() }
            permissionModeSpinner.setSelection(permissionModeValues.indexOf(prompt.permissionMode).coerceAtLeast(0))
        }

        selectCategoryInSpinner(PromptRepository.getCategoryForPrompt(prompt)?.id)

        buildToolPermissions(
            prompt.allowedTools ?: emptySet(),
            prompt.deniedTools ?: emptySet(),
        )

        // Populate Advanced tab data store
        advancedDataStore.modelOverrideId = prompt.configuredModelId
        advancedDataStore.strictContextMatching = prompt.strictContextMatching
        advancedDataStore.maxIterations = prompt.maxIterations
        advancedDataStore.specifyBeforeRun = prompt.specifyBeforeRun
        advancedDataStore.noDocumentCreation = prompt.noDocumentCreation
        advancedDataStore.autoIncludeDocuments = prompt.autoIncludeDocuments
        advancedDataStore.autoIncludeCommentaries = prompt.autoIncludeCommentaries

        // Preferences may have already been bound with default data store values before
        // loadPrompt() finished — refresh them so the UI reflects the loaded prompt.
        advancedFragment?.refreshFromDataStore()
    }

    private fun updateBibleOnlyDependentState() {
        val bibleOnly = binding.checkBibleOnly.isChecked
        binding.checkWorkspaceMenu.isEnabled = !bibleOnly
        binding.checkNoteEditor.isEnabled = !bibleOnly
        if (bibleOnly) {
            binding.checkWorkspaceMenu.isChecked = false
            binding.checkNoteEditor.isChecked = false
        }
    }

    /** Hide or show the Permissions tab and irrelevant Advanced settings based on text transformation mode. */
    private fun updateTextTransformationDependentState() {
        val isTextTransformation = binding.checkTextTransformation.isChecked

        // Remove Permissions tab when text transformation is enabled
        if (isTextTransformation && binding.tabLayout.tabCount > 2) {
            binding.tabLayout.removeTabAt(1)
        } else if (!isTextTransformation && binding.tabLayout.tabCount < 3) {
            binding.tabLayout.addTab(
                binding.tabLayout.newTab().setText(R.string.prompt_tab_permissions), 1
            )
        }

        // Hide irrelevant Advanced settings
        advancedFragment?.setTextTransformationMode(isTextTransformation)
    }

    private fun collectShowIn(): Set<PromptContext> = binding.run {
        val contexts = mutableSetOf<PromptContext>()
        if (checkVerseSelection.isChecked) contexts.add(PromptContext.VERSE_SELECTION)
        if (checkTextSelection.isChecked) contexts.add(PromptContext.TEXT_SELECTION)
        if (checkWindowMenu.isChecked) contexts.add(PromptContext.WINDOW_MENU)
        if (checkWorkspaceMenu.isChecked) contexts.add(PromptContext.WORKSPACE_MENU)
        if (checkNoteEditor.isChecked) contexts.add(PromptContext.NOTE_EDITOR)
        contexts
    }

    private val currentToolAllowed: Set<AgentTool>?
        get() {
            val allowed = listBuilder.collectAllowed()
            val denied = listBuilder.collectDenied()
            return if (allowed.isNotEmpty() || denied.isNotEmpty()) allowed else null
        }

    private val currentToolDenied: Set<AgentTool>?
        get() {
            val allowed = listBuilder.collectAllowed()
            val denied = listBuilder.collectDenied()
            return if (allowed.isNotEmpty() || denied.isNotEmpty()) denied else null
        }

    private fun captureInitialState() {
        binding.apply {
            initialName = promptName.text.toString()
            initialDescription = promptDescription.text.toString()
            initialTemplate = promptTemplate.text.toString()
            initialShowIn = collectShowIn()
            initialPermissionModeIndex = permissionModeSpinner.selectedItemPosition
            initialAllowedTools = currentToolAllowed
            initialDeniedTools = currentToolDenied
            initialIsTextTransformation = checkTextTransformation.isChecked
            initialCategoryPosition = categorySpinner.selectedItemPosition
        }
        initialAdvancedSnapshot = advancedDataStore.snapshot()
    }

    private fun isDirty(): Boolean {
        if (isBuiltIn) return advancedDataStore.snapshot() != initialAdvancedSnapshot
        if (isReadOnly) return false
        val basicDirty = binding.run {
            promptName.text.toString() != initialName ||
                promptDescription.text.toString() != initialDescription ||
                promptTemplate.text.toString() != initialTemplate ||
                collectShowIn() != initialShowIn ||
                permissionModeSpinner.selectedItemPosition != initialPermissionModeIndex ||
                currentToolAllowed != initialAllowedTools ||
                currentToolDenied != initialDeniedTools ||
                checkTextTransformation.isChecked != initialIsTextTransformation ||
                categorySpinner.selectedItemPosition != initialCategoryPosition
        }
        return basicDirty || advancedDataStore.snapshot() != initialAdvancedSnapshot
    }

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

    /** Save only the overridable fields for a built-in prompt. */
    private fun saveBuiltinOverride() {
        val promptId = prompt?.id ?: return
        val modelId = advancedDataStore.modelOverrideId
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val override = BuiltinPromptOverride(id = promptId, configuredModelId = modelId)
                DatabaseContainer.instance.aiSettingsDb.builtinPromptOverrideDao().upsert(override)
            }
            finish()
        }
    }

    private fun validateAndSave() {
        if (isBuiltIn) {
            saveBuiltinOverride()
            return
        }
        if (isReadOnly) return

        val name = binding.promptName.text.toString().trim()
        val template = binding.promptTemplate.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.prompt_name_required, Toast.LENGTH_SHORT).show()
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
            binding.promptName.requestFocus()
            return
        }

        if (template.isEmpty()) {
            Toast.makeText(this, R.string.prompt_template_required, Toast.LENGTH_SHORT).show()
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
            binding.promptTemplate.requestFocus()
            return
        }

        val description = binding.promptDescription.text.toString().trim().takeIf { it.isNotEmpty() }
        val showIn = collectShowIn()
        val selectedPermissionMode = permissionModeValues[binding.permissionModeSpinner.selectedItemPosition]
        val allowedTools = currentToolAllowed
        val deniedTools = currentToolDenied
        val specifyBeforeRun = advancedDataStore.specifyBeforeRun
        val noDocumentCreation = advancedDataStore.noDocumentCreation
        val autoIncludeDocuments = advancedDataStore.autoIncludeDocuments
        val autoIncludeCommentaries = advancedDataStore.autoIncludeCommentaries
        val selectedCategoryId = getSelectedCategoryId()

        // Read Advanced settings from the data store
        val strictContextMatching = advancedDataStore.strictContextMatching
        val maxIterations = advancedDataStore.maxIterations
        val selectedConfiguredModelId = advancedDataStore.modelOverrideId

        lifecycleScope.launch {
            var savedPromptId: IdType? = null
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
                        configuredModelId = selectedConfiguredModelId,
                        specifyBeforeRun = specifyBeforeRun,
                        noDocumentCreation = noDocumentCreation,
                        maxIterations = maxIterations,
                        autoIncludeDocuments = autoIncludeDocuments,
                        autoIncludeCommentaries = autoIncludeCommentaries,
                        bibleOnly = binding.checkBibleOnly.isChecked,
                        isTextTransformation = binding.checkTextTransformation.isChecked,
                        categoryId = selectedCategoryId,
                    )
                    PromptRepository.insertPrompt(newPrompt)
                    savedPromptId = newPrompt.id
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
                        it.configuredModelId = selectedConfiguredModelId
                        it.specifyBeforeRun = specifyBeforeRun
                        it.noDocumentCreation = noDocumentCreation
                        it.maxIterations = maxIterations
                        it.autoIncludeDocuments = autoIncludeDocuments
                        it.autoIncludeCommentaries = autoIncludeCommentaries
                        it.bibleOnly = binding.checkBibleOnly.isChecked
                        it.isTextTransformation = binding.checkTextTransformation.isChecked
                        it.categoryId = selectedCategoryId
                        PromptRepository.updatePrompt(it)
                        savedPromptId = it.id
                    }
                }
            }

            if (intent.getBooleanExtra(EXTRA_EXECUTE_AFTER_SAVE, false) && savedPromptId != null) {
                setResult(RESULT_OK, Intent().putExtra(RESULT_PROMPT_ID, savedPromptId.toString()))
            }
            finish()
        }
    }

    private fun deletePrompt() {
        if (isReadOnly) return
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
        if (isReadOnly) {
            menu.findItem(R.id.save_prompt)?.isVisible = isBuiltIn
            menu.findItem(R.id.delete_prompt)?.isVisible = false
            menu.findItem(R.id.copy_to_customize)?.isVisible = true
        } else {
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
                CommonUtils.showHelpDialog(
                    activity = this,
                    titleResId = R.string.help,
                    messageResId = R.string.help_prompt_edit_text,
                    helpPath = "ai.html#custom-prompts",
                )
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
}

/**
 * In-memory data store for the Advanced tab preferences.
 * Values are read/written here and collected by the Activity on save.
 */
class AdvancedDataStore : PreferenceDataStore() {

    var modelOverrideId: IdType? = null
    var strictContextMatching: Boolean = true
    var maxIterations: Int? = null
    var specifyBeforeRun: Boolean = false
    var noDocumentCreation: Boolean = false
    var autoIncludeDocuments: Boolean = false
    var autoIncludeCommentaries: Boolean = false

    /** Serialized model ID for the ListPreference ("" = default/null) */
    private var modelOverrideValue: String
        get() = modelOverrideId?.toString() ?: ""
        set(value) { modelOverrideId = value.takeIf { it.isNotEmpty() }?.let { IdType.fromString(it) } }

    /** Serialized max iterations for the EditTextPreference ("" = null/global default) */
    private var maxIterationsValue: String
        get() = maxIterations?.toString() ?: ""
        set(value) { maxIterations = value.trim().toIntOrNull() }

    override fun putString(key: String, value: String?) {
        when (key) {
            "model_override" -> modelOverrideValue = value ?: ""
            "max_iterations" -> maxIterationsValue = value ?: ""
        }
    }

    override fun getString(key: String, defValue: String?): String? = when (key) {
        "model_override" -> modelOverrideValue
        "max_iterations" -> maxIterationsValue
        else -> defValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        when (key) {
            "strict_context_matching" -> strictContextMatching = value
            "specify_before_run" -> specifyBeforeRun = value
            "no_document_creation" -> noDocumentCreation = value
            "auto_include_documents" -> autoIncludeDocuments = value
            "auto_include_commentaries" -> autoIncludeCommentaries = value
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
        "strict_context_matching" -> strictContextMatching
        "specify_before_run" -> specifyBeforeRun
        "no_document_creation" -> noDocumentCreation
        "auto_include_documents" -> autoIncludeDocuments
        "auto_include_commentaries" -> autoIncludeCommentaries
        else -> defValue
    }

    data class Snapshot(
        val modelOverrideId: IdType? = null,
        val strictContextMatching: Boolean = true,
        val maxIterations: Int? = null,
        val specifyBeforeRun: Boolean = false,
        val noDocumentCreation: Boolean = false,
        val autoIncludeDocuments: Boolean = false,
        val autoIncludeCommentaries: Boolean = false,
    )

    fun snapshot() = Snapshot(
        modelOverrideId, strictContextMatching, maxIterations,
        specifyBeforeRun, noDocumentCreation, autoIncludeDocuments, autoIncludeCommentaries,
    )
}

/**
 * PreferenceFragment for the Advanced tab of prompt editing.
 * Uses [AdvancedDataStore] for in-memory storage instead of SharedPreferences.
 */
class PromptAdvancedSettingsFragment : PreferenceFragmentCompat() {

    private val activity get() = requireActivity() as PromptEditActivity
    private val dataStore get() = activity.advancedDataStore

    private val configuredModelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
    private val providerConfigDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = dataStore
        setPreferencesFromResource(R.xml.prompt_advanced_settings, rootKey)

        setupModelPreference()
        setupMaxIterationsPreference()
        updateModelSummary()
    }

    private fun setupModelPreference() {
        val pref = findPreference<ListPreference>("model_override") ?: return

        val defaultModelId = AiSettings.defaultModelId
        val models = configuredModelDao.all().sortedByDescending { it.id == defaultModelId }
        val providerConfigs = providerConfigDao.all().associateBy { it.id }

        val displayEntries = mutableListOf<String>()
        val entryValues = mutableListOf<String>()

        // First entry: "Default"
        val defaultModel = defaultModelId?.let { id -> configuredModelDao.getById(id) }
        val defaultProvider = defaultModel?.let { m -> providerConfigs[m.providerConfigId] }
        val defaultSuffix = if (defaultModel != null && defaultProvider != null) {
            " (${defaultModel.modelId} — ${defaultProvider.displayName})"
        } else ""
        displayEntries.add(getString(R.string.prompt_model_default) + defaultSuffix)
        entryValues.add("")  // "" = null/default

        for (model in models) {
            val provider = providerConfigs[model.providerConfigId]
            val providerName = provider?.displayName ?: "?"
            val pricing = ModelPricing(model.inputPricePerMillion, model.outputPricePerMillion,
                model.cacheCreationPricePerMillion, model.cacheReadPricePerMillion)
            val priceStr = if (pricing.inputPerMillion > 0 || pricing.outputPerMillion > 0) {
                " (${LlmCostTracker.formatPriceCompact(pricing.inputPerMillion)}/${LlmCostTracker.formatPriceCompact(pricing.outputPerMillion)})"
            } else ""
            val prefix = if (LlmProvider.isModelSupported(model.modelId)) "✓ " else ""
            displayEntries.add("$prefix${model.modelId} — $providerName$priceStr")
            entryValues.add(model.id.toString())
        }

        pref.entries = displayEntries.toTypedArray()
        pref.entryValues = entryValues.toTypedArray()
        pref.value = dataStore.modelOverrideId?.toString() ?: ""

        pref.setOnPreferenceChangeListener { _, newValue ->
            val selectedValue = newValue as? String ?: ""
            val idx = entryValues.indexOf(selectedValue).coerceAtLeast(0)
            pref.summary = displayEntries[idx]
            true
        }
    }

    private fun setupMaxIterationsPreference() {
        val pref = findPreference<EditTextPreference>("max_iterations") ?: return
        pref.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.hint = getString(R.string.prompt_max_iterations_hint)
        }
        updateMaxIterationsSummary()
        pref.setOnPreferenceChangeListener { _, newValue ->
            val intVal = (newValue as? String)?.trim()?.toIntOrNull()
            pref.summary = getString(R.string.prompt_max_iterations_summary_with_value, formatMaxIterationsValue(intVal))
            true
        }
    }

    private fun updateModelSummary() {
        val pref = findPreference<ListPreference>("model_override") ?: return
        val entry = pref.entry
        pref.summary = entry ?: getString(R.string.prompt_model_default)
    }

    private fun formatMaxIterationsValue(value: Int?): String {
        val globalDefault = CommonUtils.aiSettings.maxIterations
        return when {
            value == null -> getString(R.string.prompt_max_iterations_global_default, globalDefault)
            value == 0 -> getString(R.string.prompt_max_iterations_unlimited)
            else -> value.toString()
        }
    }

    private fun updateMaxIterationsSummary() {
        val pref = findPreference<EditTextPreference>("max_iterations") ?: return
        val value = dataStore.maxIterations
        pref.summary = getString(R.string.prompt_max_iterations_summary_with_value, formatMaxIterationsValue(value))
    }

    /**
     * Refresh all preference UI from the current data store values.
     *
     * The fragment binds preferences during [onCreatePreferences], at which point the data
     * store may still contain default values because [PromptEditActivity.loadPrompt] is async.
     * Once the prompt is loaded and the data store is populated, the preference views must be
     * refreshed explicitly — the data store does not notify preferences of external changes.
     */
    fun refreshFromDataStore() {
        findPreference<ListPreference>("model_override")?.value = dataStore.modelOverrideId?.toString() ?: ""
        findPreference<SwitchPreference>("strict_context_matching")?.isChecked = dataStore.strictContextMatching
        findPreference<EditTextPreference>("max_iterations")?.text = dataStore.maxIterations?.toString() ?: ""
        findPreference<SwitchPreference>("specify_before_run")?.isChecked = dataStore.specifyBeforeRun
        findPreference<SwitchPreference>("no_document_creation")?.isChecked = dataStore.noDocumentCreation
        findPreference<SwitchPreference>("auto_include_documents")?.isChecked = dataStore.autoIncludeDocuments
        findPreference<SwitchPreference>("auto_include_commentaries")?.isChecked = dataStore.autoIncludeCommentaries
        updateModelSummary()
        updateMaxIterationsSummary()
    }

    fun setReadOnly(keepModelEditable: Boolean = false) {
        if (!keepModelEditable) {
            findPreference<ListPreference>("model_override")?.isEnabled = false
        }
        findPreference<SwitchPreference>("strict_context_matching")?.isEnabled = false
        findPreference<EditTextPreference>("max_iterations")?.isEnabled = false
        findPreference<SwitchPreference>("specify_before_run")?.isEnabled = false
        findPreference<SwitchPreference>("no_document_creation")?.isEnabled = false
        findPreference<SwitchPreference>("auto_include_documents")?.isEnabled = false
        findPreference<SwitchPreference>("auto_include_commentaries")?.isEnabled = false
    }

    /** Hide preferences that are irrelevant for text transformation prompts. */
    fun setTextTransformationMode(isTextTransformation: Boolean) {
        findPreference<EditTextPreference>("max_iterations")?.isVisible = !isTextTransformation
        findPreference<SwitchPreference>("no_document_creation")?.isVisible = !isTextTransformation
        findPreference<SwitchPreference>("auto_include_documents")?.isVisible = !isTextTransformation
        findPreference<SwitchPreference>("auto_include_commentaries")?.isVisible = !isTextTransformation
    }
}
