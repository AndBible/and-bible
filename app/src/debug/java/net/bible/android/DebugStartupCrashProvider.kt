/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Debug builds only (#4176): a content provider that throws while the framework installs it, i.e. before
 * `Application.onCreate` -- the same place as build 1279's crash in androidx.startup's `InitializationProvider` --
 * when the system property `debug.andbible.crash_at_startup` is `1`
 * (`adb shell setprop debug.andbible.crash_at_startup 1`, then start the app; `setprop ... 0` ends it).
 * Used to check the crash safe mode on a device.
 */
class DebugStartupCrashProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val value = runCatching {
            @SuppressLint("PrivateApi")
            val sp = Class.forName("android.os.SystemProperties")
            sp.getMethod("get", String::class.java).invoke(null, "debug.andbible.crash_at_startup") as String?
        }.getOrNull()
        if (value == "1") throw IllegalStateException("Debug startup crash (debug.andbible.crash_at_startup=1)")
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
