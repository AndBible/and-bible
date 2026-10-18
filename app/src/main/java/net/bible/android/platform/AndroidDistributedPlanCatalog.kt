package net.bible.android.platform

import net.bible.android.view.activity.readingplan.ReadingPlanCatalog
import net.bible.service.readingplan.DistributedPlanDetails
import net.bible.sharedlogic.platform.DistributedPlanCatalog

/** Android [DistributedPlanCatalog]: the AndBible-distributed plans from [ReadingPlanCatalog], evaluated per call. */
class AndroidDistributedPlanCatalog : DistributedPlanCatalog {
    override fun plans(): List<DistributedPlanDetails> =
        ReadingPlanCatalog.ABDistributedPlanDetailArray.map { DistributedPlanDetails(it.planCode, it.planName, it.planDescription) }
}
