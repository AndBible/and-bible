package net.bible.android.control.backup

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

/** F125: cancelling the backup file picker must be silent, not an error. */
class PickerOutcomeTest {
    @Test fun cancelledSavePickerIsCancelled() =
        assertEquals(PickerOutcome.CANCELLED, pickerOutcome(SaveOrShare.SAVE, Activity.RESULT_CANCELED, hasUri = false))

    @Test fun okSaveWithoutUriIsStillAnError() =
        assertEquals(PickerOutcome.FAILED, pickerOutcome(SaveOrShare.SAVE, Activity.RESULT_OK, hasUri = false))

    @Test fun okSaveWithUriCopies() =
        assertEquals(PickerOutcome.COPY, pickerOutcome(SaveOrShare.SAVE, Activity.RESULT_OK, hasUri = true))

    @Test fun shareOkAndCancelAreDone() {
        assertEquals(PickerOutcome.DONE, pickerOutcome(SaveOrShare.SHARE, Activity.RESULT_OK, hasUri = false))
        assertEquals(PickerOutcome.DONE, pickerOutcome(SaveOrShare.SHARE, Activity.RESULT_CANCELED, hasUri = false))
    }

    @Test fun shareWithOtherResultFails() =
        assertEquals(PickerOutcome.FAILED, pickerOutcome(SaveOrShare.SHARE, Activity.RESULT_FIRST_USER, hasUri = false))
}
