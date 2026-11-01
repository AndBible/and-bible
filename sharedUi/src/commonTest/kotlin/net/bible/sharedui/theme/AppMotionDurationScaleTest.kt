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

package net.bible.sharedui.theme

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppMotionDurationScaleTest {
    @Test fun zeroWhileTheSettingIsOnEvenIfTheSystemSlowsAnimationsDown() {
        assertEquals(0f, AppMotionDurationScale({ true }) { 5f }.scaleFactor)
    }

    @Test fun followsTheSystemScaleWhileTheSettingIsOff() {
        assertEquals(1f, AppMotionDurationScale({ false }).scaleFactor)
        assertEquals(0.5f, AppMotionDurationScale({ false }) { 0.5f }.scaleFactor)
    }

    @Test fun readsTheSettingLiveSoAChangeAppliesToTheNextAnimation() {
        var off = false
        val scale = AppMotionDurationScale({ off })
        assertEquals(1f, scale.scaleFactor)
        off = true
        assertEquals(0f, scale.scaleFactor)
        off = false
        assertEquals(1f, scale.scaleFactor)
    }

    @Test fun aFailingSettingsReadKeepsAnimationsOn() {
        assertEquals(1f, AppMotionDurationScale({ error("settings not ready") }).scaleFactor)
    }

    @Test fun refreshMakesTheScaleObservableThroughSnapshotFlow() = runTest {
        var off = false
        val scale = AppMotionDurationScale({ off })
        val seen = mutableListOf<Float>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { snapshotFlow { scale.scaleFactor }.toList(seen) }
        assertEquals(listOf(1f), seen)
        off = true
        scale.refresh()
        Snapshot.sendApplyNotifications()
        assertEquals(listOf(1f, 0f), seen)
        off = false
        scale.refresh()
        Snapshot.sendApplyNotifications()
        assertEquals(listOf(1f, 0f, 1f), seen)
        job.cancel()
    }
}
