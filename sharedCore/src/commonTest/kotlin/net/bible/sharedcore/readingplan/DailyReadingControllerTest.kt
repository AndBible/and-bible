package net.bible.sharedcore.readingplan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyReadingControllerTest {
    private fun controller(
        onToggle: (Int) -> Unit = {}, onRead: (Int) -> Unit = {}, onSpeak: (Int) -> Unit = {},
        onSpeakAll: () -> Unit = {}, onDone: () -> Unit = {}, onPause: () -> Unit = {}, onStop: () -> Unit = {},
        onChangePlan: () -> Unit = {}, onChangeDay: () -> Unit = {}, onSetCurrentDay: () -> Unit = {},
        onReset: () -> Unit = {}, onSetStartDate: () -> Unit = {}, onImport: () -> Unit = {},
    ) = DailyReadingController(onToggle, onRead, onSpeak, onSpeakAll, onDone, onPause, onStop,
        onChangePlan, onChangeDay, onSetCurrentDay, onReset, onSetStartDate, onImport)

    private val ui = DailyReadingUi("Plan", "Day 1", "1 Jan",
        listOf(ReadingItem(1, "Gen 1", false), ReadingItem(2, "Matt 1", true)), true, false, false)

    @Test fun setUi_updates_snapshot() {
        val c = controller()
        c.setUi(ui)
        assertEquals(ui, c.ui.value)
    }

    @Test fun action_seams_fire() {
        var toggled = -1; var spoke = -1; var doneCalled = false
        val c = controller(onToggle = { toggled = it }, onSpeak = { spoke = it }, onDone = { doneCalled = true })
        c.toggleRead(2); c.speak(1); c.done()
        assertEquals(2, toggled); assertEquals(1, spoke); assertEquals(true, doneCalled)
    }

    @Test fun pushSpeakState_drives_flow() {
        val c = controller()
        assertEquals(SpeakState.NONE, c.speakState.value)
        c.pushSpeakState(SpeakState.SPEAKING)
        assertEquals(SpeakState.SPEAKING, c.speakState.value)
    }

    @Test fun reset_is_gated_by_confirm() {
        var resetCalled = false
        val c = controller(onReset = { resetCalled = true })
        c.requestReset()
        assertEquals(ConfirmKind.RESET, c.confirm.value)
        assertEquals(false, resetCalled) // not yet
        c.confirm()
        assertEquals(true, resetCalled)
        assertNull(c.confirm.value)
    }

    @Test fun set_current_day_is_gated_and_dismissable() {
        var scdCalled = false
        val c = controller(onSetCurrentDay = { scdCalled = true })
        c.requestSetCurrentDay()
        assertEquals(ConfirmKind.SET_CURRENT_DAY, c.confirm.value)
        c.dismissConfirm()
        assertNull(c.confirm.value)
        assertEquals(false, scdCalled) // dismiss does NOT fire the seam
    }

    @Test fun confirm_fires_set_current_day_seam() {
        var scd = false; var reset = false
        val c = controller(onSetCurrentDay = { scd = true }, onReset = { reset = true })
        c.requestSetCurrentDay()
        c.confirm()
        assertEquals(true, scd)
        assertEquals(false, reset)
        assertNull(c.confirm.value)
    }

    @Test fun error_set_and_clear() {
        val c = controller()
        c.showError()
        assertEquals(ReadingPlanError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }
}
