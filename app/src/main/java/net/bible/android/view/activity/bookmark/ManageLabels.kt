/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.bookmark

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextUtils.concat
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ManageLabelsBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.ContentMatch
import net.bible.android.control.bookmark.LabelAddedOrUpdatedEvent
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.WorkspaceEntities
import net.bible.service.db.DatabaseContainer
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.getTintedDrawable
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.htmlToSpan
import net.bible.service.common.labelsAndBookmarksPlaylist
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random.Default.nextInt
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.PopupMenu
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.service.common.CommonUtils.getResourceColor
import net.bible.service.common.displayName
import net.bible.service.db.BookmarksUpdatedViaSyncEvent
import net.bible.service.db.exportStudyPads
import kotlin.collections.ArrayList
import java.util.regex.PatternSyntaxException

private const val TAG = "BookmarkLabels"

val json = Json {
    allowStructuredMapKeys = true
    encodeDefaults = true
}

fun WorkspaceEntities.WorkspaceSettings.updateFrom(resultData: ManageLabels.ManageLabelsData) {
    Log.i("ManageLabels", "WorkspaceEntities.updateRecentLabels")
    autoAssignLabels = resultData.autoAssignLabels
    autoAssignPrimaryLabel = resultData.autoAssignPrimaryLabel
    ABEventBus.post(AppSettingsUpdated())
}

@Serializable
class SearchOption(
    val text: String,
    val isSearchInsideText: Boolean,
) {
    @Transient var button: Button? = null
    val trimmedText = text.trim()
    val displayText: String get() = if(isSearchInsideText) "*$trimmedText*" else "$trimmedText*"
}

/**
 *
 * ManageLabels serves for bookmark label management, selections (in several contexts) and StudyPad selection.
 */
class ManageLabels : ListActivityBase() {
    private lateinit var binding: ManageLabelsBinding
    private val allLabels: MutableList<BookmarkEntities.Label> = ArrayList()
    private val shownLabels: MutableList<Any> = ArrayList()

    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var windowControl: WindowControl

    enum class Mode {STUDYPAD, WORKSPACE, ASSIGN, HIDELABELS}
    enum class SearchMode {NAME_START, NAME_CONTAINS, CONTENT}

    lateinit var data: ManageLabelsData

    private var lastSelectedQuickSearchButton: Button? = null
    private var searchInsideText = false
    private var searchMode = SearchMode.NAME_START
    private var currentSearchJob: Job? = null

    private fun loadFilteringSettings() {
        searchInsideText = CommonUtils.settings.getBoolean("labels_list_filter_searchInsideTextButtonActive", false)
        if (data.mode == Mode.STUDYPAD) {
            val modeOrdinal = CommonUtils.settings.getInt("labels_list_search_mode", SearchMode.NAME_START.ordinal)
            searchMode = SearchMode.entries.getOrElse(modeOrdinal) { SearchMode.NAME_START }
        }
    }

    private fun saveFilteringSettings() {
        CommonUtils.settings.setBoolean("labels_list_filter_searchInsideTextButtonActive", searchInsideText)
        if (data.mode == Mode.STUDYPAD) {
            CommonUtils.settings.setInt("labels_list_search_mode", searchMode.ordinal)
        }
    }

    fun onEventMainThread(e: BookmarksUpdatedViaSyncEvent) {
        if(e.updated.any { it.tableName == "Label" }) {
            recreate()
        }
    }

    override fun onBackPressed() {
        saveAndExit()
    }

    var highlightLabel: BookmarkEntities.Label? = null
    var overriddenLabelIds: Set<IdType> = emptySet()

