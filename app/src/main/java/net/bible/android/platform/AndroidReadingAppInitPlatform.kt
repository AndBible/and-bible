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

package net.bible.android.platform

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.service.startup.ExampleLabelNames
import net.bible.service.startup.ReadingAppInitPlatform
import net.bible.service.sword.SwordEnvironmentInitialisation

/** Android side of [net.bible.service.startup.ReadingAppInit]: logcat pipe, TTS and speak widget managers, resources. */
class AndroidReadingAppInitPlatform : ReadingAppInitPlatform {
    override suspend fun platformStart() {
        try {
            val pid = android.os.Process.myPid()
            Runtime.getRuntime().exec("logcat -P '$pid'").waitFor()
        } catch (e: Exception) {
            Log.w("AndroidReadingAppInit", "Logcat could not be run")
        }
        withContext(Dispatchers.Main) { CommonUtils.startPlatformServices() }
    }

    override val ioDispatcher get() = Dispatchers.IO

    override val exampleLabelNames: ExampleLabelNames
        get() = ExampleLabelNames(
            red = application.getString(R.string.label_red),
            green = application.getString(R.string.label_green),
            blue = application.getString(R.string.label_blue),
            underline = application.getString(R.string.label_underline),
            salvation = application.getString(R.string.label_salvation),
            migratedMyNotes = application.getString(R.string.migrated_my_notes),
        )

    override fun workspaceName(n: Int): String = application.getString(R.string.workspace_number, n)

    override fun sweepOrphanSearchIndexes() = SwordEnvironmentInitialisation.deleteOrphanLegacyIndexesInBackground()
}
