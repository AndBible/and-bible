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

package net.bible.android.view.activity.workspaces

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.workspace_selector.*
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.service.db.DatabaseContainer
import org.w3c.dom.Text
import javax.inject.Inject


class WorkspaceViewHolder(val layout: ViewGroup): RecyclerView.ViewHolder(layout)

class WorkspaceAdapter(val activity: WorkspaceSelectorActivity): RecyclerView.Adapter<WorkspaceViewHolder>() {
    val items get() = activity.dataSet

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkspaceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.workspace_list_item, parent, false) as ViewGroup
        return WorkspaceViewHolder(view)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun getItemCount() = items.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: WorkspaceViewHolder, position: Int) {
        val dragHolder = holder.layout.findViewById<ImageView>(R.id.dragHolder)
        val title = holder.layout.findViewById<TextView>(R.id.title)
        val summary = holder.layout.findViewById<TextView>(R.id.summary)
        val menuButton = holder.layout.findViewById<ImageButton>(R.id.menuButton)
        val layout = holder.layout
        val workspaceEntity = items[position]
        var titleText = workspaceEntity.name
        title.typeface = Typeface.DEFAULT
        if(activity.windowControl.windowRepository.id == workspaceEntity.id) {
            title.typeface = Typeface.DEFAULT_BOLD
            titleText = activity.getString(R.string.workspace_listing_with_current, titleText)
        }
        title.text = titleText
        summary.text = workspaceEntity.contentsText

        layout.setOnClickListener {
            activity.goToWorkspace(holder.itemId)
        }
        layout.setOnLongClickListener {true}
        dragHolder.setOnTouchListener { v, event ->
            if(event.actionMasked == MotionEvent.ACTION_DOWN) {
                activity.itemTouchHelper.startDrag(holder)
            }
            true
        }
        menuButton.setOnClickListener {
            activity.showPopupMenu(it, workspaceEntity)
        }
    }

    fun moveItem(from: Int, to: Int) {
        Log.d("MoveItem","Moving $from $to")
        if(from == to) return

        val item = items[from]
        if(from < to)
            items.removeAt(from)

        items.add(to, item)
        if(from > to)
            items.removeAt(from + 1)

        for((idx, ws) in items.withIndex()) {
            ws.orderNumber = idx
        }
        notifyItemMoved(from, to)
    }
}

@ActivityScope
class WorkspaceSelectorActivity: ActivityBase() {
    private var finished = false
    private var isDirty: Boolean = false
    private val workspacesToBeDeleted = HashSet<Long>()
    private val workspacesCreated = HashSet<Long>()
    override val customTheme: Boolean = false
    private lateinit var resultIntent: Intent
    @Inject lateinit var windowControl: WindowControl
    internal lateinit var dataSet: MutableList<WorkspaceEntities.Workspace>
    private lateinit var workspaceAdapter: WorkspaceAdapter

