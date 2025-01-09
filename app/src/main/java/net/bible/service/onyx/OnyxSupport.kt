/*
 * Copyright (c) 2025 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.onyx

import android.os.Build
import com.onyx.android.sdk.api.device.epd.UpdateOption
import com.onyx.android.sdk.device.Device
import net.bible.service.common.OnyxSupportInterface

class OnyxSupport: OnyxSupportInterface {
    override val isOnyxDevice: Boolean get() = Build.BRAND.lowercase() == "onyx"
    override val isMonochrome: Boolean get() = isOnyxDevice // TODO: fix this as soon as we learn to use SDK

    override fun setupOnyxFast() {
        if (!isOnyxDevice) return
        val onyxDev = Device.currentDevice()
        onyxDev.appScopeRefreshMode = UpdateOption.FAST
    }

    override fun setupOnyxNormal() {
        if (!isOnyxDevice) return
        val onyxDev = Device.currentDevice()
        onyxDev.appScopeRefreshMode = UpdateOption.NORMAL
    }
}
