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
import kotlinx.android.synthetic.main.manage_labels.*
import kotlinx.android.synthetic.main.manage_labels_list_item.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random.Default.nextInt

/**
 * Choose a bible or commentary to use
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ManageLabels : ListActivityBase() {
    private val labels: MutableList<BookmarkEntities.Label> = ArrayList()
    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var labelDialogs: LabelDialogs
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    var showUnassigned = false
    var showCheckboxes = false
    var selectMultiple = false
    var hasResetButton = false
    var studyPadMode = false
    private val deletedLabels = mutableSetOf<Long>()

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        setContentView(R.layout.manage_labels)
        super.buildActivityComponent().inject(this)
        val selectedLabelIds = intent.getLongArrayExtra(BookmarkControl.LABEL_IDS_EXTRA)
        if(selectedLabelIds != null) {
            showCheckboxes = true
            checkedLabels.addAll(selectedLabelIds.toList())
        }

        studyPadMode = intent.getBooleanExtra("studyPadMode", false)
        hasResetButton = intent.getBooleanExtra("resetButton", false)
        resetButton.visibility = if(hasResetButton) View.VISIBLE else View.GONE
        showUnassigned = intent.getBooleanExtra("showUnassigned", false)
        val title = intent.getStringExtra("title")
        selectMultiple = checkedLabels.size > 1 || CommonUtils.sharedPreferences.getBoolean("assignLabelsSelectMultiple", false)
        selectMultipleSwitch.isChecked = selectMultiple
        selectMultipleSwitch.visibility = if(showCheckboxes) View.VISIBLE else View.GONE
        selectMultipleSwitch.setOnCheckedChangeListener { _, isChecked ->
            selectMultiple = isChecked
            CommonUtils.sharedPreferences.edit().putBoolean("assignLabelsSelectMultiple", selectMultiple).apply()
        }

        if(studyPadMode) {
            setTitle(getString(R.string.studypads))
        }

        if(title!=null) setTitle(title)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        loadLabelList()
        listAdapter = ManageLabelItemAdapter(this, LIST_ITEM_TYPE, labels, this, checkedLabels, showCheckboxes)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.journals_options_menu, menu)
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
        if(studyPadMode) {
            okay(labels[position])
        }
        super.onListItemClick(l, v, position, id)
    }

    private val checkedLabels = mutableSetOf<Long>()

    fun delete(label: BookmarkEntities.Label) {
        ABEventBus.getDefault().post(ToastEvent(R.string.toast_deletion_cancel))
        deletedLabels.add(label.id)
        checkedLabels.remove(label.id)
        loadLabelList()
    }

    fun setEnabled(label: BookmarkEntities.Label, enabled: Boolean) {
        if (enabled) {
            if(!selectMultiple) {
                checkedLabels.clear()
            }
            checkedLabels.add(label.id)
        } else checkedLabels.remove(label.id)
        notifyDataSetChanged()
    }

    private fun randomColor(): Int = Color.argb(255, nextInt(0, 255), nextInt(0, 255), nextInt(0, 255))

    fun onNewLabel(v: View?) {
        Log.i(TAG, "New label clicked")
        val newLabel = BookmarkEntities.Label()
        newLabel.color = randomColor()
        labelDialogs.createLabel(this, newLabel) {
            loadLabelList()
            checkedLabels.add(newLabel.id)
            notifyDataSetChanged()
        }
    }

    fun editLabel(label: BookmarkEntities.Label?) {
        Log.i(TAG, "Edit label clicked")
        labelDialogs.editLabel(this, label!!) { loadLabelList() }
    }

    fun onOkay(v: View?) {
        okay()
    }

    private fun okay(selected: BookmarkEntities.Label? = null) = GlobalScope.launch(Dispatchers.Main) {
        Log.i(TAG, "Okay clicked")

        if(deletedLabels.size > 0 && !askConfirmation()) return@launch
        val result = Intent()
        bookmarkControl.deleteLabels(deletedLabels.toList())
        val labelIds = checkedLabels.toLongArray()
        result.putExtra(BookmarkControl.LABEL_IDS_EXTRA, labelIds)
        setResult(Activity.RESULT_OK, result)
        if(selected != null) {
            studyPadSelected(selected)
        }
        finish()
    }

    private suspend fun askConfirmation()  = suspendCoroutine<Boolean> {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_study_pads, deletedLabels.size))
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
        result.putExtra("reset", true)
        setResult(Activity.RESULT_CANCELED, result)
        finish();
    }

    fun onCancel(v: View?) {
        setResult(Activity.RESULT_CANCELED)
        finish();
    }

    private fun loadLabelList() {
        labels.clear()
        labels.addAll(bookmarkControl.assignableLabels.filterNot { deletedLabels.contains(it.id) || it.isSpeakLabel || it.isUnlabeledLabel })
        if(showUnassigned) {
            labels.add(bookmarkControl.labelUnlabelled)
        }
        notifyDataSetChanged()
    }

    companion object {
        private const val TAG = "BookmarkLabels"
        private const val LIST_ITEM_TYPE = R.layout.manage_labels_list_item
    }
}
