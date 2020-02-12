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

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
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
import net.bible.service.db.DatabaseContainer
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

    override fun onBindViewHolder(holder: WorkspaceViewHolder, position: Int) {
        val title = holder.layout.findViewById<TextView>(R.id.title)
        val summary = holder.layout.findViewById<TextView>(R.id.summary)
        val layout = holder.layout
        title.text = items[position].name
        summary.text = "test ${position}"

        layout.setOnClickListener {
            activity.goToWorkspace(holder.itemId)
        }
        layout.setOnLongClickListener {true}

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

class WorkspaceDetailsLookup(private val workspaceList: RecyclerView): ItemDetailsLookup<Long>() {
    override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? {
        val view = workspaceList.findChildViewUnder(e.x, e.y)
        if(view != null) {
            val holder = workspaceList.getChildViewHolder(view)
            if(holder is WorkspaceViewHolder) {
                return object: ItemDetails<Long>() {
                    override fun getSelectionKey(): Long? = holder.itemId
                    override fun getPosition(): Int = holder.adapterPosition

                }
            }
        }
        return null
    }

}

@ActivityScope
class WorkspaceSelectorActivity: ActivityBase() {
    private lateinit var resultIntent: Intent
    @Inject lateinit var windowControl: WindowControl
    internal lateinit var dataSet: MutableList<WorkspaceEntities.Workspace>
    private lateinit var workspaceAdapter: WorkspaceAdapter
    private lateinit var tracker: SelectionTracker<Long>
    private var actionMode: ActionMode? = null
    private val itemTouchHelper by lazy {
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
        save.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultIntent = Intent(this, this::class.java)
        setContentView(R.layout.workspace_selector)
        super.buildActivityComponent().inject(this)
        val layoutManager = LinearLayoutManager(this)
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

        recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = workspaceAdapter
            setHasFixedSize(true)
        }
        itemTouchHelper.attachToRecyclerView(recyclerView)



        val workspaceDetailsLookup = WorkspaceDetailsLookup(recyclerView)
        val keyProvider = object: ItemKeyProvider<Long>(SCOPE_MAPPED) {
            override fun getKey(position: Int): Long = workspaceAdapter.getItemId(position)
            override fun getPosition(key: Long): Int =
                recyclerView.findViewHolderForItemId(key)?.layoutPosition ?: RecyclerView.NO_POSITION

        }

        newWorkspace.setOnClickListener {
            val name = EditText(this)
            name.text = SpannableStringBuilder(getString(R.string.workspace_number, dataSet.size + 1))
            name.selectAll()
            name.requestFocus()
            AlertDialog.Builder(this)
                .setPositiveButton(R.string.okay) {d,_ ->
                    val windowRepository = windowControl.windowRepository
                    windowRepository.saveIntoDb()
                    val newWorkspaceEntity = WorkspaceEntities.Workspace(
                        name.text.toString(), 0,
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
            finish()
        }

        save.setOnClickListener {
            dao.updateWorkspaces(workspaceAdapter.items)
            finish()
        }

        tracker = SelectionTracker.Builder<Long>("workspace-selector",
            recyclerView,
            keyProvider,
            workspaceDetailsLookup,
            StorageStrategy.createLongStorage()
        )
            .withSelectionPredicate(SelectionPredicates.createSelectSingleAnything())
            //.withSelectionPredicate(SelectionPredicates.createSelectAnything())
            .build()

        tracker.addObserver(object: SelectionTracker.SelectionObserver<Long>() {
            override fun onItemStateChanged(key: Long, selected: Boolean) {
                val holder = recyclerView.findViewHolderForItemId(key) as WorkspaceViewHolder
                holder.layout.isActivated = selected
                if(actionMode == null) {
                    startSupportActionMode(object: ActionMode.Callback {
                        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                            val workspaceId: Long = tracker.selection.iterator().next()
                            val workspace = dataSet.find { it.id == workspaceId }!!
                            val position = dataSet.indexOf(workspace)

                            when(item?.itemId) {
                                R.id.settings -> {
                                    val intent = Intent(this@WorkspaceSelectorActivity, TextDisplaySettingsActivity::class.java)
                                    val settings = SettingsBundle(workspaceId = workspaceId,
                                        workspaceSettings = workspaceAdapter.items.find {it.id == workspaceId}!!.textDisplaySettings!!)
                                    intent.putExtra("settingsBundle", settings.toJson())
                                    startActivityForResult(intent, WORKSPACE_SETTINGS_CHANGED)
                                }
                                R.id.deleteWorkspace -> {
                                    AlertDialog.Builder(this@WorkspaceSelectorActivity)
                                        .setPositiveButton(R.string.yes) {_, _ ->
                                            dao.deleteWorkspace(workspaceId)
                                            dataSet.removeAt(position)
                                            workspaceAdapter.notifyItemRemoved(position)
                                        }
                                        .setNegativeButton(R.string.cancel, null)
                                        .setMessage(getString(R.string.remove_workspace_confirmation))
                                        .create()
                                        .show()
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
                                R.id.cloneWorkspace -> {}
                            }
                            return false
                        }

                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            actionMode = mode
                            mode.menuInflater.inflate(R.menu.workspace_action_mode_menu, menu)
                            return true
                        }

                        override fun onDestroyActionMode(mode: ActionMode) {
                            tracker.clearSelection()
                            actionMode = null
                        }

                        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                            return true
                        }

                    })
                }
            }
            override fun onSelectionChanged() {
                actionMode?.invalidate()
                if(tracker.selection.isEmpty) {
                    actionMode?.finish()
                }
            }
        })

    }

    private val dao = DatabaseContainer.db.workspaceDao()

    override fun onBackPressed() {
        // dao.updateWorkspaces(workspaceAdapter.items)
        setResult(Activity.RESULT_CANCELED, resultIntent)
        super.onBackPressed()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == WORKSPACE_SETTINGS_CHANGED) {
            val extras = data!!.extras!!
            val settings = SettingsBundle.fromJson(extras.getString("settingsBundle")!!)
            if(settings.workspaceId == windowControl.windowRepository.id) {
                resultIntent.putExtra("settingsChanged", true)
                setResult(Activity.RESULT_CANCELED, resultIntent)
            }
            val workspaceItem = workspaceAdapter.items.find {it.id == settings.workspaceId}!!
            workspaceItem.textDisplaySettings = settings.workspaceSettings
            dao.updateWorkspace(workspaceItem)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun goToWorkspace(itemId: Long) {
        dao.updateWorkspaces(workspaceAdapter.items)
        resultIntent.putExtra("workspaceId", itemId)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val WORKSPACE_SETTINGS_CHANGED = 1
    }

}
