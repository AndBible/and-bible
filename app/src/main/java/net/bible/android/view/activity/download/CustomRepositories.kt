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

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.text.TextUtils.concat
import android.text.method.LinkMovementMethod
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
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.activity.databinding.CustomRepositoriesBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.database.CustomRepository
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.htmlToSpan
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.install.InstallManager


const val customRepositoriesWikiUrl = "https://github.com/AndBible/and-bible/wiki/Custom-repositories"


class CustomRepositories : ListActivityBase() {
    private lateinit var binding: CustomRepositoriesBinding
    private var customRepositories = arrayListOf<CustomRepository>()
    private val dao get() = DatabaseContainer.db.customRepositoryDao()

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
                view.findViewById<TextView>(R.id.descriptionText).text = customRepositories[position].description
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
        }

        val repository = data.repository!!

        if(data.delete) {
            if(repository.id != 0L) {
                dao.delete(repository)
            }
        } else {
            if(InstallManager().installers.keys.find { it == repository.name } != null) {
                ABEventBus.post(ToastEvent(getString(R.string.duplicate_custom_repository, repository.name)))
            }
            else if(repository.id != 0L) {
                dao.update(repository)
            } else {
                try {
                    dao.insert(repository)
                } catch (e: SQLiteConstraintException) {
                    ABEventBus.post(ToastEvent(getString(R.string.duplicate_custom_repository, repository.name)))
                    Log.e(TAG, "Constraint exception", e)
                }
            }
        }
        reloadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_repositories_options_menu, menu)
        return true
    }

    private fun help() {
        val s0 = getString(R.string.custom_repositories_help0)
        val s3 = getString(R.string.wiki_page)
        val urlLink = """<a href="$customRepositoriesWikiUrl">$s3</a>"""
        val s2 = htmlToSpan(getString(R.string.custom_repositories_help2, urlLink))
        val s  = concat(
            s0, "\n\n", s2
        )
        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(R.string.custom_repositories)
            .setMessage(s)
            .create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
    private fun newItem() = lifecycleScope.launch {
        val intent = Intent(this@CustomRepositories, CustomRepositoryEditor::class.java)
        intent.putExtra("data", RepositoryData().toJSON())
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

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed")
        finish()
    }

    companion object {
        private const val TAG = "CustomRepositories"
    }
}
