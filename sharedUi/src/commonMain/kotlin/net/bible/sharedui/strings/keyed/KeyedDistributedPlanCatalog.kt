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

package net.bible.sharedui.strings.keyed

import net.bible.service.readingplan.DistributedPlanDetails
import net.bible.sharedlogic.platform.DistributedPlanCatalog

/** The seven distributed reading plans (`ReadingPlanCatalog.ABDistributedPlanDetailArray`) named from a [StringTable]. */
class KeyedDistributedPlanCatalog(private val t: StringTable) : DistributedPlanCatalog {
    override fun plans(): List<DistributedPlanDetails> = PLAN_CODES.map {
        DistributedPlanDetails(it, t.text("plan_name_$it"), t.text("plan_description_$it"))
    }

    companion object {
        /** Plan file names, in the order `ReadingPlanCatalog` lists them. */
        val PLAN_CODES = listOf(
            "y1ntpspr", "y1ot1nt1_chronological", "y1ot1nt1_OTandNT", "y1ot1nt1_OTthenNT",
            "y1ot1nt2_mcheyne", "y1ot6nt4_profHorner", "y2ot1ntps2",
        )
    }
}
