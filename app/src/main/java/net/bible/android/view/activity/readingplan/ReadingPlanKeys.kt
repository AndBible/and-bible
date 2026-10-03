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
package net.bible.android.view.activity.readingplan

import net.bible.android.BibleApplication
import net.bible.android.activity.R

/**
 * Reading-plan intent keys, lifted out of the about-to-be-deleted `DailyReading`'s companion
 * (Batch Z-late phase 1) because `DailyReadingComposeActivity` read them at the time.
 *
 * **That reader is gone.** nav-graph 3/5/6 moved the daily-reading screen into the Compose
 * navigation graph, where the plan and day travel IN the route (`NavRoutes.dailyReading(plan,
 * day)`), and Task 9 deleted the host. As of Task 9 [PLAN] and [DAY] have no live reader or writer
 * anywhere in the repo; their only consumer is `IntentKeysTest:49-50`, which pins the two key
 * STRINGS so that a stored intent written by an older install still decodes to the same names.
 *
 * The FILE must stay regardless of what happens to this object: [ReadingPlanCatalog] below shares
 * it and has a live production reader (`ReadingPlanTextFileDao`). Whether this object in
 * particular should outlive the migration is an epilogue question for the maintainer, not a
 * cleanup task's call — see `ClassicReadingPlanRemovalGuardTest.theSurvivingReadingPlanCollaborators-
 * StillExist`, which pins the path.
 */
object ReadingPlanKeys {
    val PLAN = "net.bible.android.view.activity.readingplan.Plan"
    val DAY = "net.bible.android.view.activity.readingplan.Day"
}

/**
 * The bundled reading plans' display names and descriptions, lifted with its `PlanDetails` type out
 * of `DailyReading`'s companion.
 *
 * Still an eagerly-initialised `val` calling `app.getString(...)`, deliberately: that resolves the
 * localized strings when the holder is first touched. Strictly, the TRIGGER SET narrowed by the
 * move: previously any touch of `DailyReading`'s companion initialised this catalogue; now only
 * `ReadingPlanTextFileDao`'s first touch does. What is unchanged is that the touch happens
 * later-or-equal to before, and it resolves the same strings once per process — both at the same
 * call site in `ReadingPlanTextFileDao`. Turning it into a `by lazy` or a function would change when
 * the locale is read, which is a behaviour change.
 */
object ReadingPlanCatalog {
    private val app = BibleApplication.application

    class PlanDetails(
        val planCode: String,
        val planName: String,
        val planDescription: String
    )

    // Link AB distributed reading plan file names with plan name/description resource strings
    val ABDistributedPlanDetailArray = arrayOf(
        PlanDetails(
            "y1ntpspr",
            app.getString(R.string.plan_name_y1ntpspr),
            app.getString(R.string.plan_description_y1ntpspr)
        ),
        PlanDetails (
            "y1ot1nt1_chronological",
            app.getString(R.string.plan_name_y1ot1nt1_chronological),
            app.getString(R.string.plan_description_y1ot1nt1_chronological)
        ),
        PlanDetails(
            "y1ot1nt1_OTandNT",
            app.getString(R.string.plan_name_y1ot1nt1_OTandNT),
            app.getString(R.string.plan_description_y1ot1nt1_OTandNT)
        ),
        PlanDetails(
            "y1ot1nt1_OTthenNT",
            app.getString(R.string.plan_name_y1ot1nt1_OTthenNT),
            app.getString(R.string.plan_description_y1ot1nt1_OTthenNT)
        ),
        PlanDetails(
            "y1ot1nt2_mcheyne",
            app.getString(R.string.plan_name_y1ot1nt2_mcheyne),
            app.getString(R.string.plan_description_y1ot1nt2_mcheyne)
        ),
        PlanDetails(
            "y1ot6nt4_profHorner",
            app.getString(R.string.plan_name_y1ot6nt4_profHorner),
            app.getString(R.string.plan_description_y1ot6nt4_profHorner)
        ),
        PlanDetails(
            "y2ot1ntps2",
            app.getString(R.string.plan_name_y2ot1ntps2),
            app.getString(R.string.plan_description_y2ot1ntps2)
        )
    )
}
