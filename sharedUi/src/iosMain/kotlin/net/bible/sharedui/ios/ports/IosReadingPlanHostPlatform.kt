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

import net.bible.sharedui.app.readingplan.ReadingPlanHostPlatform
import net.bible.sharedui.ios.IosPending

/** Pending (stream H): portrait, and a fixed epoch date until the real platform half lands. */
@kotlin.native.HiddenFromObjC
class IosReadingPlanHostPlatform : ReadingPlanHostPlatform {
    override val isPortrait: Boolean get() = true

    override fun localYmd(epochMs: Long): Triple<Int, Int, Int> {
        IosPending.log("ReadingPlanHostPlatform.localYmd")
        return Triple(1970, 1, 1)
    }

    override fun withLocalYmd(epochMs: Long, year: Int, month1to12: Int, day: Int): Long {
        IosPending.log("ReadingPlanHostPlatform.withLocalYmd")
        return epochMs
    }
}
