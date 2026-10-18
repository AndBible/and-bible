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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedui.ios.IosPending
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile

/** File sizes are real (locale formatted); plurals are pending (stream H) and answer an empty string. */
@kotlin.native.HiddenFromObjC
class IosPlatformText : PlatformText {
    override fun formatFileSize(bytes: Long): String =
        NSByteCountFormatter.stringFromByteCount(bytes, NSByteCountFormatterCountStyleFile)

    override fun plural(key: PluralKey, count: Int, vararg args: Any): String {
        IosPending.log("PlatformText.plural")
        return ""
    }
}
