package net.bible.android.view.widget

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import net.bible.android.TEST_SDK
import net.bible.android.control.widget.VerseBible
import net.bible.android.control.widget.VerseChangeFrequency
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.database.IdType
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class VerseWidgetConfigScreenTest {
    @get:Rule val compose = createComposeRule()

    private val labelId = IdType()
    private fun options(plan: Boolean, labels: Boolean = true) = VerseConfigOptions(
        labels = if (labels) listOf(labelId to "Memory verses") else emptyList(),
        bibles = listOf(VerseBible("KJV", "KJV", "KJV"), VerseBible("FinRK", "FinRK", "German")),
        defaultBible = VerseBible("KJV", "KJV", "KJV"),
        hasReadingPlan = plan,
    )

    private fun show(o: VerseConfigOptions, existing: VerseWidgetConfig? = null, onSave: (VerseWidgetConfig) -> Unit = {}) =
        compose.setContent { AbTheme { VerseWidgetConfigScreen(o, existing, newSeed = 42L, onSave = onSave, onCancel = {}) } }

    @Test fun configScreenHidesPlanOptionWithoutPlan() {
        show(options(plan = false))
        compose.onAllNodesWithTag("source-plan").assertCountEquals(0)
    }

    @Test fun configScreenShowsPlanOptionWithAPlan() {
        show(options(plan = true))
        compose.onAllNodesWithTag("source-plan").assertCountEquals(1)
    }

    @Test fun withoutLabelsOnlyRandomAndPlanAreOffered() {
        show(options(plan = false, labels = false))
        compose.onAllNodesWithTag("source-label").assertCountEquals(0)
        compose.onAllNodesWithTag("source-random").assertCountEquals(1)
    }

    @Test fun configScreenSavesSelection() {
        var saved: VerseWidgetConfig? = null
        show(options(plan = true)) { saved = it }
        compose.onNodeWithTag("source-label").performClick()
        compose.onNodeWithTag("label-Memory verses").performClick()
        compose.onNodeWithTag("freq-EVERY_3_HOURS").performClick()
        compose.onNodeWithTag("bible-FinRK").performScrollTo().performClick()
        compose.onNodeWithTag("show-buttons").performScrollTo().performClick()
        compose.onNodeWithTag("verse-config-save").performClick()
        assertEquals(
            VerseWidgetConfig(VerseSource.Label(labelId), VerseChangeFrequency.EVERY_3_HOURS, "FinRK", showButtons = false, showSourceAndDate = true, seed = 42L),
            saved,
        )
    }

    @Test fun reconfiguringKeepsSeedAndShuffleCount() {
        var saved: VerseWidgetConfig? = null
        val existing = VerseWidgetConfig(VerseSource.ReadingPlan, seed = 7L, shuffleCount = 4)
        show(options(plan = true), existing) { saved = it }
        compose.onNodeWithTag("verse-config-save").performClick()
        assertNotNull(saved)
        assertEquals(existing, saved)
    }

    @Test fun aSavedPlanSourceFallsBackToRandomWhenThePlanIsGone() {
        var saved: VerseWidgetConfig? = null
        show(options(plan = false), VerseWidgetConfig(VerseSource.ReadingPlan, seed = 7L)) { saved = it }
        compose.onNodeWithTag("verse-config-save").performClick()
        assertEquals(VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE), saved!!.source)
    }
}
