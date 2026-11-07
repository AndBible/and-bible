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
package net.bible.sharedcore.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

/** [IosBetaBuild.isBeta] for this process: the bundle's receipt name, simulator, debug binary and launch arguments. */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun detectIosBetaBuild(): Boolean = IosBetaBuild.isBeta(
    receiptName = NSBundle.mainBundle.appStoreReceiptURL?.lastPathComponent,
    isSimulator = NSProcessInfo.processInfo.environment["SIMULATOR_UDID"] != null,
    isDebugBinary = kotlin.native.Platform.isDebugBinary,
    arguments = NSProcessInfo.processInfo.arguments.map { it.toString() },
)
