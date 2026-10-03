package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.readingplan.DailyReadingScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DailyReadingGoldenTest {
    private val mixedReadings = listOf(
        ReadingItem(1, "Genesis 1-2", true),
        ReadingItem(2, "Psalm 1", false),
        ReadingItem(3, "Matthew 1", false),
    )
    private fun ui(
        readings: List<ReadingItem> = mixedReadings,
        allRead: Boolean = false,
        dateBased: Boolean = false,
    ) = DailyReadingUi("Bible in a year", "Day 3", "Thu 9 Jan", readings, showSpeakAll = true, allRead = allRead, isDateBasedPlan = dateBased)

    private fun screen(ui: DailyReadingUi = ui(), speak: SpeakState = SpeakState.NONE) = @androidx.compose.runtime.Composable {
        DailyReadingScreen(
            ui = ui, speakState = speak, error = null, confirm = null, startDatePick = null,
            onToggleRead = {}, onRead = {}, onSpeak = {}, onSpeakAll = {}, onDone = {},
            onPauseSpeak = {}, onStopSpeak = {}, onChangePlan = {}, onChangeDay = {}, onSetCurrentDay = {},
            onSetStartDate = {}, onReset = {}, onImportPlan = {}, onConfirm = {}, onDismissConfirm = {},
            onDismissError = {}, onConfirmStartDatePicker = { _, _, _ -> }, onDismissStartDatePicker = {},
            onNavigateUp = {},
        )
    }

    @Test fun daily_primary() { captureMatrix("DailyReading", "primary") { screen()() } }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun daily_primary_rtl() { captureRtl("DailyReading", "primary") { screen()() } }

    @Test fun daily_allRead() {
        val allRead = mixedReadings.map { it.copy(isRead = true) }
        captureGolden("DailyReading", "allRead", EDGE_MODE) { screen(ui(readings = allRead, allRead = true))() }
    }

    @Test fun daily_speaking() {
        captureGolden("DailyReading", "speaking", EDGE_MODE) { screen(speak = SpeakState.SPEAKING)() }
    }
}