    val itemTouchHelper by lazy {
        val cb = object: ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val adapter = recyclerView.adapter as WorkspaceAdapter
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // TODO: delete?
            }
        }
        ItemTouchHelper(cb)
    }

    fun setDirty() {
        isDirty = true
        resultIntent.putExtra("changed", true)
        save.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.buildActivityComponent().inject(this)
        windowControl.windowRepository.saveIntoDb()
        resultIntent = Intent(this, this::class.java)
        setContentView(R.layout.workspace_selector)
        workspaceAdapter = WorkspaceAdapter(this).apply {
            setHasStableIds(true)
            registerAdapterDataObserver(object: RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    setDirty()
                }

                override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                    if(payload == null) {
                        setDirty()
                    }
                }

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    setDirty()
                }

                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                    setDirty()
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    setDirty()
                }
            })
        }

        dataSet = DatabaseContainer.db.workspaceDao().allWorkspaces().toMutableList()

        val workspace = dataSet.find { it.id == windowControl.windowRepository.id }!!
        val currentPosition = dataSet.indexOf(workspace)

        val llm = LinearLayoutManager(this)

        recyclerView.apply {
            layoutManager = llm
            setHasFixedSize(true)
        }
        itemTouchHelper.attachToRecyclerView(recyclerView)

        newWorkspace.setOnClickListener {
            val name = EditText(this)
            name.text = SpannableStringBuilder(getString(R.string.workspace_number, dataSet.size + 1))
            name.selectAll()
            name.requestFocus()
            AlertDialog.Builder(this)
                .setPositiveButton(R.string.okay) {d,_ ->
                    val windowRepository = windowControl.windowRepository
                    val newWorkspaceEntity = WorkspaceEntities.Workspace(
                        name.text.toString(), null, 0,
                        windowRepository.orderNumber,
                        windowRepository.textDisplaySettings,
                        windowRepository.windowBehaviorSettings
                    ).apply {
                        id = dao.insertWorkspace(this)
                    }
                    goToWorkspace(newWorkspaceEntity.id)
                }
                .setView(name)
                .setNegativeButton(R.string.cancel, null)
                .setTitle(getString(R.string.give_name_workspace))
                .create()
                .show()
        }

        cancel.setOnClickListener {
            cancelChanges()
            finishCanceled()
        }

        save.setOnClickListener {
            applyChanges()
            finishOk()
        }
        recyclerView.adapter = workspaceAdapter
    }

    private fun finishOk() {
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
        finished = true;
    }

    private fun finishCanceled() {
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
        finished = true;
    }

    private fun applyChanges() {
        workspacesToBeDeleted.forEach {
            dao.deleteWorkspace(it)
        }
        dao.updateWorkspaces(dataSet)
    }

    private fun cancelChanges() {
        workspacesCreated.forEach {
            dao.deleteWorkspace(it)
        }
    }

    private fun handleMenuItem(item: MenuItem?, workspace: WorkspaceEntities.Workspace): Boolean {
        val position = dataSet.indexOf(workspace)
        val workspaceId = workspace.id

        when(item?.itemId) {
            R.id.settings -> {
                val intent = Intent(this@WorkspaceSelectorActivity, TextDisplaySettingsActivity::class.java)
                val settings = SettingsBundle(
                    workspaceId = workspaceId,
                    workspaceName = workspace.name,
                    workspaceSettings = dataSet.find {it.id == workspaceId}!!.textDisplaySettings ?: WorkspaceEntities.TextDisplaySettings.default
                )
                intent.putExtra("settingsBundle", settings.toJson())
                startActivityForResult(intent, WORKSPACE_SETTINGS_CHANGED)
            }
            R.id.deleteWorkspace -> {
                workspacesToBeDeleted.add(workspaceId)
                dataSet.removeAt(position)
                workspaceAdapter.notifyItemRemoved(position)
            }
            R.id.renameWorkspace -> {
                val name = EditText(this@WorkspaceSelectorActivity)
                name.text = SpannableStringBuilder(workspace.name)
                name.selectAll()
                name.requestFocus()
                AlertDialog.Builder(this@WorkspaceSelectorActivity)
                    .setPositiveButton(R.string.okay) {d,_ ->
                        workspace.name = name.text.toString()
                        workspaceAdapter.notifyItemChanged(position)
                    }
                    .setView(name)
                    .setNegativeButton(R.string.cancel, null)
                    .setTitle(getString(R.string.give_name_workspace))
                    .create()
                    .show()
            }
            R.id.cloneWorkspace -> {
                val name = EditText(this)
                name.text = SpannableStringBuilder(getString(R.string.copy_of_workspace, workspace.name))
                name.selectAll()
                name.requestFocus()
                AlertDialog.Builder(this)
                    .setPositiveButton(R.string.okay) {d,_ ->
                        val newWorkspaceEntity = dao.cloneWorkspace(workspaceId, name.text.toString())
                        workspacesCreated.add(newWorkspaceEntity.id)
                        dataSet.add(position + 1, newWorkspaceEntity)
                        workspaceAdapter.notifyItemInserted(position + 1)
                    }
                    .setView(name)
                    .setNegativeButton(R.string.cancel, null)
                    .setTitle(getString(R.string.give_name_workspace))
                    .create()
                    .show()

            }
            R.id.copySettings -> {
                copySettingsStage1(workspace)
            }
        }
        return false
    }

    private fun copySettingsStage1(workspace: WorkspaceEntities.Workspace) {
        val items = WorkspaceEntities.TextDisplaySettings.Types.values().map {
            getPrefItem(SettingsBundle(workspace.id, workspace.name, workspace.textDisplaySettings?: WorkspaceEntities.TextDisplaySettings.default), it).title
        }.toTypedArray()
        val checkedItems = items.map { false }.toBooleanArray()
        val dialog = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay) {d,_ ->
                copySettingsStage2(workspace, checkedItems)
            }
            .setMultiChoiceItems(items, checkedItems) { _, pos, value ->
                checkedItems[pos] = value
            }
            .setNeutralButton(R.string.select_all, null)
            .setNegativeButton(R.string.cancel, null)
            .setTitle(getString(R.string.copy_settings_title))
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val allSelected = checkedItems.find { !it } == null
                val newValue = !allSelected
                val v = dialog.listView
                for(i in 0 until v.count) {
                    v.setItemChecked(i, newValue)
                    checkedItems[i] = newValue
                }
                (it as Button).text = getString(if(allSelected) R.string.select_all else R.string.select_none)
            }
        }
        dialog.show()

    }

    private fun copySettingsStage2(workspace: WorkspaceEntities.Workspace, checkedTypes: BooleanArray) {
        val types = WorkspaceEntities.TextDisplaySettings.Types.values()
        val workspaceNames = dataSet.map { it.name }.toTypedArray()
        val checkedItems = workspaceNames.map {false}.toBooleanArray()

        val dialog = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay) { d, _ ->
                for ((wsIdx, ws) in dataSet.withIndex())
                    for ((tIdx, type) in types.withIndex()) {
                        if(checkedTypes[tIdx] && checkedItems[wsIdx] && workspace.id != ws.id) {
                            val s = ws.textDisplaySettings?: WorkspaceEntities.TextDisplaySettings.default
                            s.setValue(type, workspace.textDisplaySettings?.getValue(type))
                            ws.textDisplaySettings = s
                        }
                    }
                setDirty()
            }
            .setMultiChoiceItems(workspaceNames, checkedItems) { _, pos, value ->
                checkedItems[pos] = value
            }
            .setNeutralButton(R.string.select_all, null)
            .setNegativeButton(R.string.cancel, null)
            .setTitle(getString(R.string.copy_settings_workspaces_title))
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val allSelected = checkedItems.find { !it } == null
                val newValue = !allSelected
                val v = dialog.listView
                for(i in 0 until v.count) {
                    v.setItemChecked(i, newValue)
                    checkedItems[i] = newValue
                }
                (it as Button).text = getString(if(allSelected) R.string.select_all else R.string.select_none)
            }
        }
        dialog.show()

    }

    private val dao = DatabaseContainer.db.workspaceDao()

    override fun onBackPressed() {
        cancelChanges()
        finishCanceled()
        super.onBackPressed()
    }

    override fun onDetachedFromWindow() {
        if(!finished) {
            cancelChanges()
        }
        super.onDetachedFromWindow()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == WORKSPACE_SETTINGS_CHANGED) {
            val extras = data!!.extras!!
            val reset = extras.getBoolean("reset")
            val settings = SettingsBundle.fromJson(extras.getString("settingsBundle")!!)
            val workspaceItem = dataSet.find {it.id == settings.workspaceId}!!
            workspaceItem.textDisplaySettings =
                if(reset) WorkspaceEntities.TextDisplaySettings.default
                else settings.workspaceSettings
            setDirty()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun goToWorkspace(itemId: Long) {
        fun apply(save: Boolean) {
            if(save)
                applyChanges()
            else
                cancelChanges()
            resultIntent.putExtra("workspaceId", itemId)
            finishOk()
        }
        if(isDirty) {
            AlertDialog.Builder(this@WorkspaceSelectorActivity)
                .setPositiveButton(R.string.yes) {_, _ ->
                    apply(true)
                }
                .setNegativeButton(R.string.no) {_, _ ->
                    resultIntent.putExtra("changed", false)
                    apply(false)
                }
                .setNeutralButton(R.string.cancel, null)
                .setMessage(getString(R.string.workspace_save_changes))
                .create()
                .show()
        } else {
            apply(true)
        }

    }

    fun showPopupMenu(view: View, workspaceEntity: WorkspaceEntities.Workspace) {
        val popup = PopupMenu(this, view)
        popup.setOnMenuItemClickListener {
            handleMenuItem(it, workspaceEntity)
        }
        val menu = popup.menu
        popup.menuInflater.inflate(R.menu.workspace_popup_menu, menu)
        val delItem = menu.findItem(R.id.deleteWorkspace)
        delItem.isEnabled = workspaceEntity.id != windowControl.windowRepository.id
        popup.show()
    }

    companion object {
        const val WORKSPACE_SETTINGS_CHANGED = 1
    }

}
