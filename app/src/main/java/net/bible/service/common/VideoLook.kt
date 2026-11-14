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

/**
 * The "fake production" look for tutorial video captures.
 *
 * Enabled for debug builds with `-PvideoLook=true` (or `VIDEO_LOOK=true` in local.properties),
 * which sets [BuildConfig.VIDEO_LOOK]. The build itself drops the debug app name, the debug icon
 * and the `-beta` version suffix (app/build.gradle.kts); this object hides the runtime debug
 * traits: the beta notice and beta-only settings, the build type in the version string, the
 * debug tools rows and the test AI prompts. The build stays debuggable (adb, WebView CDP) and
 * keeps its applicationId suffix. Off by default, in which case every function here returns
 * exactly what the code returned before the switch existed.
 */
object VideoLook {
    /** True when this APK was built with the video look switch. */
    val enabled: Boolean get() = BuildConfig.VIDEO_LOOK

    /** Whether a build with the debuggable flag [debuggable] shows debug-only behaviour. */
    fun showsDebugTraits(debuggable: Boolean, videoLook: Boolean = enabled): Boolean =
        debuggable && !videoLook

    /** The build type named in the on-screen version string: a video-look build says `release`. */
    fun displayedBuildType(buildType: String, videoLook: Boolean = enabled): String =
        if (videoLook) "release" else buildType

    /**
     * Whether the app counts as a beta build ([CommonUtils.isBeta]): a pre-release version name,
     * a `.next` package, or a build with [debugTraits]. [versionName] may carry the `#hash ...`
     * tail of [CommonUtils.applicationVersionName].
     */
    fun isBeta(versionName: String, packageName: String, debugTraits: Boolean): Boolean {
        val ver = versionName.split("#")[0]
        return ver.endsWith("-beta") || ver.endsWith("-test") || ver.endsWith("-alpha") ||
            packageName.endsWith(".next") || debugTraits
    }
}
