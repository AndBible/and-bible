/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.common

import android.graphics.Color
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import androidx.appcompat.R as AppCompatR

/**
 * Generic search/filter helper for RecyclerView-based list activities.
 *
 * Maintains a filtered view of the source list. The adapter should read from [filteredItems]
 * instead of the source list directly.
 */
class RecyclerViewSearchHelper<T>(
    private val allItems: () -> List<T>,
    private val searchableText: (T) -> String,
    private val onFilterChanged: (isFiltering: Boolean) -> Unit = {}
) {
    private var _filteredItems: List<T>? = null
    private var currentQuery: String = ""

    val filteredItems: List<T> get() = _filteredItems ?: allItems()
    val isFiltering: Boolean get() = _filteredItems != null

    fun filter(query: String) {
        currentQuery = query.trim()
        if (currentQuery.isEmpty()) {
            _filteredItems = null
            onFilterChanged(false)
        } else {
            val lower = currentQuery.lowercase()
            _filteredItems = allItems().filter { searchableText(it).lowercase().contains(lower) }
            onFilterChanged(true)
        }
    }

    fun refresh() {
        if (isFiltering) filter(currentQuery)
    }
}

/**
 * Adds a SearchView to the action bar menu that filters a RecyclerView list via the given helper.
 */
fun <T> AppCompatActivity.setupRecyclerViewSearch(
    menu: Menu,
    helper: RecyclerViewSearchHelper<T>,
    adapter: RecyclerView.Adapter<*>,
    hintResId: Int = R.string.search,
    notifyAdapter: () -> Unit = { adapter.notifyDataSetChanged() }
): MenuItem {
    val searchItem = menu.add(Menu.NONE, Menu.NONE, 0, hintResId)
    searchItem.setIcon(R.drawable.ic_search_24dp)
    searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW or MenuItem.SHOW_AS_ACTION_IF_ROOM)

    val actionBarContext = supportActionBar?.themedContext ?: this
    val searchView = SearchView(actionBarContext).apply {
        queryHint = getString(hintResId)
        findViewById<EditText>(AppCompatR.id.search_src_text)?.apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(180, 255, 255, 255))
        }
        setIconifiedByDefault(false)
        findViewById<ImageView>(AppCompatR.id.search_mag_icon)?.apply {
            setImageDrawable(null)
            layoutParams = layoutParams.also { it.width = 0 }
        }
    }
    searchItem.actionView = searchView

    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            searchView.clearFocus()
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            helper.filter(newText ?: "")
            notifyAdapter()
            return true
        }
    })

    searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            helper.filter("")
            notifyAdapter()
            return true
        }
    })

    return searchItem
}
