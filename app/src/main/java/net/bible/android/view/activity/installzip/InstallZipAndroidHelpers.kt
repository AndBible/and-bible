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
package net.bible.android.view.activity.installzip

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import net.bible.android.activity.R

// (Not named InstallZipFlow.kt: the :sharedUi file of that name would share its JVM facade class.)
// The InstallZip state machine (`InstallZipFlow`) moved to :sharedUi (I2 Stream A Task 12, same FQN); what stays
// here is the Android half it reaches through `AndroidInstallZipHostPlatform`.

/** Classic `InstallZip.getDisplayName`, moved verbatim from A49. */
internal fun installZipDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.isLast) return null
        it.moveToFirst()
        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx < 0) null else it.getString(idx)
    }

/** Classic `InstallZip.getFileFromUserAndInstall`'s supported-formats blurb, moved verbatim from A49. */
internal fun installZipFormatsText(context: Context): String {
    val zip = context.getString(R.string.format_zip, context.getString(R.string.app_name_andbible))
    val myBible = context.getString(R.string.format_mybible)
    val mySword = context.getString(R.string.format_mysword)
    val eSword = context.getString(R.string.format_esword)
    val epub = context.getString(R.string.format_epub)
    val studyPads = context.getString(R.string.format_studypads)
    val ttf = context.getString(R.string.format_ttf)
    val csvPrompts = context.getString(R.string.format_csv_prompts)
    return context.getString(R.string.choose_file, context.getString(R.string.app_name_andbible)) + " \n\n" +
        context.getString(R.string.supported_formats, "$zip, $myBible, $mySword, $eSword, $epub, $ttf, $csvPrompts, $studyPads")
}
