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

import net.bible.android.database.progress.GlobalReadingProgressSettings
import net.bible.service.db.DatabaseContainer

/**
 * Accessor for global reading progress settings stored in the syncable ProgressDatabase.
 * Each property reads/writes the [GlobalReadingProgressSettings] singleton row.
 */
object ReadingProgressSettings {
    private val dao get() = DatabaseContainer.instance.progressDb.globalReadingProgressSettingsDao()

    private fun getOrDefault(): GlobalReadingProgressSettings = dao.get() ?: GlobalReadingProgressSettings()

    private fun update(transform: GlobalReadingProgressSettings.() -> GlobalReadingProgressSettings) {
        dao.set(getOrDefault().transform())
    }

    var autoMarkMemorized: Boolean
        get() = getOrDefault().autoMarkMemorized
        set(value) = update { copy(autoMarkMemorized = value) }
}
