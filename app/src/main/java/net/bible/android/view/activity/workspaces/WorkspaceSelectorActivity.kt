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
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
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


class WorkspaceViewHolder(val layout: LinearLayout): RecyclerView.ViewHolder(layout)

class WorkspaceAdapter(val activity: WorkspaceSelectorActivity): RecyclerView.Adapter<WorkspaceViewHolder>() {
    val items get() = activity.dataSet

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkspaceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.workspace_list_item, parent, false) as LinearLayout
        return WorkspaceViewHolder(view)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: WorkspaceViewHolder, position: Int) {
        val title = holder.layout.findViewById<TextView>(R.id.title)
        val summary = holder.layout.findViewById<TextView>(R.id.summary)
        val layout = holder.layout.findViewById<LinearLayout>(R.id.layout)
        title.text = items[position].name
        summary.text = "test ${position}"

        layout.setOnClickListener {
            activity.workspaceClicked(holder.itemId)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultIntent = Intent(this, this::class.java)
        setContentView(R.layout.workspace_selector)
        super.buildActivityComponent().inject(this)
        val layoutManager = LinearLayoutManager(this)
        workspaceAdapter = WorkspaceAdapter(this).apply {
            setHasStableIds(true)
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

        tracker = SelectionTracker.Builder<Long>("workspace-id",
            recyclerView,
            keyProvider,
            workspaceDetailsLookup,
            StorageStrategy.createLongStorage()
        )
            .withSelectionPredicate(SelectionPredicates.createSelectSingleAnything())
            .build()


        tracker.addObserver(object: SelectionTracker.SelectionObserver<Long>() {
            override fun onItemStateChanged(key: Long, selected: Boolean) {
                val holder = recyclerView.findViewHolderForItemId(key) as WorkspaceViewHolder
                holder.layout.isActivated = selected
                if(actionMode == null) {
                    startSupportActionMode(object: ActionMode.Callback {
                        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                            when(item?.itemId) {
                                R.id.settings -> {
                                    val intent = Intent(this@WorkspaceSelectorActivity, TextDisplaySettingsActivity::class.java)
                                    val workspaceId: Long = tracker.selection.iterator().next()

                                    val settings = SettingsBundle(workspaceId = workspaceId,
                                        workspaceSettings = workspaceAdapter.items.find {it.id == workspaceId}!!.textDisplaySettings!!)
                                    intent.putExtra("settingsBundle", settings.toJson())
                                    startActivityForResult(intent, WORKSPACE_SETTINGS_CHANGED)
                                }
                                R.id.deleteWorkspace -> {}
                                R.id.renameWorkspace -> {}
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
        dao.updateWorkspaces(workspaceAdapter.items)
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

    fun workspaceClicked(itemId: Long) {
        dao.updateWorkspaces(workspaceAdapter.items)
        resultIntent.putExtra("workspaceId", itemId)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val WORKSPACE_SETTINGS_CHANGED = 1
    }

}