    private fun showKeyboard() = binding.run {
        editSearchText.requestFocus()
        val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editSearchText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateSearchButtonsProperties(searchOption: SearchOption? = null) {
        setFilterButtonBackground(lastSelectedQuickSearchButton, false)
        setFilterButtonBackground(searchOption?.button, true)

        setSearchInsideTextButtonBackground()
    }

    private fun resetFilter() {
        resetSearchButtonProperties()
        searchText = ""
        updateLabelList(rePopulate = true)
    }

    private fun resetSearchButtonProperties() {
        updateSearchButtonsProperties()
        lastSelectedQuickSearchButton = null
    }

    private fun reBuildQuickSearchButtonList() = binding.run {
        updateSearchButtonsProperties()
    }

    private fun setFilterButtonBackground(button: Button?, isSelected: Boolean) {
        button?: return
        (button.background as GradientDrawable).run {
            setColor(getResourceColor(if (isSelected) R.color.grey_500 else R.color.transparent))
            setStroke(4, getResourceColor(R.color.grey_500))
        }
    }

    private fun setSearchInsideTextButtonBackground() = binding.run {
        val background = searchInsideTextButton.background as GradientDrawable

        if (data.mode == Mode.STUDYPAD) {
            val (text, isActive) = when (searchMode) {
                SearchMode.NAME_START -> getString(R.string.match_start_of_text) to false
                SearchMode.NAME_CONTAINS -> getString(R.string.match_any_text) to true
                SearchMode.CONTENT -> getString(R.string.match_content) to true
            }
            searchInsideTextButton.text = text
            background.setColor(getResourceColor(if (isActive) R.color.blue_200 else R.color.transparent))
        } else {
            // For other modes, use old toggle behavior
            if (searchInsideText) {
                searchInsideTextButton.text = getString(R.string.match_any_text)
                background.setColor(getResourceColor(R.color.blue_200))
            } else {
                searchInsideTextButton.text = getString(R.string.match_start_of_text)
                background.setColor(getResourceColor(R.color.transparent))
            }
        }
    }

    @Serializable
    data class ManageLabelsData(
        val mode: Mode,
        val selectedLabels: MutableSet<IdType> = mutableSetOf(),
        val autoAssignLabels: MutableSet<IdType> = mutableSetOf(),
        val deletedLabels: MutableSet<IdType> = mutableSetOf(),
        val deletedLabelsWithOrphanedBookmarks: MutableSet<IdType> = mutableSetOf(),
        val changedLabels: MutableSet<IdType> = mutableSetOf(),

        var autoAssignPrimaryLabel: IdType? = null,
        var bookmarkPrimaryLabel: IdType? = null,

        val isWindow: Boolean = false,

        var reset: Boolean = false,
    ) {
        val showUnassigned: Boolean get() = setOf(Mode.HIDELABELS, Mode.WORKSPACE).contains(mode)
        val showCheckboxes: Boolean get() = setOf(Mode.HIDELABELS, Mode.ASSIGN).contains(mode)
        val hasResetButton: Boolean get() = setOf(Mode.WORKSPACE, Mode.HIDELABELS).contains(mode)
        val hasReOrderButton: Boolean get() = setOf(Mode.HIDELABELS, Mode.ASSIGN, Mode.WORKSPACE).contains(mode)
        val workspaceEdits: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val primaryShown: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val showActiveCategory: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN, Mode.HIDELABELS).contains(mode)
        val hideCategories: Boolean get() = setOf(Mode.STUDYPAD).contains(mode)

        val contextSelectedItems: MutableSet<IdType> get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignLabels
                else -> selectedLabels
            }

