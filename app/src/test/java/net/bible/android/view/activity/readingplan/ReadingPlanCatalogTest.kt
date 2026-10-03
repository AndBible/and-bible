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
package net.bible.android.view.activity.readingplan

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Batch Z-late phase 1, P2. [ReadingPlanCatalog.ABDistributedPlanDetailArray] moved out of
 * `DailyReading`'s companion, verbatim, into this holder. Needs Robolectric because the array
 * resolves string resources through [net.bible.android.BibleApplication].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingPlanCatalogTest {

    @Test fun theSevenBundledPlansAreStillListedInOrder() {
        assertEquals(
            listOf(
                "y1ntpspr",
                "y1ot1nt1_chronological",
                "y1ot1nt1_OTandNT",
                "y1ot1nt1_OTthenNT",
                "y1ot1nt2_mcheyne",
                "y1ot6nt4_profHorner",
                "y2ot1ntps2",
            ),
            ReadingPlanCatalog.ABDistributedPlanDetailArray.map { it.planCode },
        )
    }
}
