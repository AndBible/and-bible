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

package net.bible.service.device.speak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media.session.MediaButtonReceiver
import net.bible.android.platform.AndroidMediaSession
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.log.Log

class AndBibleMediaButtonReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        CommonUtils.initializeApp()
        Log.i("MediaButtons", "MediaButtonReceiver onReceive")
        AndroidMediaSession.current?.let { MediaButtonReceiver.handleIntent(it.ms, intent) }
    }
}
