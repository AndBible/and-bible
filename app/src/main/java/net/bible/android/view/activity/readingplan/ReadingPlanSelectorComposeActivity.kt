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
import net.bible.service.common.CommonUtils
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingPlanSelectorController
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.readingplan.ReadingPlanSelectorScreen
import net.bible.sharedui.theme.AbTheme
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
                val dto = readingPlanControl.readingPlanList.first { it.planCode == planCode }
                readingPlanControl.startReadingPlan(dto)
                setResult(Activity.RESULT_OK, Intent(planCode))
                finish()
            },
            onReset = { planCode -> readingPlanControl.reset(planCode) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = getString(R.string.rdg_plan_selector_title)
        ABEventBus.register(this) { onMain<ReadingPlansUpdatedViaSyncEvent> { controller.load() } }
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
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
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }
}
