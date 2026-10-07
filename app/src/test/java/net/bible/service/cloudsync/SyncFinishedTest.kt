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

package net.bible.service.cloudsync

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [recordSyncFinished] stamps `globalLastSynchronized` and only then emits the `false` edge (ABEventBus phase 3, C4). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SyncFinishedTest {
    @Test fun theTimestampIsWrittenBeforeTheFalseEdge() {
        CommonUtils.settings.setLong("globalLastSynchronized", 0L)
        var seenAtEmit = -1L
        val subscription = CloudSync.runningChanged.subscribe { running ->
            if (!running) seenAtEmit = CommonUtils.settings.getLong("globalLastSynchronized", 0L)
        }
        try { recordSyncFinished(now = 1234L) } finally { subscription.cancel() }
        assertEquals(1234L, seenAtEmit)
    }

    @Test fun noReadingHostIsNeeded() {
        CommonUtils.settings.setLong("globalLastSynchronized", 0L)
        recordSyncFinished(now = 99L)
        assertEquals(99L, CommonUtils.settings.getLong("globalLastSynchronized", 0L))
    }
}
