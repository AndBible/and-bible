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

package net.bible.service.common

import net.bible.android.activity.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLookTest {
    /**
     * The unit tests compile against the plain debug variant, so this pins the switch's default:
     * a build without -PvideoLook / VIDEO_LOOK keeps every debug trait. (It fails on purpose if
     * the suite itself is run with the switch on.)
     */
    @Test
    fun switchDefaultsOff() {
        assertFalse(BuildConfig.VIDEO_LOOK)
        assertFalse(VideoLook.enabled)
        assertTrue(VideoLook.showsDebugTraits(debuggable = true))
        assertEquals(BuildConfig.BUILD_TYPE, VideoLook.displayedBuildType(BuildConfig.BUILD_TYPE))
    }

    @Test
    fun videoLookHidesDebugTraitsOfADebuggableBuild() {
        assertFalse(VideoLook.showsDebugTraits(debuggable = true, videoLook = true))
        assertFalse(VideoLook.showsDebugTraits(debuggable = false, videoLook = true))
        assertTrue(VideoLook.showsDebugTraits(debuggable = true, videoLook = false))
        assertFalse(VideoLook.showsDebugTraits(debuggable = false, videoLook = false))
    }

    @Test
    fun versionStringNamesReleaseOnlyWithVideoLook() {
        assertEquals("release", VideoLook.displayedBuildType("debug", videoLook = true))
        assertEquals("debug", VideoLook.displayedBuildType("debug", videoLook = false))
        assertEquals("release", VideoLook.displayedBuildType("release", videoLook = false))
    }

    @Test
    fun betaDetection() {
        val pkg = "net.bible.android.activity.debug"
        // A production version name of a video-look build: not beta.
        assertFalse(VideoLook.isBeta("6.0.1271#abc github standard release (2026)", pkg, debugTraits = false))
        // Debug traits alone (a plain debug build) make it beta, as before the switch.
        assertTrue(VideoLook.isBeta("6.0.1271#abc github standard debug (2026)", pkg, debugTraits = true))
        // Pre-release suffixes are read before the '#' tail only.
        assertTrue(VideoLook.isBeta("6.0.1271-beta#abc github standard release (2026)", pkg, debugTraits = false))
        assertTrue(VideoLook.isBeta("6.0.1271-test", pkg, debugTraits = false))
        assertTrue(VideoLook.isBeta("6.0.1271-alpha#x", pkg, debugTraits = false))
        assertFalse(VideoLook.isBeta("6.0.1271#build-beta", pkg, debugTraits = false))
        assertTrue(VideoLook.isBeta("6.0.1271", "net.bible.android.activity.next", debugTraits = false))
    }
}
