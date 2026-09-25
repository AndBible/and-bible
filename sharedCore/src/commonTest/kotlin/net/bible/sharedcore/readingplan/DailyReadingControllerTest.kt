package net.bible.sharedcore.readingplan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyReadingControllerTest {
    private fun controller(
        onToggle: (Int) -> Unit = {}, onRead: (Int) -> Unit = {}, onSpeak: (Int) -> Unit = {},
        onSpeakAll: () -> Unit = {}, onDone: () -> Unit = {}, onPause: () -> Unit = {}, onStop: () -> Unit = {},
        onChangePlan: () -> Unit = {}, onChangeDay: () -> Unit = {}, onSetCurrentDay: () -> Unit = {},
        onReset: () -> Unit = {}, onSetStartDate: () -> Unit = {},
        onConfirmStartDate: (year: Int, month1to12: Int, day: Int) -> Unit = { _, _, _ -> },
        onImport: () -> Unit = {},
    ) = DailyReadingController(onToggle, onRead, onSpeak, onSpeakAll, onDone, onPause, onStop,
        onChangePlan, onChangeDay, onSetCurrentDay, onReset, onSetStartDate, onConfirmStartDate, onImport)

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

    @Test fun showStartDatePicker_pushes_state() {
        val c = controller()
        assertNull(c.startDatePick.value)
        c.showStartDatePicker(1_000L, 2_000L)
        assertEquals(StartDatePick(1_000L, 2_000L), c.startDatePick.value)
    }

    @Test fun confirmStartDate_calls_the_callback_once_and_clears() {
        var calls = 0; var y = -1; var m = -1; var d = -1
        val c = controller(onConfirmStartDate = { year, month, day -> calls++; y = year; m = month; d = day })
        c.showStartDatePicker(1_000L, 2_000L)
        c.confirmStartDate(2026, 9, 25)
        assertEquals(1, calls)
        assertEquals(2026, y); assertEquals(9, m); assertEquals(25, d)
        assertNull(c.startDatePick.value)
    }

    @Test fun dismissStartDatePicker_clears_without_the_callback() {
        var calls = 0
        val c = controller(onConfirmStartDate = { _, _, _ -> calls++ })
        c.showStartDatePicker(1_000L, 2_000L)
        c.dismissStartDatePicker()
        assertNull(c.startDatePick.value)
        assertEquals(0, calls)
    }

    @Test fun setStartDate_fires_the_host_seam() {
        var called = false
        val c = controller(onSetStartDate = { called = true })
        c.setStartDate()
        assertEquals(true, called)
    }
}
