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
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
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
}


/**
 *
 * ManageLabels serves for bookmark label management, selections (in several contexts) and StudyPad selection.
 */
class ManageLabels : ListActivityBase() {
    private lateinit var binding: ManageLabelsBinding
    private val allLabels: MutableList<BookmarkEntities.Label> = ArrayList()
    private val shownLabels: MutableList<BookmarkEntities.Label> = ArrayList()
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

        val selectedItems: MutableSet<Long> get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignLabels
                else -> selectedLabels
            }

        var primaryLabel: Long? get() =
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

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        binding = ManageLabelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)

        data = ManageLabelsData.fromJSON(intent.getStringExtra("data")!!)

        binding.resetButton.visibility = if(data.hasResetButton) View.VISIBLE else View.GONE

        selectMultiple = data.selectedLabels.size > 1 || CommonUtils.sharedPreferences.getBoolean("assignLabelsSelectMultiple", false)

        binding.selectMultipleSwitch.isChecked = selectMultiple
        binding.selectMultipleSwitch.visibility = if(data.showCheckboxes) View.VISIBLE else View.GONE
        binding.selectMultipleSwitch.setOnCheckedChangeListener { _, isChecked ->
            selectMultiple = isChecked
            CommonUtils.sharedPreferences.edit().putBoolean("assignLabelsSelectMultiple", selectMultiple).apply()
        }

        if(data.mode == Mode.STUDYPAD) {
            title = getString(R.string.studypads)
            binding.okButton.visibility = View.GONE
            binding.spacer.visibility = View.GONE
        }

        title = getString(data.titleId)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        updateLabelList(true)
        listAdapter = ManageLabelItemAdapter(this, shownLabels, this)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.journals_options_menu, menu)
        menu.findItem(R.id.help).isVisible = !data.showCheckboxes
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = false
        when(item.itemId){
            R.id.help -> {
                CommonUtils.showHelp(this, listOf(R.string.help_studypads_title, R.string.help_bookmarks_title))
            }
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
            okay(shownLabels[position])
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

    private var newLabelCount = 0L

    fun onNewLabel(v: View?) {
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
        val intent = Intent(this, LabelEditActivity::class.java)
        val labelData = LabelEditActivity.LabelData(
            isAssigning = data.mode == Mode.ASSIGN,
            label = label,
            isAutoAssign = data.autoAssignLabels.contains(label.id),
            isFavourite = data.favouriteLabels.contains(label.id),
            isAutoAssignPrimary = data.autoAssignPrimaryLabel == label.id,
            isThisBookmarkPrimary = data.primaryLabel == label.id,
        )
        intent.putExtra("data", json.encodeToString(serializer(), labelData))

        GlobalScope.launch(Dispatchers.Main) {
            val result = awaitIntent(intent) ?: return@launch
            if (result.resultCode != Activity.RESULT_CANCELED) {
                val newLabelData: LabelEditActivity.LabelData = json.decodeFromString(
                    serializer(), result.resultData.getStringExtra("data")!!)

                if (newLabelData.delete) {
                    deleteLabel(label)

                } else {
                    shownLabels.remove(label)
                    label = newLabelData.label
                    shownLabels.add(label)
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
                }
                updateLabelList()
            }
        }
    }

    fun onOkay(v: View?) {
        okay()
    }

    private fun okay(selected: BookmarkEntities.Label? = null) = GlobalScope.launch(Dispatchers.Main) {
        Log.i(TAG, "Okay clicked")
        val deleteLabelIds = data.deletedLabels.filter{ it > 0 }.toList()
        if(deleteLabelIds.isNotEmpty() && !askConfirmation(deleteLabelIds.size)) return@launch
        val result = Intent()
        bookmarkControl.deleteLabels(deleteLabelIds)
        val saveLabels = shownLabels.filter { data.changedLabels.contains(it.id) && !data.deletedLabels.contains(it.id) }
        saveLabels.filter { it.id < 0 }.forEach {
            val oldLabel = it.id
            it.id = bookmarkControl.insertOrUpdateLabel(it).id
            for(list in listOf(data.selectedLabels, data.autoAssignLabels, data.changedLabels, data.favouriteLabels)) {
                if(list.contains(oldLabel)) {
                    list.remove(oldLabel)
                    list.add(it.id)
                }
            }
            if(data.primaryLabel == oldLabel) {
                data.primaryLabel = it.id
            }
        }

        for (it in saveLabels) {
            bookmarkControl.insertOrUpdateLabel(it)
        }

        result.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, result)
        if(selected != null) {
            studyPadSelected(selected)
        }
        finish()
    }

    private suspend fun askConfirmation(size: Int)  = suspendCoroutine<Boolean> {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_study_pads, size))
            .setPositiveButton(R.string.yes) { _, _ ->
                it.resume(true)
            }
            .setCancelable(true)
            .setOnCancelListener { _ -> it.resume(false) }
            .setNegativeButton(R.string.cancel) { _, _ -> it.resume(false)}
            .show()
    }

    fun onReset(v: View?) {
        val result = Intent()
        data.reset = true

        result.putExtra("data", data.toJSON())

        setResult(Activity.RESULT_OK, result)
        finish();
    }

    fun onCancel(v: View?) {
        setResult(Activity.RESULT_CANCELED)
        finish();
    }

    private fun updateLabelList(fromDb: Boolean = false) {
        if(fromDb) {
            allLabels.clear()
            allLabels.addAll(bookmarkControl.assignableLabels.filterNot { it.isSpeakLabel || it.isUnlabeledLabel })
            if (data.showUnassigned) {
                allLabels.add(bookmarkControl.labelUnlabelled)
            }
            shownLabels.addAll(allLabels)
        }
        shownLabels.myRemoveIf { data.deletedLabels.contains(it.id) }
        shownLabels.sortBy { it.name }
        val labelIds = shownLabels.map { it.id }.toSet()

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

private fun <E> MutableSet<E>.myRemoveIf(function: (it: E) -> Boolean)  = filter { function.invoke(it) }.forEach { remove(it) }
private fun <E> MutableList<E>.myRemoveIf(function: (it: E) -> Boolean)  = filter { function.invoke(it) }.forEach { remove(it) }

