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

package net.bible.android.view.activity.readingplan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.service.readingplan.OneDaysReadingsDto
import net.bible.sharedcore.readingplan.DayEntry
import net.bible.sharedcore.readingplan.DailyReadingListController
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.readingplan.DailyReadingListScreen
import org.koin.android.ext.android.inject

/** Compose host for the day chooser — the new-path twin of the classic [DailyReadingList]. */
class DailyReadingListComposeActivity : ActivityBase() {
    private val readingPlanControl: ReadingPlanControl by inject()

    private fun primaryOf(dto: OneDaysReadingsDto): String =
        if (dto.isDateBasedPlan && dto.readingDate != null) dto.readingDateString else dto.dayDesc

    private val controller by lazy {
        DailyReadingListController(
            loadDays = {
                readingPlanControl.currentPlansReadingList.map { DayEntry(it.day, primaryOf(it), it.readingsDesc) }
            },
            onSelect = { day ->
                setResult(Activity.RESULT_OK, Intent(day.toString()))
                finish()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = getString(R.string.rdg_plan_title)
        ABEventBus.register(this) { onMain<ReadingPlansUpdatedViaSyncEvent> { controller.load() } }
        setContent {
            AbAppTheme {
                    val days by controller.days.collectAsState()
                    val error by controller.error.collectAsState()
                    DailyReadingListScreen(
                        title = title,
                        days = days,
                        error = error,
                        onSelect = controller::select,
                        onDismissError = controller::dismissError,
                        onNavigateUp = { finish() },
                    )
            }
        }
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }
}
