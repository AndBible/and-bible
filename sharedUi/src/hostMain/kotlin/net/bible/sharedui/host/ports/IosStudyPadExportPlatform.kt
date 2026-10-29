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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.host.ports

import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedui.app.bookmark.StudyPadExportPlatform
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.service.db.exportStudyPads
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import org.koin.mp.KoinPlatform

/** Uses the same archive writer and save-or-share flow as Android, with the iOS backup host. */
@kotlin.native.HiddenFromObjC
class IosStudyPadExportPlatform(
    private val backupHost: () -> BackupHost = { KoinPlatform.getKoin().get() },
    private val backupControl: () -> BackupControl = { KoinPlatform.getKoin().get() },
    private val strings: () -> CoreStrings = { KoinPlatform.getKoin().get() },
    private val environment: () -> AppEnvironment = { KoinPlatform.getKoin().get() },
) : StudyPadExportPlatform {
    override suspend fun export(labels: List<BookmarkEntities.Label>, chooseDestination: suspend () -> SaveOrShare?) {
        exportStudyPads(
            labels = labels, host = backupHost(), strings = strings(), environment = environment(),
            backupControl = backupControl(), chooseDestination = chooseDestination,
        )
    }
}
