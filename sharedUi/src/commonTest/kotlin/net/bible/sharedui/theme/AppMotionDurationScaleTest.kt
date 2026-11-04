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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

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

    /**
     * The collector runs on the test's own (queued) dispatcher and the test waits for each value instead of expecting
     * it synchronously: once any Compose scene has started the JVM-wide `GlobalSnapshotManager`, every global write
     * (here [AppMotionDurationScale.refresh]) is also applied from that manager's thread, so the change may reach the
     * flow from there, before or during this test's own [Snapshot.sendApplyNotifications]. With an unconfined collector
     * that thread ran the collector concurrently with the assertion (a red about one unfiltered jvmTest run in five).
     * A refresh that is not observable still fails: the receive never completes and the test times out.
     */
    @Test fun refreshMakesTheScaleObservableThroughSnapshotFlow() = runTest(timeout = 10.seconds) {
        var off = false
        val scale = AppMotionDurationScale({ off })
        val seen = Channel<Float>(Channel.UNLIMITED)
        val job = launch { snapshotFlow { scale.scaleFactor }.collect { seen.send(it) } }
        assertEquals(1f, seen.receive())
        off = true
        scale.refresh()
        Snapshot.sendApplyNotifications()
        assertEquals(0f, seen.receive())
        off = false
        scale.refresh()
        Snapshot.sendApplyNotifications()
        assertEquals(1f, seen.receive())
        job.cancel()
        assertTrue(seen.tryReceive().isFailure, "one value per refresh")
    }
}
