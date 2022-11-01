/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.activity.BuildConfig.FLAVOR_appearance
import net.bible.android.activity.BuildConfig.FLAVOR_distchannel

object BuildVariant {
    // Group the properties according to their flavorDimension

    object Appearance {
        inline val isDiscrete get() = FLAVOR_appearance == "discrete"
    }

    object DistributionChannel {
        inline val isSamsung get() = FLAVOR_distchannel == "samsung"
        inline val isPlay get() = FLAVOR_distchannel == "googleplay"
        inline val isHuawei get() = FLAVOR_distchannel == "huawei"
        inline val isFdroid get() = FLAVOR_distchannel == "fdroid"
        inline val isAmazon get() = FLAVOR_distchannel == "amazon"
    }
}

