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
import android.text.format.DateFormat.format
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.SharedActivityState.Companion.currentWorkspaceName
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.history.HistoryController
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.history.HistoryScreen
import org.koin.android.ext.android.inject

/** Compose host for History — the new-path twin of the classic [History]. */
class HistoryComposeActivity : ActivityBase() {
    private val historyManager: HistoryManager by inject()
    private val windowControl: WindowControl by inject()

    private var historyItems: List<HistoryItem> = emptyList()

    private val controller by lazy {
        HistoryController(
            loadEntries = {
                historyItems = historyManager.getHistory(windowControl.activeWindow.id)
                historyItems.mapIndexed { index, item ->
                    HistoryEntry(
                        id = index,
                        title = item.description.toString(),
                        timestamp = format("h:mm a, E d MMM ", item.createdAt).toString(),
                    )
                }
            },
            onRevert = { id ->
                historyItems[id].revertTo()
                setResult(Activity.RESULT_OK, Intent())
                finish()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = getString(R.string.history_for, currentWorkspaceName, windowControl.activeWindowPosition + 1)
        setContent {
            AbAppTheme {
                    val entries by controller.entries.collectAsState()
                    val error by controller.error.collectAsState()
                    HistoryScreen(
                        title = title,
                        entries = entries,
                        error = error,
                        onSelect = controller::onSelect,
                        onDismissError = controller::dismissError,
                    )
            }
        }
    }
}
