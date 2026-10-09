/*
 * Copyright (c) 2021-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query

@Entity
class BooleanSetting(
    @PrimaryKey val key: String,
    var value: Boolean,
)

@Entity
class LongSetting(
    @PrimaryKey val key: String,
    var value: Long,
)

@Entity
class StringSetting(
    @PrimaryKey val key: String,
    var value: String,
)

@Entity
class DoubleSetting(
    @PrimaryKey val key: String,
    var value: Double,
)

@Dao
interface BooleanSettingDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE) suspend fun insertOrUpdate(value: BooleanSetting)
    @Query("DELETE FROM BooleanSetting WHERE `key`=:key") suspend fun delete(key: String)
    @Query("SELECT * FROM BooleanSetting WHERE `key`=:key") suspend fun byKey(key: String): BooleanSetting?
    @Query("SELECT * FROM BooleanSetting") suspend fun all(): List<BooleanSetting>

    suspend fun get(key: String, default_: Boolean = false) = byKey(key)?.value ?: default_

    suspend fun set(key: String, value: Boolean?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(BooleanSetting(key, value))
        }
    }
}


@Dao
interface LongSettingDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE) suspend fun insertOrUpdate(value: LongSetting)
    @Query("DELETE FROM LongSetting WHERE `key`=:key") suspend fun delete(key: String)
    @Query("SELECT * FROM LongSetting WHERE `key`=:key") suspend fun byKey(key: String): LongSetting?
    @Query("SELECT * FROM LongSetting") suspend fun all(): List<LongSetting>

    suspend fun get(key: String, default_: Long) = byKey(key)?.value ?: default_

    suspend fun set(key: String, value: Long?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(LongSetting(key, value))
        }
    }
}

@Dao
interface StringSettingDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE) suspend fun insertOrUpdate(value: StringSetting)
    @Query("DELETE FROM StringSetting WHERE `key`=:key") suspend fun delete(key: String)
    @Query("SELECT * FROM StringSetting WHERE `key`=:key") suspend fun byKey(key: String): StringSetting?
    @Query("SELECT * FROM StringSetting") suspend fun all(): List<StringSetting>

    suspend fun get(key: String, default_: String?) = byKey(key)?.value ?: default_

    suspend fun set(key: String, value: String?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(StringSetting(key, value))
        }
    }
}

@Dao
interface DoubleSettingDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE) suspend fun insertOrUpdate(value: DoubleSetting)
    @Query("DELETE FROM DoubleSetting WHERE `key`=:key") suspend fun delete(key: String)
    @Query("SELECT * FROM DoubleSetting WHERE `key`=:key") suspend fun byKey(key: String): DoubleSetting?
    @Query("SELECT * FROM DoubleSetting") suspend fun all(): List<DoubleSetting>

    suspend fun get(key: String, default_: Double) = byKey(key)?.value ?: default_

    suspend fun set(key: String, value: Double?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(DoubleSetting(key, value))
        }
    }
}
