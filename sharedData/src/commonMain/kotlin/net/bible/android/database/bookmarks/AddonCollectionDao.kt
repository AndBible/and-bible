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

package net.bible.android.database.bookmarks

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonCollectionModule
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonCollectionRow
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonLabelLocalState
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonLinkLocalState

/** Ownership and per-device state of add-on collection rows ([AddonCollectionEntities]). */
@Dao
interface AddonCollectionDao {
    @Query("SELECT * FROM AddonCollectionModule WHERE moduleInitials = :initials")
    suspend fun module(initials: String): AddonCollectionModule?

    @Query("SELECT * FROM AddonCollectionModule ORDER BY moduleInitials")
    suspend fun allModules(): List<AddonCollectionModule>

    @Upsert
    suspend fun upsertModule(module: AddonCollectionModule)

    @Query("DELETE FROM AddonCollectionModule WHERE moduleInitials = :initials")
    suspend fun deleteModule(initials: String)

    @Query("SELECT * FROM AddonCollectionRow WHERE moduleInitials = :initials")
    suspend fun rowsOf(initials: String): List<AddonCollectionRow>

    @Query("DELETE FROM AddonCollectionRow WHERE moduleInitials = :initials")
    suspend fun deleteRowsOf(initials: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(rows: List<AddonCollectionRow>)

    @Query("SELECT COUNT(*) > 0 FROM AddonCollectionRow WHERE tableName = 'Label' AND id1 = :id")
    suspend fun isAddonLabel(id: IdType): Boolean

    @Query("SELECT COUNT(*) > 0 FROM AddonCollectionRow WHERE tableName IN ('BibleBookmark', 'GenericBookmark') AND id1 = :id")
    suspend fun isAddonBookmark(id: IdType): Boolean

    @Query("SELECT COUNT(*) > 0 FROM AddonCollectionRow WHERE tableName = 'StudyPadTextEntry' AND id1 = :id")
    suspend fun isAddonStudyPadEntry(id: IdType): Boolean

    /** Whether the label link (bookmark, label) of [tableName] (`BibleBookmarkToLabel`/`GenericBookmarkToLabel`) is an add-on row. */
    @Query("SELECT COUNT(*) > 0 FROM AddonCollectionRow WHERE tableName = :tableName AND id1 = :bookmarkId AND id2 = :labelId")
    suspend fun isAddonLink(tableName: String, bookmarkId: IdType, labelId: IdType): Boolean

    @Query("SELECT moduleInitials FROM AddonCollectionRow WHERE tableName = 'Label' AND id1 = :id")
    suspend fun moduleOfLabel(id: IdType): String?

    @Query("SELECT moduleInitials FROM AddonCollectionRow WHERE tableName IN ('BibleBookmark', 'GenericBookmark') AND id1 = :id LIMIT 1")
    suspend fun moduleOfBookmark(id: IdType): String?

    @Query("SELECT id1 FROM AddonCollectionRow WHERE tableName = 'Label'")
    suspend fun addonLabelIdList(): List<IdType>

    /** Label id -> module initials for every add-on label. */
    @Query("SELECT id1 AS labelId, moduleInitials FROM AddonCollectionRow WHERE tableName = 'Label'")
    suspend fun addonLabelModules(): List<AddonLabelModule>

    @Query("SELECT id1 FROM AddonCollectionRow WHERE tableName IN ('BibleBookmark', 'GenericBookmark')")
    suspend fun addonBookmarkIdList(): List<IdType>

    @Query("SELECT * FROM AddonLabelLocalState WHERE labelId = :labelId")
    suspend fun labelLocalState(labelId: IdType): AddonLabelLocalState?

    @Query("SELECT labelId FROM AddonLabelLocalState WHERE favourite = 1")
    suspend fun favouriteAddonLabelIds(): List<IdType>

    @Upsert
    suspend fun upsertLabelLocalState(state: AddonLabelLocalState)

    @Query("SELECT * FROM AddonLinkLocalState WHERE bookmarkId = :bookmarkId AND labelId = :labelId")
    suspend fun linkLocalState(bookmarkId: IdType, labelId: IdType): AddonLinkLocalState?

    @Query("SELECT * FROM AddonLinkLocalState WHERE labelId = :labelId")
    suspend fun linkLocalStates(labelId: IdType): List<AddonLinkLocalState>

    @Upsert
    suspend fun upsertLinkLocalState(state: AddonLinkLocalState)
}

suspend fun AddonCollectionDao.addonLabelIds(): Set<IdType> = addonLabelIdList().toSet()

/** Row of [AddonCollectionDao.addonLabelModules]. */
data class AddonLabelModule(val labelId: IdType, val moduleInitials: String)
