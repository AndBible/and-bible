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
package net.bible.android.view.activity.navigation.genbookmap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.sword.nameWithoutDocument
import net.bible.sharedcore.navigation.ChooseMapKeyController
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.navigation.ChooseMapKeyScreen
import org.crosswire.jsword.passage.Key
import org.koin.android.ext.android.inject

/** Compose host for the map key chooser — the new-path twin of classic [ChooseMapKey]. */
class ChooseMapKeyComposeActivity : ActivityBase() {
    private val windowControl: WindowControl by inject()

    private val page: CurrentMapPage
        get() = windowControl.activeWindowPageManager.currentMap

    private var keys: List<Key> = emptyList()

    /** Reproduce classic [ChooseMapKey.itemSelected] result Intent (className = classic class). */
    private fun buildResult(key: Key?): Intent =
        Intent(this, ChooseMapKey::class.java).apply {
            putExtra("key", key?.osisRef)
            putExtra("book", page.currentDocument?.initials)
        }

    private val controller by lazy {
        ChooseMapKeyController(
            loadRows = { keys.mapIndexed { i, k -> KeyRow(i.toString(), k.nameWithoutDocument) } },
            currentRow = { page.key?.let { cur -> keys.indexOf(cur).takeIf { it >= 0 }?.toString() } },
            onSelect = { keyId ->
                val key = keys.getOrNull(keyId.toIntOrNull() ?: -1)
                setResult(Activity.RESULT_OK, buildResult(key))
                finish()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keys = page.keyChooserKeys()
        if (keys.isEmpty()) {
            setResult(Activity.RESULT_OK, buildResult(null))
            finish()
            return
        }
        val title = getString(R.string.doc_type_map)
        setContent {
            AbAppTheme {
                    val rows by controller.rows.collectAsState()
                    val currentKeyId by controller.currentKeyId.collectAsState()
                    val error by controller.error.collectAsState()
                    ChooseMapKeyScreen(
                        title = title,
                        rows = rows,
                        currentKeyId = currentKeyId,
                        error = error,
                        onSelect = controller::select,
                        onDismissError = controller::dismissError,
                        onNavigateUp = { finish() },
                    )
            }
        }
    }
}
