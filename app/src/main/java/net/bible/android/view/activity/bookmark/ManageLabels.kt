/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.view.activity.bookmark

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ManageLabelsBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.json
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random.Default.nextInt

fun WorkspaceEntities.WorkspaceSettings.updateFrom(resultData: ManageLabels.ManageLabelsData) {
    autoAssignLabels = resultData.autoAssignLabels
    favouriteLabels = resultData.favouriteLabels
    autoAssignPrimaryLabel = resultData.autoAssignPrimaryLabel
    ABEventBus.getDefault().post(AppSettingsUpdated())
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
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    enum class Mode {STUDYPAD, WORKSPACE, ASSIGN, HIDELABELS, MANAGELABELS}

    @Serializable
    data class ManageLabelsData(
        val mode: Mode,

        val selectedLabels: MutableSet<Long> = mutableSetOf(),
        val autoAssignLabels: MutableSet<Long> = mutableSetOf(),
        val favouriteLabels: MutableSet<Long> = mutableSetOf(),
        val deletedLabels: MutableSet<Long> = mutableSetOf(),
        val changedLabels: MutableSet<Long> = mutableSetOf(),

        var autoAssignPrimaryLabel: Long? = null,
        var bookmarkPrimaryLabel: Long? = null,

        var reset: Boolean = false,
    ) {
        val showUnassigned: Boolean get() = setOf(Mode.HIDELABELS, Mode.MANAGELABELS).contains(mode)
        val showCheckboxes: Boolean get() = setOf(Mode.HIDELABELS, Mode.ASSIGN).contains(mode)
        val hasResetButton: Boolean get() = setOf(Mode.WORKSPACE, Mode.HIDELABELS).contains(mode)
        val workspaceEdits: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val primaryShown: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val showActiveCategory: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val hideCategories: Boolean get() = setOf(Mode.STUDYPAD).contains(mode)

        val contextSelectedItems: MutableSet<Long> get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignLabels
                else -> selectedLabels
            }

        var contextPrimaryLabel: Long? get() =
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
                Mode.WORKSPACE -> R.string.auto_assign_labels
                Mode.HIDELABELS -> R.string.bookmark_settings_hide_labels_title
                Mode.MANAGELABELS -> R.string.manage_labels
            }
        }

        fun toJSON(): String = json.encodeToString(serializer(), this)
        fun applyFrom(workspaceSettings: WorkspaceEntities.WorkspaceSettings): ManageLabelsData {
            favouriteLabels.addAll(workspaceSettings.favouriteLabels)
            autoAssignLabels.addAll(workspaceSettings.autoAssignLabels)
            autoAssignPrimaryLabel = workspaceSettings.autoAssignPrimaryLabel
            return this
        }

        companion object {
            fun fromJSON(str: String): ManageLabelsData = json.decodeFromString(serializer(), str)
        }
    }

    var selectMultiple: Boolean = false

    lateinit var data: ManageLabelsData

    override fun onBackPressed() {
        saveAndExit()
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        binding = ManageLabelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)

        data = ManageLabelsData.fromJSON(intent.getStringExtra("data")!!)

        binding.selectMultipleSwitch.visibility = View.GONE
        selectMultiple = true
        // Let's remove selectMultible and see if anyone notices
        //selectMultiple = data.selectedLabels.size > 1 || CommonUtils.sharedPreferences.getBoolean("assignLabelsSelectMultiple", false)
        //binding.selectMultipleSwitch.isChecked = selectMultiple
        //binding.selectMultipleSwitch.visibility = if(data.showCheckboxes) View.VISIBLE else View.GONE
        //binding.selectMultipleSwitch.setOnCheckedChangeListener { _, isChecked ->
        //    selectMultiple = isChecked
        //    CommonUtils.sharedPreferences.edit().putBoolean("assignLabelsSelectMultiple", selectMultiple).apply()
        //}

        if(data.mode == Mode.STUDYPAD) {
            title = getString(R.string.studypads)
        }

        title = getString(data.titleId)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        updateLabelList(fromDb = true)
        listAdapter = ManageLabelItemAdapter(this, shownLabels, this)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        menuInflater.inflate(R.menu.manage_labels_options_menu, menu)
        menu.findItem(R.id.resetButton).isVisible = data.hasResetButton
        menu.findItem(R.id.help).isVisible = !data.showCheckboxes
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.help -> CommonUtils.showHelp(this, listOf(R.string.help_studypads_title, R.string.help_bookmarks_title))
            R.id.newLabel -> newLabel()
            R.id.reOrder -> updateLabelList(reOrder = true)
            android.R.id.home -> saveAndExit()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    private fun studyPadSelected(journal: BookmarkEntities.Label) {
        Log.d(TAG, "Journal selected:" + journal.name)
        try {
            activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocumentAndKey(FakeBookFactory.journalDocument, StudyPadKey(journal))
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to show journal", e)
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        if(data.mode == Mode.STUDYPAD) {
            val selected = shownLabels[position]
            if(selected is BookmarkEntities.Label) saveAndExit(selected)
        }
        super.onListItemClick(l, v, position, id)
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
        Log.i(TAG, "New label clicked")
        val newLabel = BookmarkEntities.Label()
        newLabel.color = randomColor()
        newLabel.id = -(newLabelCount++)
        editLabel(newLabel)
    }

    private fun deleteLabel(label: BookmarkEntities.Label) {
        data.deletedLabels.add(label.id)
        data.selectedLabels.remove(label.id)
        data.autoAssignLabels.remove(label.id)
        data.favouriteLabels.remove(label.id)
        data.changedLabels.remove(label.id)

        ensureNotBookmarkPrimaryLabel(label)
        ensureNotAutoAssignPrimaryLabel(label)
    }

    fun editLabel(label_: BookmarkEntities.Label) {
        var label = label_
        val isNew = label.id < 0
        val intent = Intent(this, LabelEditActivity::class.java)
        val labelData = LabelEditActivity.LabelData(
            isAssigning = data.mode == Mode.ASSIGN,
            label = label,
            isAutoAssign = data.autoAssignLabels.contains(label.id),
            isFavourite = data.favouriteLabels.contains(label.id),
            isAutoAssignPrimary = data.autoAssignPrimaryLabel == label.id,
            isThisBookmarkPrimary = data.bookmarkPrimaryLabel == label.id,
            isThisBookmarkSelected = data.selectedLabels.contains(label.id)
        )
        if(isNew) {
            if(data.mode === Mode.ASSIGN) {
                labelData.isThisBookmarkSelected = true
                labelData.isThisBookmarkPrimary = true
            } else if(data.mode === Mode.WORKSPACE) {
                labelData.isAutoAssignPrimary = true
                labelData.isAutoAssign = true
            }
        }

        intent.putExtra("data", json.encodeToString(serializer(), labelData))

        GlobalScope.launch(Dispatchers.Main) {
            val result = awaitIntent(intent) ?: return@launch
            if (result.resultCode != Activity.RESULT_CANCELED) {
                val newLabelData: LabelEditActivity.LabelData = json.decodeFromString(
                    serializer(), result.resultData.getStringExtra("data")!!)

                if(newLabelData.label.name.isEmpty() && label.id < 0) {
                    return@launch
                }

                if (newLabelData.delete) {
                    deleteLabel(label)

                } else {
                    val idx = shownLabels.indexOf(label)
                    shownLabels.remove(label)
                    label = newLabelData.label
                    if(idx > 0) {
                        shownLabels.add(idx, label)
                    } else {
                        shownLabels.add(label)
                    }
                    data.changedLabels.add(label.id)

                    if (newLabelData.isAutoAssign) {
                        data.autoAssignLabels.add(label.id)
                    } else {
                        data.autoAssignLabels.remove(label.id)
                    }
                    if (newLabelData.isFavourite) {
                        data.favouriteLabels.add(label.id)
                    } else {
                        data.favouriteLabels.remove(label.id)
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
                }
                updateLabelList(reOrder = isNew)
                if(isNew) {
                    listView.smoothScrollToPosition(shownLabels.indexOf(label))
                }
            }
        }
    }

    private fun saveAndExit(selected: BookmarkEntities.Label? = null) = GlobalScope.launch(Dispatchers.Main) {
        Log.i(TAG, "Okay clicked")
        val deleteLabelIds = data.deletedLabels.filter{ it > 0 }.toList()
        if(deleteLabelIds.isNotEmpty()) {
            bookmarkControl.deleteLabels(deleteLabelIds)
        }

        val result = Intent()
        val saveLabels = shownLabels
            .filterIsInstance<BookmarkEntities.Label>()
            .filter{ data.changedLabels.contains(it.id) && !data.deletedLabels.contains(it.id) }

        val newLabels = saveLabels.filter { it.id < 0 }
        val existingLabels = saveLabels.filter { it.id > 0 }

        for (it in newLabels) {
            val oldLabel = it.id
            it.id = 0
            it.id = bookmarkControl.insertOrUpdateLabel(it).id
            for(list in listOf(data.selectedLabels, data.autoAssignLabels, data.changedLabels, data.favouriteLabels)) {
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

        result.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, result)
        if(selected != null) {
            studyPadSelected(selected)
        }
        finish()
    }

    private suspend fun askConfirmation(message: String, yesNo: Boolean = false)  = suspendCoroutine<Boolean> {
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
        GlobalScope.launch(Dispatchers.Main) {
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

    fun updateLabelList(fromDb: Boolean = false, reOrder: Boolean = false) {
        if(fromDb) {
            allLabels.clear()
            allLabels.addAll(bookmarkControl.assignableLabels.filterNot { it.isUnlabeledLabel })
            if (data.showUnassigned) {
                allLabels.add(bookmarkControl.labelUnlabelled)
            }
            if(data.showActiveCategory) {
                shownLabels.add(LabelCategory.ACTIVE)
            }
            if(!data.hideCategories) {
                shownLabels.add(LabelCategory.RECENT)
                shownLabels.add(LabelCategory.OTHER)
            }
            shownLabels.addAll(allLabels)
        }
        val recentLabelIds = mainBibleActivity.workspaceSettings.recentLabels.map { it.labelId }
        shownLabels.myRemoveIf { it is BookmarkEntities.Label && data.deletedLabels.contains(it.id) }
        if(fromDb || reOrder) {
            shownLabels.sortWith(compareBy({
                val inActiveCategory = data.showActiveCategory && (it == LabelCategory.ACTIVE || (it is BookmarkEntities.Label && data.contextSelectedItems.contains(it.id)))
                val inRecentCategory = !data.hideCategories && (it == LabelCategory.RECENT || (it is BookmarkEntities.Label && recentLabelIds.contains(it.id)))
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
                    is BookmarkEntities.Label -> it.name.toLowerCase(Locale.getDefault())
                    else -> ""
                }
            }))
        }

        val labelIds = shownLabels.filterIsInstance<BookmarkEntities.Label>().map { it.id }.toSet()

        // Some sanity check
        data.autoAssignLabels.myRemoveIf { !labelIds.contains(it) }
        data.favouriteLabels.myRemoveIf { !labelIds.contains(it) }
        data.selectedLabels.myRemoveIf { !labelIds.contains(it) }

        notifyDataSetChanged()
    }

    companion object {
        private const val TAG = "BookmarkLabels"
    }
}

enum class LabelCategory {ACTIVE, RECENT, OTHER}

private fun <E> MutableSet<E>.myRemoveIf(function: (it: E) -> Boolean)  = filter { function.invoke(it) }.forEach { remove(it) }
private fun <E> MutableList<E>.myRemoveIf(function: (it: E) -> Boolean)  = filter { function.invoke(it) }.forEach { remove(it) }

