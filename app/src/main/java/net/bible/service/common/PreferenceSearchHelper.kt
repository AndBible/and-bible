/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import android.app.Activity
import android.graphics.Color
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.appcompat.R as AppCompatR
import net.bible.android.activity.R

class PreferenceSearchHelper {
    private val savedVisibility = mutableMapOf<String, Boolean>()
    private val savedCategoryVisibility = mutableMapOf<String, Boolean>()
    private var initialized = false

    fun filterPreferences(fragment: PreferenceFragmentCompat, query: String) {
        val screen = fragment.preferenceScreen ?: return
        val trimmedQuery = query.trim()

        if (!initialized) {
            saveVisibility(screen)
            initialized = true
        }

        if (trimmedQuery.isEmpty()) {
            restoreVisibility(screen)
            return
        }

        val lowerQuery = trimmedQuery.lowercase()

        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                var anyChildVisible = false
                for (j in 0 until pref.preferenceCount) {
                    val child = pref.getPreference(j)
                    val originallyVisible = savedVisibility[child.key ?: child.title?.toString() ?: "$i-$j"] ?: true
                    if (!originallyVisible) {
                        child.isVisible = false
                        continue
                    }
                    val matches = matchesQuery(child, lowerQuery)
                    child.isVisible = matches
                    if (matches) anyChildVisible = true
                }
                pref.isVisible = anyChildVisible
            } else {
                val originallyVisible = savedVisibility[pref.key ?: pref.title?.toString() ?: "$i"] ?: true
                pref.isVisible = originallyVisible && matchesQuery(pref, lowerQuery)
            }
        }
    }

    private fun matchesQuery(pref: Preference, lowerQuery: String): Boolean {
        val title = pref.title?.toString()?.lowercase() ?: ""
        val summary = pref.summary?.toString()?.lowercase() ?: ""
        return title.contains(lowerQuery) || summary.contains(lowerQuery)
    }

    private fun saveVisibility(screen: PreferenceGroup) {
        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                savedCategoryVisibility[pref.key ?: pref.title?.toString() ?: "$i"] = pref.isVisible
                for (j in 0 until pref.preferenceCount) {
                    val child = pref.getPreference(j)
                    savedVisibility[child.key ?: child.title?.toString() ?: "$i-$j"] = child.isVisible
                }
            } else {
                savedVisibility[pref.key ?: pref.title?.toString() ?: "$i"] = pref.isVisible
            }
        }
    }

    private fun restoreVisibility(screen: PreferenceGroup) {
        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                pref.isVisible = savedCategoryVisibility[pref.key ?: pref.title?.toString() ?: "$i"] ?: true
                for (j in 0 until pref.preferenceCount) {
                    val child = pref.getPreference(j)
                    child.isVisible = savedVisibility[child.key ?: child.title?.toString() ?: "$i-$j"] ?: true
                }
            } else {
                pref.isVisible = savedVisibility[pref.key ?: pref.title?.toString() ?: "$i"] ?: true
            }
        }
    }
}

fun PreferenceFragmentCompat.setupPreferenceSearch(menu: Menu, activity: Activity): PreferenceSearchHelper {
    val helper = PreferenceSearchHelper()

    val searchItem = menu.add(Menu.NONE, Menu.NONE, 0, R.string.search_settings)
    searchItem.setIcon(R.drawable.ic_search_24dp)
    searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW or MenuItem.SHOW_AS_ACTION_IF_ROOM)

    val actionBarContext = (activity as? AppCompatActivity)?.supportActionBar?.themedContext ?: activity
    val searchView = SearchView(actionBarContext)
    searchView.queryHint = activity.getString(R.string.search_settings)
    searchView.findViewById<EditText>(AppCompatR.id.search_src_text)?.apply {
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(180, 255, 255, 255))
    }
    searchView.setIconifiedByDefault(false)
    searchView.findViewById<ImageView>(AppCompatR.id.search_mag_icon)?.apply {
        setImageDrawable(null)
        layoutParams = layoutParams.also { it.width = 0 }
    }
    searchItem.actionView = searchView

    val fragment = this
    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            searchView.clearFocus()
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            helper.filterPreferences(fragment, newText ?: "")
            return true
        }
    })

    searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            helper.filterPreferences(fragment, "")
            return true
        }
    })

    return helper
}
