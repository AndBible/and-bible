package net.bible.android.view.activity.readingplan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingPlanSelectorController
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.readingplan.ReadingPlanSelectorScreen
import org.koin.android.ext.android.inject

/** Compose host for the reading-plan chooser — the new-path twin of the classic [ReadingPlanSelectorList]. */
class ReadingPlanSelectorComposeActivity : ActivityBase() {
    private val readingPlanControl: ReadingPlanControl by inject()

    private val controller by lazy {
        ReadingPlanSelectorController(
            loadPlans = {
                readingPlanControl.readingPlanList.map { PlanEntry(it.planCode, it.planName ?: "", it.planDescription ?: "") }
            },
            hasDuplicates = { readingPlanControl.readingPlanUserDuplicates },
            onSelect = { planCode ->
                val dto = readingPlanControl.readingPlanList.firstOrNull { it.planCode == planCode }
                if (dto != null) {
                    readingPlanControl.startReadingPlan(dto)
                    setResult(Activity.RESULT_OK, Intent(planCode))
                    finish()
                }
                // else: the plan vanished (sync); ignore the tap rather than crash
            },
            onReset = { planCode -> readingPlanControl.reset(planCode) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = getString(R.string.rdg_plan_selector_title)
        ABEventBus.register(this) { onMain<ReadingPlansUpdatedViaSyncEvent> { controller.load() } }
        setContent {
            AbAppTheme {
                    val plans by controller.plans.collectAsState()
                    val duplicate by controller.duplicateWarning.collectAsState()
                    val error by controller.error.collectAsState()
                    ReadingPlanSelectorScreen(
                        title = title,
                        plans = plans,
                        duplicateWarning = duplicate,
                        error = error,
                        onSelect = controller::select,
                        onReset = controller::reset,
                        onDismissError = controller::dismissError,
                        onDismissDuplicate = controller::dismissDuplicateWarning,
                        onNavigateUp = { finish() },
                    )
            }
        }
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }
}
