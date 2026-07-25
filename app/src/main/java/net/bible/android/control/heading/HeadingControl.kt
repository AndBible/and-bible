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
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.control.heading

import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
import net.bible.android.database.headings.CustomHeading
import net.bible.android.database.headings.HeadingOverride
import net.bible.service.db.DatabaseContainer

class CustomHeadingsUpdatedEvent(val bookInitials: String)

object HeadingControl {
    private val dao get() = DatabaseContainer.instance.headingsDb.headingsDao()

    fun headingsForRange(bookInitials: String, startOrdinal: Int, endOrdinal: Int) =
        dao.customHeadingsForRange(bookInitials, startOrdinal, endOrdinal)

    fun overridesForRange(bookInitials: String, startOrdinal: Int, endOrdinal: Int) =
        dao.headingOverridesForRange(bookInitials, startOrdinal, endOrdinal)

    fun allHeadingsFor(bookInitials: String) = dao.allCustomHeadingsFor(bookInitials)

    fun allOverridesFor(bookInitials: String) = dao.allHeadingOverridesFor(bookInitials)

    fun addCustomHeading(bookInitials: String, v11n: String, ordinal: Int, level: Int, text: String) {
        dao.insert(CustomHeading(
            bookInitials = bookInitials,
            v11n = v11n,
            ordinal = ordinal,
            level = level.coerceIn(1, 6),
            text = text,
        ))
        ABEventBus.post(CustomHeadingsUpdatedEvent(bookInitials))
    }

    fun updateCustomHeading(id: IdType, level: Int, text: String) {
        val heading = dao.customHeadingById(id) ?: return
        heading.level = level.coerceIn(1, 6)
        heading.text = text
        dao.update(heading)
        ABEventBus.post(CustomHeadingsUpdatedEvent(heading.bookInitials))
    }

    fun deleteCustomHeading(id: IdType) {
        val heading = dao.customHeadingById(id) ?: return
        dao.deleteCustomHeading(id)
        ABEventBus.post(CustomHeadingsUpdatedEvent(heading.bookInitials))
    }

    fun setHeadingOverride(
        bookInitials: String,
        v11n: String,
        ordinal: Int,
        titleIndex: Int,
        newText: String?,
        newLevel: Int?,
        deleted: Boolean,
    ) {
        val sanitizedText = newText?.trim()?.ifEmpty { null }
        val sanitizedLevel = newLevel?.takeIf { it in 1..6 }
        if (sanitizedText == null && sanitizedLevel == null && !deleted) {
            dao.headingOverrideFor(bookInitials, ordinal, titleIndex)?.let { dao.deleteHeadingOverride(it.id) }
        } else {
            val override = dao.headingOverrideFor(bookInitials, ordinal, titleIndex)
                ?: HeadingOverride(
                    bookInitials = bookInitials,
                    v11n = v11n,
                    ordinal = ordinal,
                    titleIndex = titleIndex,
                )
            override.newText = sanitizedText
            override.newLevel = sanitizedLevel
            override.deleted = deleted
            if (dao.headingOverrideById(override.id) == null) {
                dao.insert(override)
            } else {
                dao.update(override)
            }
        }
        ABEventBus.post(CustomHeadingsUpdatedEvent(bookInitials))
    }

    fun removeHeadingOverride(id: IdType) {
        val override = dao.headingOverrideById(id) ?: return
        dao.deleteHeadingOverride(id)
        ABEventBus.post(CustomHeadingsUpdatedEvent(override.bookInitials))
    }
}
