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
import net.bible.android.activity.R
import net.bible.android.activity.databinding.CustomRepositoriesBinding
import net.bible.android.database.CustomRepository
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.db.DatabaseContainer

class CustomRepositories : ListActivityBase() {
    private lateinit var binding: CustomRepositoriesBinding
    private var customRepositories = arrayListOf<CustomRepository>()
    private var dao = DatabaseContainer.db.customRepositoryDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CustomRepositoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        customRepositories.addAll(dao.all())
        listAdapter = createAdapter()
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_repositories_options_menu, menu)
        return true
    }

    private fun help() {}

    private fun newItem() {

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