        var contextPrimaryLabel: IdType? get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignPrimaryLabel
                Mode.ASSIGN -> bookmarkPrimaryLabel
                else -> null
            }
            set(value) =
                when (mode) {
                    Mode.WORKSPACE -> autoAssignPrimaryLabel = value
                    Mode.ASSIGN -> bookmarkPrimaryLabel = value
                    else -> {}
                }

        val titleId: Int get() {
            return when(mode) {
                Mode.ASSIGN -> R.string.assign_labels
                Mode.STUDYPAD -> R.string.studypads
                Mode.WORKSPACE -> R.string.labels
                Mode.HIDELABELS -> R.string.bookmark_settings_hide_labels_title
            }
        }

        fun toJSON(): String = json.encodeToString(serializer(), this)
        fun applyFrom(workspaceSettings: WorkspaceEntities.WorkspaceSettings?): ManageLabelsData {
            workspaceSettings?: return this
            autoAssignLabels.addAll(workspaceSettings.autoAssignLabels)
            autoAssignPrimaryLabel = workspaceSettings.autoAssignPrimaryLabel
            return this
        }

        companion object {
            fun fromJSON(str: String): ManageLabelsData = json.decodeFromString(serializer(), str)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ManageLabelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)

        data = ManageLabelsData.fromJSON(intent.getStringExtra("data")!!)

        loadFilteringSettings()

        allLabels.addAll(bookmarkControl.assignableLabels.filter {!it.isUnlabeledLabel})

        val workspaceId = bookmarkControl.windowControl.windowRepository.id
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        overriddenLabelIds = workspaceDao.labelOverrides(workspaceId)
            .filter { it.hasOverride }
            .map { it.labelId }
            .toSet()

        if(data.mode == Mode.STUDYPAD) {
            title = getString(R.string.studypads)
        }

        title = getString(data.titleId)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        updateLabelList(rePopulate = true)

        val key = windowControl.activeWindowPageManager.currentPage.key
        if(key is StudyPadKey) {
            highlightLabel = key.label
        }

        listAdapter = ManageLabelItemAdapter(this, shownLabels, this)

        highlightLabel?.also {
            val pos = shownLabels.indexOf(it)
            listView.setSelection(pos)
        }

        binding.run {
            clearSearchTextButton.setOnClickListener {
                searchText = ""
                updateLabelList(rePopulate = true)
            }

            editSearchText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    updateLabelList(rePopulate = true)
                    resetSearchButtonProperties()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            setSearchInsideTextButtonBackground()
            searchInsideTextButton.setOnClickListener {
                if (data.mode == Mode.STUDYPAD) {
                    // Show popup menu with three search mode options
                    val popup = PopupMenu(this@ManageLabels, it)
                    popup.menuInflater.inflate(R.menu.search_mode_menu, popup.menu)

                    popup.setOnMenuItemClickListener { menuItem ->
                        searchMode = when (menuItem.itemId) {
                            R.id.search_mode_name_start -> SearchMode.NAME_START
                            R.id.search_mode_name_contains -> SearchMode.NAME_CONTAINS
                            R.id.search_mode_content -> SearchMode.CONTENT
                            else -> searchMode
                        }
                        setSearchInsideTextButtonBackground()
                        saveFilteringSettings()
                        updateLabelList(rePopulate = true)
                        true
                    }
                    popup.show()
                } else {
                    // Old toggle behavior for non-STUDYPAD modes
                    searchInsideText = !searchInsideText
                    setSearchInsideTextButtonBackground()
                    updateLabelList(rePopulate = true)
                }
            }
            editSearchText.requestFocus()
        }
        ABEventBus.register(this)
        reBuildQuickSearchButtonList()
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.manage_labels_options_menu, menu)
        menu.findItem(R.id.export_studypads).title = getString(R.string.export_something, getString(R.string.studypads))
        menu.findItem(R.id.import_studypads).title = getString(R.string.import_items, getString(R.string.studypads))
        menu.findItem(R.id.resetButton).isVisible = data.hasResetButton
        menu.findItem(R.id.reOrder).isVisible = data.hasReOrderButton
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.help -> help()
            R.id.newLabel -> newLabel()
            R.id.resetButton -> reset()
            R.id.export_studypads -> {
                lifecycleScope.launch {
                    val labels = Dialogs.multiselect(
                        context = this@ManageLabels,
                        title = getString(R.string.export_something, getString(R.string.studypads)),
                        items = bookmarkControl.assignableLabels,
                        itemToString = { it.displayName }
                    )
                    if (labels.isNotEmpty()) {
                        exportStudyPads(this@ManageLabels, *labels.toTypedArray())
                    }
                }
            }
            R.id.import_studypads -> {
                val intent = Intent(this, InstallZip::class.java)
                lifecycleScope.launch {
                    awaitIntent(intent)
                    updateLabelList(rePopulate = true, reOrder = true)
                }
            }
            R.id.reOrder -> updateLabelList(rePopulate = true, reOrder = true)
            android.R.id.home -> saveAndExit()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    private fun help() {
        when(data.mode) {
            Mode.STUDYPAD -> CommonUtils.showHelp(this, listOf(R.string.studypads))
            Mode.ASSIGN -> help(HelpMode.ASSIGN)
            Mode.WORKSPACE -> help(HelpMode.WORKSPACE)
            Mode.HIDELABELS -> help(HelpMode.HIDE)
        }
    }

    enum class HelpMode {WORKSPACE, ASSIGN, HIDE}

    private fun getIconString(id: Int, iconId: Int): SpannableString {
        val s = getString(id,"__ICON__")
        val start = s.indexOf("__ICON__")
        val length = 8
        val icon = ImageSpan(getTintedDrawable(iconId))
        val span = SpannableString(s)
        span.setSpan(icon, start, start + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    private fun help(helpMode: HelpMode) {
        val length = 9

        val videoLink = "<i><a href=\"${labelsAndBookmarksPlaylist}\">${getString(R.string.watch_tutorial_video)}</a></i><br><br>"
        val v = htmlToSpan(videoLink)

        val h1 = when(helpMode) {
            HelpMode.WORKSPACE -> getString(R.string.auto_assing_labels_help1)
            HelpMode.ASSIGN -> getString(R.string.assing_labels_help1)
            HelpMode.HIDE -> getString(R.string.bookmark_settings_hide_labels_summary)
        }

        val h11 =  "\n\n" + getString(R.string.setting_scope, getString(
            if(data.isWindow) R.string.setting_scope_window
            else R.string.setting_scope_workspace)
        )

        val h2 = concat("\n\n", getIconString(R.string.assing_labels_help2, R.drawable.ic_baseline_bookmark_24))
        val text = getString(R.string.assing_labels_help3, "__ICON2__ __ICON3__")

        val start2 = text.indexOf("__ICON2__")
        val start3 = text.indexOf("__ICON3__")
        val h3 = concat("\n\n", SpannableString(text).apply {
            val icon2 = ImageSpan(getTintedDrawable(R.drawable.ic_label_24dp))
            val icon3 = ImageSpan(getTintedDrawable(R.drawable.ic_label_circle))
            setSpan(icon2, start2, start2 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(icon3, start3, start3 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        })

        val h4 = concat("\n\n", getIconString(R.string.assing_labels_help4, R.drawable.ic_baseline_favorite_24))
        val h5 = concat("\n\n", getIconString(R.string.assing_labels_help5, R.drawable.ic_baseline_refresh_24))
        val span = concat(
            v,
            h1,
            if(listOf(HelpMode.HIDE, HelpMode.WORKSPACE).contains(helpMode)) h11 else "",
            *if(helpMode != HelpMode.HIDE) arrayOf(h2, h3, h4) else arrayOf(""),
            h5,
        )

        val title = getString(when(helpMode) {
            HelpMode.ASSIGN -> R.string.assign_labels
            HelpMode.WORKSPACE -> R.string.labels
            HelpMode.HIDE -> R.string.bookmark_settings_hide_labels_title
        })

        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(title)
            .setIcon(R.drawable.ic_logo)
            .setMessage(span)
            .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun studyPadSelected(journal: BookmarkEntities.Label, firstMatch: ContentMatch? = null) {
        Log.i(TAG, "Journal selected:" + journal.name)
        try {
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(
                FakeBookFactory.journalDocument,
                StudyPadKey(journal, entryId = firstMatch?.entryId)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to show journal", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    fun ensureNotAutoAssignPrimaryLabel(label: BookmarkEntities.Label) {
        if (data.autoAssignPrimaryLabel == label.id || data.autoAssignPrimaryLabel == null) {
            data.autoAssignPrimaryLabel = data.autoAssignLabels.toList().firstOrNull()
        }
    }

    fun ensureNotBookmarkPrimaryLabel(label: BookmarkEntities.Label) {
        if (data.bookmarkPrimaryLabel == label.id || data.bookmarkPrimaryLabel == null) {
            data.bookmarkPrimaryLabel = data.selectedLabels.toList().firstOrNull()
        }
    }

    private fun randomColor(): Int = Color.argb(255, nextInt(0, 255), nextInt(0, 255), nextInt(0, 255))

    private var newLabelCount = 1L

    private fun newLabel() {
        Log.i(TAG, "newLabel")
        val newLabel = BookmarkEntities.Label(new = true)
        newLabel.color = randomColor()
        editLabel(newLabel, suggestedName = searchText.takeIf { it.isNotBlank() })
    }

    private fun deleteLabel(label: BookmarkEntities.Label, deleteOrphanedBookmarks: Boolean = false) {
        Log.i(TAG, "deleteLabel")
        data.deletedLabels.add(label.id)
        if (deleteOrphanedBookmarks) {
            data.deletedLabelsWithOrphanedBookmarks.add(label.id)
        }
        data.selectedLabels.remove(label.id)
        data.autoAssignLabels.remove(label.id)
        data.changedLabels.remove(label.id)
        allLabels.myRemoveIf { it.id == label.id }

        ensureNotBookmarkPrimaryLabel(label)
        ensureNotAutoAssignPrimaryLabel(label)
    }

    fun editLabel(label_: BookmarkEntities.Label, suggestedName: String? = null) {
        var label = label_
        val isNew = label.new
        Log.i(TAG, "editLabel isNew: $isNew")
        val intent = Intent(this, LabelEditActivity::class.java)

        val workspaceId = bookmarkControl.windowControl.windowRepository.id
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        val existingOverrides = if (!isNew) workspaceDao.labelOverrides(workspaceId) else emptyList()
        val existingOverride = existingOverrides.find { it.labelId == label.id }
        val workspaceOverride = existingOverride ?: WorkspaceEntities.WorkspaceLabelOverride(
            workspaceId = workspaceId,
            labelId = label.id,
        )

        val labelData = LabelEditActivity.LabelData(
            isAssigning = data.mode == Mode.ASSIGN,
            label = label,
            isAutoAssign = data.autoAssignLabels.contains(label.id),
            isAutoAssignPrimary = data.autoAssignPrimaryLabel == label.id,
            isThisBookmarkPrimary = data.bookmarkPrimaryLabel == label.id,
            isThisBookmarkSelected = data.selectedLabels.contains(label.id),
            suggestedName = suggestedName,
            workspaceOverride = workspaceOverride,
            hasWorkspaceContext = true,
        )
        if(isNew) {
            if(data.mode == Mode.ASSIGN) {
                labelData.isThisBookmarkSelected = true
                labelData.isThisBookmarkPrimary = true
            } else if(data.mode == Mode.WORKSPACE) {
                labelData.isAutoAssignPrimary = true
                labelData.isAutoAssign = true
            }
        }

        intent.putExtra("data", json.encodeToString(serializer(), labelData))

        lifecycleScope.launch(Dispatchers.Main) {
            Log.i(TAG, "editLabel waiting for results")
            val result = awaitIntent(intent)

            if (result.resultCode != Activity.RESULT_CANCELED) {
                Log.i(TAG, "editLabel result NOT CANCELLED")
                val newLabelData: LabelEditActivity.LabelData = json.decodeFromString(
                    serializer(), result.data?.getStringExtra("data")!!)

                if(newLabelData.label.name.isEmpty() && label.new) {
                    Log.i(TAG, "editLabel name not specified or is new")
                    return@launch
                }

                if (newLabelData.delete) {
                    Log.i(TAG, "editLabel delete specified")
                    deleteLabel(label, newLabelData.deleteOrphanedBookmarks)
                } else {
                    Log.i(TAG, "editLabel delete not specified")
                    allLabels.remove(label)
                    label = newLabelData.label
                    allLabels.add(label)
                    data.changedLabels.add(label.id)

                    if (newLabelData.isAutoAssign) {
                        data.autoAssignLabels.add(label.id)
                    } else {
                        data.autoAssignLabels.remove(label.id)
                    }
                    if (newLabelData.isAutoAssignPrimary) {
                        data.autoAssignPrimaryLabel = label.id
                    } else {
                        ensureNotAutoAssignPrimaryLabel(label)
                    }
                    if (newLabelData.isThisBookmarkPrimary) {
                        data.bookmarkPrimaryLabel = label.id
                    } else {
                        ensureNotBookmarkPrimaryLabel(label)
                    }
                    if(data.mode == Mode.ASSIGN) {
                        if (newLabelData.isThisBookmarkSelected) {
                            data.selectedLabels.add(label.id)
                        } else {
                            data.selectedLabels.remove(label.id)
                        }
                    }

                    // Save workspace override
                    val returnedOverride = newLabelData.workspaceOverride
                    if (returnedOverride != null) {
                        val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
                        if (returnedOverride.hasOverride) {
                            dao.insertOrUpdateLabelOverride(returnedOverride)
                            overriddenLabelIds = overriddenLabelIds + returnedOverride.labelId
                        } else {
                            dao.deleteLabelOverride(returnedOverride.workspaceId, returnedOverride.labelId)
                            overriddenLabelIds = overriddenLabelIds - returnedOverride.labelId
                        }
                        ABEventBus.post(LabelAddedOrUpdatedEvent(label))
                    }
                }
                updateLabelList(rePopulate = true, reOrder = isNew)
                if(isNew) {
                    listView.setSelection(shownLabels.indexOf(label))
                }
            } else {
                Log.i(TAG, "editLabel result CANCELLED")
            }
        }
    }

    // This is called by the listener, which is created and configured by the list adapter
    // It should only be called when the mode is STUDYPAD.  Nonetheless, I retained the mode check from the previous
    // onListItemClick() method as an additional sanity check.
    fun selectStudyPadLabel(selected: BookmarkEntities.Label, firstMatch: ContentMatch? = null) {
        if (data.mode == Mode.STUDYPAD) {
            saveAndExit(selected, firstMatch)
        }
        else {
            Log.e(TAG, "Call to selectStudyPadLabel() is unexpected when mode is not STUDYPAD.  mode=${data.mode}")
        }
    }

    private fun saveAndExit(selected: BookmarkEntities.Label? = null, firstMatch: ContentMatch? = null) = lifecycleScope.launch(Dispatchers.Main) {
        Log.i(TAG, "saveAndExit")
        saveFilteringSettings()

        val deleteLabelIds = data.deletedLabels.toList()
        if(deleteLabelIds.isNotEmpty()) {
            val labelsWithOrphanedBookmarkDeletion = data.deletedLabelsWithOrphanedBookmarks.toList()
            val labelsWithoutOrphanedBookmarkDeletion = deleteLabelIds.filter { !labelsWithOrphanedBookmarkDeletion.contains(it) }
            if (labelsWithoutOrphanedBookmarkDeletion.isNotEmpty()) {
                bookmarkControl.deleteLabels(labelsWithoutOrphanedBookmarkDeletion, deleteOrphanedBookmarks = false)
            }
            if (labelsWithOrphanedBookmarkDeletion.isNotEmpty()) {
                bookmarkControl.deleteLabels(labelsWithOrphanedBookmarkDeletion, deleteOrphanedBookmarks = true)
            }
        }

        val saveLabels = allLabels
            .filter{ data.changedLabels.contains(it.id) && !data.deletedLabels.contains(it.id) }

        val newLabels = saveLabels.filter { it.new }
        val existingLabels = saveLabels.filter { !it.new }

        for (it in newLabels) {
            val oldLabel = it.id
            it.id = bookmarkControl.insertOrUpdateLabel(it).id
            it.new = false
            for(list in listOf(data.selectedLabels, data.autoAssignLabels, data.changedLabels)) {
                if(list.contains(oldLabel)) {
                    list.remove(oldLabel)
                    list.add(it.id)
                }
            }
            if(data.bookmarkPrimaryLabel == oldLabel) {
                data.bookmarkPrimaryLabel = it.id
            }
            if(data.autoAssignPrimaryLabel == oldLabel) {
                data.autoAssignPrimaryLabel = it.id
            }
        }

        for (it in existingLabels) {
            bookmarkControl.insertOrUpdateLabel(it)
        }

        setResult(Activity.RESULT_OK, Intent().apply { putExtra("data", this@ManageLabels.data.toJSON())})

        if(selected != null) {
            studyPadSelected(selected, firstMatch)
        }
        finish()
    }

    private suspend fun askConfirmation(message: String, yesNo: Boolean = false)  = suspendCoroutine {
        AlertDialog.Builder(this).apply {
            setMessage(message)
            setPositiveButton(R.string.yes) { _, _ ->
                it.resume(true)
            }
            if(yesNo) {
                setNegativeButton(R.string.no) { _, _ ->
                    it.resume(false)
                }
                setCancelable(false)
            } else {
                setCancelable(true)
                setOnCancelListener { _ -> it.resume(false) }
                setNegativeButton(R.string.cancel) { _, _ -> it.resume(false) }
            }
            show()
        }
    }

    fun reset() {
        lifecycleScope.launch(Dispatchers.Main) {
            val msgId = when(data.mode) {
                Mode.WORKSPACE -> R.string.reset_workspace_labels
                Mode.HIDELABELS -> R.string.reset_hide_labels
                else -> throw RuntimeException("Illegal value")
            }
            if(askConfirmation(getString(msgId))) {
                val result = Intent()
                data.reset = true

                result.putExtra("data", data.toJSON())

                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }

    }

    private var searchText: String
        get() = binding.editSearchText.text.toString()
        set(value) {
            binding.editSearchText.setText(value)
        }

    private val filterRegex: Regex get() {
        val text = Regex.escape(searchText)
        // For STUDYPAD mode, use searchMode; for other modes, use searchInsideText
        val regex = if (data.mode == Mode.STUDYPAD) {
            when (searchMode) {
                SearchMode.NAME_START -> "^$text"
                SearchMode.NAME_CONTAINS -> text
                SearchMode.CONTENT -> "" // Not used for regex filtering
            }
        } else {
            if (searchInsideText) text else "^$text"
        }
        return try {
            regex.toRegex(RegexOption.IGNORE_CASE)
        } catch (e: PatternSyntaxException) {
            "".toRegex()
        }
    }

    private fun addCategoriesToShownLabels() {
        val labelUnlabeledNotModified = data.changedLabels.find { it == bookmarkControl.labelUnlabelled.id } != null
        if (data.showUnassigned && !labelUnlabeledNotModified) {
            shownLabels.add(bookmarkControl.labelUnlabelled)
        }
        if(data.showActiveCategory && data.contextSelectedItems.isNotEmpty()) {
            shownLabels.add(LabelCategory.ACTIVE)
        }
        if(!data.hideCategories) {
            shownLabels.add(LabelCategory.RECENT)
            shownLabels.add(LabelCategory.OTHER)
        }
    }

    private fun performContentSearch() {
        if (searchText.length >= 3) {
            // Cancel previous search job to prevent race conditions
            currentSearchJob?.cancel()

            // Launch new search with debouncing and error handling
            currentSearchJob = lifecycleScope.launch {
                try {
                    // Debounce: wait 300ms before executing search
                    delay(300)

                    // Perform search on IO dispatcher
                    val results = withContext(Dispatchers.IO) {
                        bookmarkControl.searchStudyPadsByContent(searchText)
                    }

                    // Update UI on Main dispatcher
                    shownLabels.clear()
                    shownLabels.addAll(results)
                    notifyDataSetChanged()
                } catch (e: Exception) {
                    Log.e(TAG, "Search failed", e)
                    // Show all labels on error
                    shownLabels.clear()
                    shownLabels.addAll(allLabels)
                    addCategoriesToShownLabels()
                    notifyDataSetChanged()
                }
            }
        } else {
            // Cancel any ongoing search if less than 3 characters
            currentSearchJob?.cancel()
            currentSearchJob = null

            // Less than 3 characters, show all labels
            shownLabels.addAll(allLabels)
            addCategoriesToShownLabels()
        }
    }

    private fun performNameSearch() {
        Log.i(TAG, "Parsing filter: $filterRegex")

        fun labelMatches(label: BookmarkEntities.Label): Boolean =
            searchText.isEmpty() ||
                filterRegex.containsMatchIn(label.displayName) ||
                data.selectedLabels.contains(label.id)

        shownLabels.addAll(allLabels.filter { labelMatches(it) })
        val labelUnlabeledNotModified = data.changedLabels.find { it == bookmarkControl.labelUnlabelled.id } != null
        if (data.showUnassigned && labelMatches(bookmarkControl.labelUnlabelled) && !labelUnlabeledNotModified) {
            shownLabels.add(bookmarkControl.labelUnlabelled)
        }
        if(data.showActiveCategory && data.contextSelectedItems.isNotEmpty()) {
            shownLabels.add(LabelCategory.ACTIVE)
        }
        if(!data.hideCategories) {
            shownLabels.add(LabelCategory.RECENT)
            shownLabels.add(LabelCategory.OTHER)
        }
    }

    fun updateLabelList(rePopulate: Boolean = false, reOrder: Boolean = false) {
        if (rePopulate) {
            shownLabels.clear()

            // Handle content search mode separately
            if (data.mode == Mode.STUDYPAD && searchMode == SearchMode.CONTENT) {
                performContentSearch()
                if (searchText.length >= 3) {
                    return // Exit early, async operation will update list
                }
            } else {
                performNameSearch()
            }
        }

        val recentLabelIds = bookmarkControl.windowControl.windowRepository.workspaceSettings.recentLabels
            .map { it.labelId }
        if(rePopulate || reOrder) {
            shownLabels.sortWith(compareBy({
                val inActiveCategory = data.showActiveCategory &&
                    (it == LabelCategory.ACTIVE ||
                        (it is BookmarkEntities.Label && data.contextSelectedItems.contains(it.id)))
                val inRecentCategory = !data.hideCategories &&
                    (it == LabelCategory.RECENT || (it is BookmarkEntities.Label && recentLabelIds.contains(it.id)))
                when {
                    inActiveCategory -> 1
                    inRecentCategory -> 2
                    else -> 3
                }
            }, {
                when (it) {
                    is LabelCategory -> 1
                    else -> 2
                }
            }, {
                when (it) {
                    is BookmarkEntities.Label -> it.name.lowercase(Locale.getDefault())
                    else -> ""
                }
            }))
        }

        notifyDataSetChanged()
    }
}

enum class LabelCategory {ACTIVE, RECENT, OTHER}

private fun <E> MutableSet<E>.myRemoveIf(function: (it: E) -> Boolean) =
    filter { function.invoke(it) }.forEach { remove(it) }
private fun <E> MutableList<E>.myRemoveIf(function: (it: E) -> Boolean) =
    filter { function.invoke(it) }.forEach { remove(it) }
