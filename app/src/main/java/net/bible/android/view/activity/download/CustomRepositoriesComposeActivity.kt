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
package net.bible.android.view.activity.download

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.sharedcore.download.CustomRepositoryController
import net.bible.sharedcore.download.CustomRepositoryData
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.download.CustomRepositoriesScreen
import org.koin.android.ext.android.inject

/**
 * Compose host for the custom-repositories list -- the new-path twin of classic [CustomRepositories].
 * Drives the shared [CustomRepositoryController] over [CustomRepositoryService] and renders
 * [CustomRepositoriesScreen].
 *
 * Row taps / "create" both navigate to the editor (routed through [ScreenLauncher] --
 * [CustomRepositoryEditorComposeActivity] or classic [CustomRepositoryEditor], per `use_compose_ui`)
 * carrying the SAME classic `"data"`=[RepositoryData] JSON extra ([toRepositoryData]) either editor
 * expects, so the two are interchangeable. The returned [RepositoryResult] ([toRepositoryResult])
 * is fed straight to [CustomRepositoryController.applyResult] (insert/update/delete/cancel); a
 * rejected duplicate-name upsert posts a [ToastEvent] (classic `handleResult`'s
 * `duplicate_custom_repository` toast).
 */
class CustomRepositoriesComposeActivity : ActivityBase() {
    private val service: CustomRepositoryService by inject()

    private val controller by lazy {
        CustomRepositoryController(service, lifecycleScope).apply {
            onDuplicate = { name ->
                ABEventBus.post(ToastEvent(getString(R.string.duplicate_custom_repository, name)))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AbAppTheme {
                    val state by controller.state.collectAsState()
                    CustomRepositoriesScreen(
                        state = state,
                        onRowClick = { id -> openEditorForRow(id) },
                        onCreate = { openEditor(null) },
                        onUp = { finish() },
                    )
            }
        }
    }

    /** Classic `onListItemClick`: look up the full row (the list screen only carries id/name/
     *  description) and open the editor pre-filled with it. */
    private fun openEditorForRow(id: Long) {
        lifecycleScope.launch {
            val repo = service.list().find { it.id == id }
            openEditor(repo)
        }
    }

    /** [repo] null = classic `newItem()` (a blank `RepositoryData()`); non-null = classic
     *  `onListItemClick` (`RepositoryData(repo)`). Both carry the SAME `"data"` extra shape, so the
     *  launched editor -- Compose or classic -- reads it identically. */
    private fun openEditor(repo: CustomRepositoryData?) {
        val json = RepositoryResult(repository = repo).toRepositoryData().toJSON()
        val intent = ScreenLauncher.intentFor(this, Screen.CustomRepositoryEditor).putExtra("data", json)
        lifecycleScope.launch {
            val result = awaitIntent(intent)
            val data = RepositoryData.fromJSON(result.data?.getStringExtra("data")!!)
            controller.applyResult(data.toRepositoryResult())
        }
    }
}
