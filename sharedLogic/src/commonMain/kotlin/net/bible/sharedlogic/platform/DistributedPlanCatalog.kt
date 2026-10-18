package net.bible.sharedlogic.platform

import net.bible.service.readingplan.DistributedPlanDetails

/**
 * Port: the reading plans shipped with the app, with their localized name and description. Read per lookup, because the
 * platform resolves its localized strings lazily. Android: `ReadingPlanCatalog.ABDistributedPlanDetailArray`.
 */
fun interface DistributedPlanCatalog {
    fun plans(): List<DistributedPlanDetails>
}
