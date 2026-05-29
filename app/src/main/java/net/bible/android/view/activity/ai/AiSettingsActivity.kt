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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.EditText
import android.widget.ExpandableListView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ManagePromptsBinding
import net.bible.android.activity.databinding.ManagePromptsCategoryHeaderBinding
import net.bible.android.activity.databinding.ManagePromptsListItemBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.service.common.AndBibleAddons
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.GlobalAiSettings
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.PromptCategory
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

    /** Groups for ExpandableListView: category (null = uncategorized) + its prompts */
    data class PromptGroup(val category: PromptCategory?, val prompts: List<AgentPrompt>)

    private var groups = mutableListOf<PromptGroup>()
    private var favoriteIds = emptySet<IdType>()
    private lateinit var binding: ManagePromptsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ManagePromptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildActivityComponent().inject(this)

        title = getString(R.string.ai_settings)

        binding.configureConnectionButton.setOnClickListener {
            startActivity(Intent(this, AiConnectionSettingsActivity::class.java))
        }

        binding.list.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            editPrompt(groups[groupPosition].prompts[childPosition])
            true
        }
        binding.list.setOnItemLongClickListener { _, _, flatPosition, _ ->
            val packed = (binding.list as ExpandableListView).getExpandableListPosition(flatPosition)
            val type = ExpandableListView.getPackedPositionType(packed)
            val groupPos = ExpandableListView.getPackedPositionGroup(packed)
            val childPos = ExpandableListView.getPackedPositionChild(packed)
            when (type) {
                ExpandableListView.PACKED_POSITION_TYPE_GROUP -> {
                    val group = groups.getOrNull(groupPos)
                    val cat = group?.category
                    if (cat != null && cat.id != PromptRepository.FAVORITES_CATEGORY_ID) showCategoryContextMenu(cat)
                    true
                }
                ExpandableListView.PACKED_POSITION_TYPE_CHILD -> {
                    val prompt = groups.getOrNull(groupPos)?.prompts?.getOrNull(childPos)
                    if (prompt != null) showPromptContextMenu(prompt)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateView()
    }

    private fun updateView() {
        if (CommonUtils.settings.llmConfigured) {
            binding.viewFlipper.displayedChild = 1
            loadPrompts()
        } else {
            binding.viewFlipper.displayedChild = 0
        }
        invalidateOptionsMenu()
    }

    private fun loadPrompts() {
        lifecycleScope.launch {
            val (loadedGroups, loadedFavoriteIds) = withContext(Dispatchers.IO) {
                val allPrompts = PromptRepository.allPromptsIncludingHidden()
                val hidden = CommonUtils.aiSettings.hiddenBuiltInPrompts
                val visiblePrompts = allPrompts.filter { it.id !in hidden || !BuiltInPrompts.isBuiltIn(it.id) }

                val grouped = visiblePrompts.groupBy { PromptRepository.getCategoryForPrompt(it) }

                val result = mutableListOf<PromptGroup>()

                // Virtual "Favorites" group first
                val favIds = PromptRepository.favoritePromptIds()
                val favoritePrompts = visiblePrompts.filter { it.id in favIds }
                if (favoritePrompts.isNotEmpty()) {
                    result.add(PromptGroup(PromptCategory(id = PromptRepository.FAVORITES_CATEGORY_ID), favoritePrompts))
                }

                // Uncategorized
                grouped[null]?.let { result.add(PromptGroup(null, it)) }
                // Then all categories in order (built-in + user), including empty user categories
                for (cat in PromptRepository.allCategories().sortedBy { it.orderNumber }) {
                    val catPrompts = grouped[cat]
                    if (catPrompts != null) {
                        result.add(PromptGroup(cat, catPrompts))
                    } else if (!BuiltInPrompts.isBuiltInCategory(cat.id)) {
                        // Show empty user categories so user can manage them
                        result.add(PromptGroup(cat, emptyList()))
                    }
                }
                Pair(result, favIds)
            }

            groups.clear()
            groups.addAll(loadedGroups)
            favoriteIds = loadedFavoriteIds

            // Save scroll position before replacing adapter
            val firstVisible = binding.list.firstVisiblePosition
            val topOffset = binding.list.getChildAt(0)?.top ?: 0

            val adapter = PromptExpandableAdapter()
            binding.list.setAdapter(adapter)
            // Expand all groups by default, except hidden categories
            for (i in groups.indices) {
                val cat = groups[i].category
                if (cat == null || !PromptRepository.isCategoryHidden(cat)) {
                    binding.list.expandGroup(i)
                }
            }

            // Restore scroll position
            binding.list.setSelectionFromTop(firstVisible, topOffset)

            // Show empty view only when there are no prompts at all
            val totalPrompts = groups.sumOf { it.prompts.size }
            binding.empty.visibility = if (totalPrompts == 0) View.VISIBLE else View.GONE
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
        menu.findItem(R.id.new_category)?.isVisible = configured
        menu.findItem(R.id.ai_connection_settings)?.isVisible = configured
        menu.findItem(R.id.reset_all_ai_settings)?.isVisible = configured && CommonUtils.isDebugMode
        menu.findItem(R.id.export_prompts_csv)?.isVisible = configured
        menu.findItem(R.id.import_prompts_csv)?.isVisible = configured
        menu.findItem(R.id.restore_hidden_prompts)?.isVisible =
            configured && CommonUtils.aiSettings.hiddenBuiltInPrompts.isNotEmpty()
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
            R.id.new_category -> {
                showNewCategoryDialog()
                true
            }
            R.id.ai_connection_settings -> {
                startActivity(Intent(this, AiConnectionSettingsActivity::class.java))
                true
            }
            R.id.restore_hidden_prompts -> {
                restoreHiddenPrompts()
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
            R.id.show_help -> {
                CommonUtils.showHelpDialog(
                    activity = this,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_settings_text,
                    helpPath = "ai.html",
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
                for (config in providerDao.all()) {
                    config.removeApiKey()
                    LlmCostTracker.reset(config.id)
                }
                providerDao.deleteAll()

                // Reset global AI settings (disclaimer, permissions, language, etc.)
                val globalDao = DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao()
                globalDao.set(GlobalAiSettings())

                PromptRepository.deleteAllUserPrompts()
                PromptRepository.deleteAllUserCategories()
            }
            ABEventBus.post(AppSettingsUpdated())
            updateView()
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
                getString(R.string.csv_export_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importPrompts() {
        val options = arrayOf(
            getString(R.string.import_prompts_editable),
            getString(R.string.import_prompts_addon),
        )
        val installAsAddon = suspendCancellableCoroutine<Boolean?> { cont ->
            AlertDialog.Builder(this)
                .setTitle(R.string.import_prompts_csv)
                .setItems(options) { _, which -> cont.resume(which == 1) }
                .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        } ?: return

        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    if (installAsAddon) {
                        installCsvAsAddon(uri)
                    } else {
                        importCsvAsEditable(uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing prompts from CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.csv_import_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importCsvAsEditable(uri: Uri) {
        val importResult = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                PromptCsvUtils.importPromptsFromCsv(inputStream)
            } ?: throw IllegalArgumentException("Could not open input stream")
        }

        if (importResult.errors > 0) {
            val message =
                getString(R.string.csv_import_errors, importResult.created, importResult.updated, importResult.errors) +
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
                getString(R.string.csv_import_success, importResult.created, importResult.updated),
                Toast.LENGTH_SHORT
            ).show()
        }

        loadPrompts()
    }

    private suspend fun installCsvAsAddon(uri: Uri) {
        val displayName = contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        } ?: "prompts.csv"

        withContext(Dispatchers.IO) {
            val outDir = File(SharedConstants.modulesDir, "prompts")
            outDir.mkdirs()
            val outFile = File(outDir, displayName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            addCsvPromptBook(outFile)
            AndBibleAddons.clearCaches()
        }

        PromptRepository.clearAddonCache()
        Toast.makeText(this, R.string.install_zip_successfull, Toast.LENGTH_SHORT).show()
        loadPrompts()
    }

    private fun showNewCategoryDialog() {
        val editText = EditText(this).apply { hint = getString(R.string.new_category_name) }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_category)
            .setView(editText)
            .setPositiveButton(R.string.okay) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            PromptRepository.insertCategory(PromptCategory(name = name))
                        }
                        loadPrompts()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCategoryContextMenu(category: PromptCategory) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        val isBuiltInCat = BuiltInPrompts.isBuiltInCategory(category.id)

        if (!isBuiltInCat) {
            val categoryGroups = groups.filter { it.category != null }
            val categoryIndex = categoryGroups.indexOfFirst { it.category?.id == category.id }

            if (categoryIndex > 0) {
                items.add(getString(R.string.move_category_up) to {
                    swapCategoryOrder(category, categoryGroups[categoryIndex - 1].category!!)
                })
            }
            if (categoryIndex in 0 until categoryGroups.size - 1) {
                items.add(getString(R.string.move_category_down) to {
                    swapCategoryOrder(category, categoryGroups[categoryIndex + 1].category!!)
                })
            }
        }

        val isHidden = PromptRepository.isCategoryHidden(category)
        items.add(getString(if (isHidden) R.string.show_category else R.string.hide_category) to {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isBuiltInCat) {
                        val current = CommonUtils.aiSettings.hiddenBuiltInCategories
                        CommonUtils.aiSettings.hiddenBuiltInCategories =
                            if (isHidden) current - category.id else current + category.id
                    } else {
                        PromptRepository.updateCategory(category.copy(hidden = !category.hidden))
                    }
                }
                loadPrompts()
            }
        })

        if (isBuiltInCat) {
            // Built-in categories only support hide/show
            AlertDialog.Builder(this)
                .setTitle(category.name)
                .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
                .show()
            return
        }

        items.add(getString(R.string.rename) to {
            val editText = EditText(this).apply { setText(category.name) }
            AlertDialog.Builder(this)
                .setTitle(R.string.rename)
                .setView(editText)
                .setPositiveButton(R.string.okay) { _, _ ->
                    val newName = editText.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                PromptRepository.updateCategory(category.copy(name = newName))
                            }
                            loadPrompts()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        })

        items.add(getString(R.string.delete_category) to {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_category_confirm_title, category.name))
                .setItems(arrayOf(
                    getString(R.string.delete_category_keep_prompts),
                    getString(R.string.delete_category_and_prompts),
                )) { _, which ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            PromptRepository.deleteCategory(category.id, deletePrompts = which == 1)
                        }
                        loadPrompts()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        })

        AlertDialog.Builder(this)
            .setTitle(category.name)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun swapPromptOrder(a: AgentPrompt, b: AgentPrompt) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val aNew = a.copy(orderNumber = b.orderNumber)
                val bNew = b.copy(orderNumber = a.orderNumber)
                PromptRepository.updatePrompt(aNew)
                PromptRepository.updatePrompt(bNew)
            }
            loadPrompts()
        }
    }

    private fun swapCategoryOrder(a: PromptCategory, b: PromptCategory) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val tmpOrder = a.orderNumber
                PromptRepository.updateCategory(a.copy(orderNumber = b.orderNumber))
                PromptRepository.updateCategory(b.copy(orderNumber = tmpOrder))
            }
            loadPrompts()
        }
    }

    private fun showPromptContextMenu(prompt: AgentPrompt) {
        val isBuiltIn = BuiltInPrompts.isBuiltIn(prompt.id)
        val isReadOnly = PromptRepository.isReadOnly(prompt.id)
        val items = mutableListOf<Pair<String, () -> Unit>>()

        if (isBuiltIn) {
            items.add(getString(R.string.ai_hide_prompt) to {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        PromptRepository.setBuiltInPromptHidden(prompt.id, true)
                    }
                    Toast.makeText(this@AiSettingsActivity, R.string.ai_prompt_hidden, Toast.LENGTH_SHORT).show()
                    loadPrompts()
                }
            })
        }

        items.add(getString(R.string.copy) to {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    PromptRepository.copyPrompt(prompt.id)
                }
                loadPrompts()
            }
        })

        // Move up/down and move to category (only for user prompts)
        if (!isReadOnly) {
            val siblings = groups.find { it.prompts.contains(prompt) }?.prompts
                ?.filter { !PromptRepository.isReadOnly(it.id) } ?: emptyList()
            val promptIndex = siblings.indexOf(prompt)

            if (promptIndex > 0) {
                items.add(getString(R.string.move_category_up) to {
                    swapPromptOrder(prompt, siblings[promptIndex - 1])
                })
            }
            if (promptIndex in 0 until siblings.size - 1) {
                items.add(getString(R.string.move_category_down) to {
                    swapPromptOrder(prompt, siblings[promptIndex + 1])
                })
            }

            items.add(getString(R.string.move_to_category) to {
                showMoveToCategoryDialog(prompt)
            })
        }

        if (!isReadOnly) {
            items.add(getString(R.string.delete) to {
                AlertDialog.Builder(this)
                    .setTitle(prompt.name)
                    .setMessage(R.string.delete_prompt_confirm_message)
                    .setPositiveButton(R.string.okay) { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                PromptRepository.deletePrompt(prompt)
                            }
                            loadPrompts()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            })
        }

        val names = items.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(prompt.name)
            .setItems(names) { _, which -> items[which].second() }
            .show()
    }

    private fun showMoveToCategoryDialog(prompt: AgentPrompt) {
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { PromptRepository.allCategories() }
            val options = listOf(getString(R.string.category_none)) + categories.map { it.name }
            val categoryIds: List<IdType?> = listOf(null) + categories.map { it.id }

            AlertDialog.Builder(this@AiSettingsActivity)
                .setTitle(R.string.move_to_category)
                .setItems(options.toTypedArray()) { _, which ->
                    val targetCategoryId = categoryIds[which]
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            prompt.categoryId = targetCategoryId
                            PromptRepository.updatePrompt(prompt)
                        }
                        loadPrompts()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun restoreHiddenPrompts() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                CommonUtils.aiSettings.hiddenBuiltInPrompts = emptySet()
            }
            Toast.makeText(this@AiSettingsActivity, R.string.ai_prompts_restored, Toast.LENGTH_SHORT).show()
            loadPrompts()
        }
    }

    inner class PromptExpandableAdapter : BaseExpandableListAdapter() {
        override fun getGroupCount(): Int = groups.size
        override fun getChildrenCount(groupPosition: Int): Int = groups[groupPosition].prompts.size
        override fun getGroup(groupPosition: Int): PromptGroup = groups[groupPosition]
        override fun getChild(groupPosition: Int, childPosition: Int): AgentPrompt =
            groups[groupPosition].prompts[childPosition]
        override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()
        override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()
        override fun hasStableIds(): Boolean = false
        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

        override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
            val headerBinding = if (convertView != null) {
                ManagePromptsCategoryHeaderBinding.bind(convertView)
            } else {
                ManagePromptsCategoryHeaderBinding.inflate(layoutInflater, parent, false)
            }

            val group = groups[groupPosition]
            val isFavorites = group.category?.id == PromptRepository.FAVORITES_CATEGORY_ID
            val isHidden = !isFavorites && group.category?.let { PromptRepository.isCategoryHidden(it) } == true
            val categoryName = when {
                isFavorites -> getString(R.string.prompt_category_favorites)
                group.category != null -> group.category.name
                else -> getString(R.string.prompt_category_uncategorized)
            }
            headerBinding.categoryName.text = if (isHidden) "$categoryName (${getString(R.string.hidden)})" else categoryName
            headerBinding.categoryName.alpha = if (isHidden) 0.5f else 1.0f
            headerBinding.promptCount.text = group.prompts.size.toString()
            headerBinding.promptCount.alpha = if (isHidden) 0.5f else 1.0f
            headerBinding.expandIndicator.setImageResource(
                if (isExpanded) R.drawable.ic_arrow_drop_up_grey_24dp
                else R.drawable.ic_arrow_drop_down_grey_24dp
            )
            headerBinding.expandIndicator.alpha = if (isHidden) 0.5f else 1.0f

            return headerBinding.root
        }

        override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup): View {
            val itemBinding = if (convertView != null) {
                ManagePromptsListItemBinding.bind(convertView)
            } else {
                ManagePromptsListItemBinding.inflate(layoutInflater, parent, false)
            }

            val prompt = groups[groupPosition].prompts[childPosition]
            val isBuiltIn = BuiltInPrompts.isBuiltIn(prompt.id)
            val isAddon = prompt.sourceModule != null

            itemBinding.promptName.text = prompt.name
            itemBinding.promptDescription.text = prompt.description ?: ""
            itemBinding.promptDescription.visibility = if (prompt.description.isNullOrEmpty()) View.GONE else View.VISIBLE
            itemBinding.builtInBadge.visibility = if (isBuiltIn) View.VISIBLE else View.GONE
            if (isAddon) {
                itemBinding.addonBadge.text = getString(R.string.addon_prompt_badge, prompt.sourceModule)
                itemBinding.addonBadge.visibility = View.VISIBLE
            } else {
                itemBinding.addonBadge.visibility = View.GONE
            }

            val contextNames = prompt.showIn.map { context ->
                when (context) {
                    PromptContext.VERSE_SELECTION -> getString(R.string.prompt_context_verse_selection)
                    PromptContext.TEXT_SELECTION -> getString(R.string.prompt_context_text_selection)
                    PromptContext.WINDOW_MENU -> getString(R.string.prompt_context_window_menu)
                    PromptContext.WORKSPACE_MENU -> getString(R.string.prompt_context_workspace_menu)
                    PromptContext.NOTE_EDITOR -> getString(R.string.prompt_context_note_editor)
                }
            }
            itemBinding.promptContexts.text = contextNames.joinToString(", ")

            itemBinding.favoriteIcon.setImageResource(
                if (prompt.id in favoriteIds) R.drawable.ic_star_filled_24
                else R.drawable.ic_star_outline_24
            )
            itemBinding.favoriteIcon.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { PromptRepository.toggleFavorite(prompt.id) }
                    loadPrompts()
                }
            }

            return itemBinding.root
        }
    }

    companion object {
        private const val TAG = "AiSettingsActivity"
    }
}
