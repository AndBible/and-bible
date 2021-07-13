/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

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
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertOrUpdate(value: BooleanSetting)
    @Query("DELETE FROM BooleanSetting WHERE `key`=:key") fun delete(key: String)
    @Query("SELECT * FROM BooleanSetting WHERE `key`=:key") fun byKey(key: String): BooleanSetting?

    fun get(key: String, default_: Boolean = false) = byKey(key)?.value ?: default_

    fun set(key: String, value: Boolean?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(BooleanSetting(key, value))
        }
    }
}


@Dao
interface LongSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertOrUpdate(value: LongSetting)
    @Query("DELETE FROM LongSetting WHERE `key`=:key") fun delete(key: String)
    @Query("SELECT * FROM LongSetting WHERE `key`=:key") fun byKey(key: String): LongSetting?

    fun get(key: String, default_: Long) = byKey(key)?.value ?: default_

    fun set(key: String, value: Long?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(LongSetting(key, value))
        }
    }
}

@Dao
interface StringSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertOrUpdate(value: StringSetting)
    @Query("DELETE FROM StringSetting WHERE `key`=:key") fun delete(key: String)
    @Query("SELECT * FROM StringSetting WHERE `key`=:key") fun byKey(key: String): StringSetting?

    fun get(key: String, default_: String?) = byKey(key)?.value ?: default_

    fun set(key: String, value: String?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(StringSetting(key, value))
        }
    }
}

@Dao
interface DoubleSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertOrUpdate(value: DoubleSetting)
    @Query("DELETE FROM DoubleSetting WHERE `key`=:key") fun delete(key: String)
    @Query("SELECT * FROM DoubleSetting WHERE `key`=:key") fun byKey(key: String): DoubleSetting?

    fun get(key: String, default_: Double) = byKey(key)?.value ?: default_

    fun set(key: String, value: Double?) {
        if(value == null) {
            delete(key);

        } else {
            insertOrUpdate(DoubleSetting(key, value))
        }
    }
}
