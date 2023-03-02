/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.download

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import de.greenrobot.event.EventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.CustomRepositoriesBinding
import net.bible.android.activity.databinding.CustomRepositoryEditorBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.database.CustomRepository
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.CommonUtils.json
import net.bible.service.db.DatabaseContainer
import org.spongycastle.crypto.tls.TlsAEADCipher
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@Serializable
data class RepositoryData (
    var repository: CustomRepository,
    var delete: Boolean = false,
    var cancel: Boolean = false,
) {
    fun toJSON(): String = json.encodeToString(serializer(), this)

    companion object {
        fun fromJSON(str: String): RepositoryData = json.decodeFromString(serializer(), str)
    }
}
class CustomRepositoryEditor: CustomTitlebarActivityBase() {
    private lateinit var binding: CustomRepositoryEditorBinding
    private lateinit var data: RepositoryData
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = RepositoryData.fromJSON(intent.getStringExtra("data")!!)
        binding = CustomRepositoryEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateUI()
        buildActivityComponent().inject(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_repository_editor_options_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.delete -> delete()
            R.id.help -> help()
            android.R.id.home -> saveAndExit()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed")
        saveAndExit()
    }

    private fun saveAndExit() {
        updateData()
        val resultIntent = Intent()
        val repository = data.repository
        if (!data.delete && (repository.name.isEmpty() || repository.spec == null || repository.spec?.isEmpty() == true)) {
            data.cancel = true
            ABEventBus.post(ToastEvent(R.string.invalid_repository_not_saved))
        }

        resultIntent.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun updateData() = binding.run {
        data.repository.name = repositoryName.text.toString()
        data.repository.spec = repositorySpec.text.toString()
    }

    private fun updateUI() = binding.run {
        repositoryName.setText(data.repository.name)
        repositorySpec.setText(data.repository.spec)
    }

    private fun delete() = lifecycleScope.launch(Dispatchers.Main) {
        val result = suspendCoroutine {
            AlertDialog.Builder(this@CustomRepositoryEditor)
                .setMessage(getString(R.string.delete_custom_repository, data.repository.name))
                .setPositiveButton(R.string.yes) { _, _ -> it.resume(true) }
                .setNegativeButton(R.string.no) {_, _ -> it.resume(false)}
                .setCancelable(true)
                .create().show()
        }
        if(result) {
            data.delete = true
            saveAndExit()
        }
    }


    private fun help() {

    }
}

class CustomRepositories : ListActivityBase() {
    private lateinit var binding: CustomRepositoriesBinding
    private var customRepositories = arrayListOf<CustomRepository>()
    private var dao = DatabaseContainer.db.customRepositoryDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CustomRepositoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        listAdapter = createAdapter()
        reloadData()
    }

    private fun reloadData() {
        customRepositories.clear()
        customRepositories.addAll(dao.all())
        (listAdapter as ArrayAdapter<*>).notifyDataSetChanged()
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    private fun createAdapter(): ListAdapter {
        return object : ArrayAdapter<CustomRepository>(
            this,
            R.layout.custom_repository_item,
            R.id.titleText, customRepositories
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                view.findViewById<TextView>(R.id.titleText).text = customRepositories[position].name
                return view
            }
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val repo = customRepositories[position]
        val intent = Intent(this@CustomRepositories, CustomRepositoryEditor::class.java)
        intent.putExtra("data", RepositoryData(repo).toJSON())
        lifecycleScope.launch {
            val result = awaitIntent(intent)
            val data = RepositoryData.fromJSON(result.resultData.getStringExtra("data")!!)
            handleResult(data)
        }
    }

    private fun handleResult(data: RepositoryData) {
        if(data.cancel) {
            return
        } else if(data.delete) {
            if(data.repository.id != 0L) {
                dao.delete(data.repository)
            }
        } else {
            dao.upsert(data.repository)
        }
        reloadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_repositories_options_menu, menu)
        return true
    }

    private fun help() {

    }

    private fun newItem() = lifecycleScope.launch {
        val intent = Intent(this@CustomRepositories, CustomRepositoryEditor::class.java)
        intent.putExtra("data", RepositoryData(CustomRepository()).toJSON())
        val result = awaitIntent(intent)
        val data = RepositoryData.fromJSON(result.resultData.getStringExtra("data")!!)
        handleResult(data)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.help -> help()
            R.id.newItem -> newItem()
            android.R.id.home -> finish()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    companion object {
        private const val TAG = "CustomRepositories"
    }
}
