/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.applyComposeHostWindowSetup
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade.readOsisFragment
import net.bible.sharedcore.navigation.ChooseDictionaryWordController
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.navigation.ChooseDictionaryWordScreen
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.jdom2.Element
import org.koin.android.ext.android.inject

/** Compose host for the dictionary word chooser — the new-path twin of classic [ChooseDictionaryWord]. */
class ChooseDictionaryWordComposeActivity : ActivityBase() {
    /**
     * This host's [ChooseDictionaryWordScreen] renders through `AbScaffold`, which owns the
     * system-bar insets -- so this host must not pad its content root too. See the
     * host-inset-ownership spec, section 3.2.
     */
    override val disableBaseSetupUi = true

    private val windowControl: WindowControl by inject()

    private val page get() = windowControl.activeWindowPageManager.currentDictionary

    /** Global key list, resolved off-main once; index into it is the [DictRow.keyId]. */
    private var keys: List<Key> = emptyList()

    private val controller by lazy {
        ChooseDictionaryWordController(
            onSelect = { keyId ->
                val key = keys.getOrNull(keyId.toIntOrNull() ?: -1) ?: return@ChooseDictionaryWordController
                // Classic ChooseDictionaryWord.itemSelected: shares ChooseGeneralBookKey's GenBookKey result shape.
                val intent = Intent()
                intent.putExtra("key", key.osisRef)
                intent.putExtra("book", page.currentDocument?.initials)
                intent.putExtra(ActivityResultKind.EXTRA, ActivityResultKind.GenBookKey.name)
                setResult(Activity.RESULT_OK, intent)
                finish()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyComposeHostWindowSetup()
        // Parity: no dictionary → finish immediately.
        if (page.currentDocument == null) { finish(); return }

        val title = getString(R.string.dictionary)
        val hint = getString(R.string.search)

        lifecycleScope.launch {
            try {
                keys = withContext(Dispatchers.IO) { page.cachedGlobalKeyList ?: emptyList() }
                controller.setAllRows(keys.mapIndexed { i, k -> DictRow(i.toString(), k.name) })
            } catch (e: Exception) {
                Log.e(TAG, "Error creating dictionary key list", e)
                controller.showError()
            }
        }

        setContent {
            AbAppTheme {
                    val loading by controller.loading.collectAsState()
                    val query by controller.query.collectAsState()
                    val rows by controller.rows.collectAsState()
                    val error by controller.error.collectAsState()
                    ChooseDictionaryWordScreen(
                        title = title,
                        hint = hint,
                        loading = loading,
                        query = query,
                        rows = rows,
                        error = error,
                        loadSnippet = { keyId -> withContext(Dispatchers.IO) { snippetFor(keyId) } },
                        onQueryChange = controller::setQuery,
                        onSelect = controller::select,
                        onDismissError = controller::dismissError,
                        onNavigateUp = { finish() },
                    )
            }
        }
    }

    /** Port of classic KeyInfo.toString()/getEntrySnippet — the "key - snippet" text, minus the "key - " prefix. */
    private fun snippetFor(keyId: String): String {
        val key = keys.getOrNull(keyId.toIntOrNull() ?: -1) ?: return ""
        val book: Book = page.currentDocument ?: return ""
        val text = try { readOsisFragment(book, key) } catch (e: OsisError) { e.xml }
        return getEntrySnippet(text, key.toString())
    }

    private fun getEntrySnippet(text: Element, key: String): String {
        text.removeChild("title")
        val entry = text.getChild("entryFree") ?: return cleanUpSnippet(text.value, key)
        val greekOrHebrew = entry.getChildren("orth")?.map { it.text }?.filter { it != "" }?.joinToString(" - ") ?: ""
        if (greekOrHebrew != "") return greekOrHebrew
        return cleanUpSnippet(entry.value, key)
    }

    private fun cleanUpSnippet(snippet: String, key: String): String {
        var noNewLines = snippet.replace('\n', ' ')
        if (noNewLines.startsWith(key)) noNewLines = noNewLines.substring(key.length)
        return maxLettersWholeWords(noNewLines)
    }

    private fun maxLettersWholeWords(text: String, max: Int = 50): String {
        val words = text.split(' ').toMutableList()
        var result = ""
        while (result.length < max && words.isNotEmpty()) { result += words[0] + ' '; words.removeAt(0) }
        val append = if (words.isNotEmpty()) "..." else ""
        return "$result$append"
    }

    companion object {
        private const val TAG = "ChooseDictionaryWordCompose"
    }
}
