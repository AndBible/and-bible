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
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListView
import kotlinx.android.synthetic.main.manage_labels.*
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.CommonUtils
import java.util.*
import javax.inject.Inject
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

    var showUnassigned = false
    var showCheckboxes = false
    var selectMultiple = false
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

        showUnassigned = intent.getBooleanExtra("showUnassigned", false)
        val title = intent.getStringExtra("title")
        selectMultiple = CommonUtils.sharedPreferences.getBoolean("assignLabelsSelectMultiple", false)
        selectMultipleSwitch.isChecked = selectMultiple
        selectMultipleSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            selectMultiple = isChecked
            CommonUtils.sharedPreferences.edit().putBoolean("assignLabelsSelectMultiple", selectMultiple).apply()
        }
        if(title!=null) setTitle(title)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        loadLabelList()
        listAdapter = ManageLabelItemAdapter(this, LIST_ITEM_TYPE, labels, this, checkedLabels, showCheckboxes)
    }

    private val checkedLabels = mutableSetOf<Long>()

    fun delete(label: BookmarkEntities.Label) {
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
        Log.i(TAG, "Okay clicked")
        val result = Intent()
        bookmarkControl.deleteLabels(deletedLabels.toList())
        val labelIds = checkedLabels.toLongArray()
        result.putExtra(BookmarkControl.LABEL_IDS_EXTRA, labelIds)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    fun onCancel(v: View?) {
        setResult(Activity.RESULT_CANCELED)
        finish();
    }

    private fun loadLabelList() {
        labels.clear()
        labels.addAll(bookmarkControl.assignableLabels.filter { !deletedLabels.contains(it.id) })
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
