package net.bible.android.control.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/** A platform delivery cancellation is neither an error nor a successful backup. */
class PickerOutcomeTest {
    @Test fun androidSaveCancellationAndMissingUriRemainDistinct() {
        assertEquals(DeliveryResult.CANCELLED, net.bible.android.platform.androidDeliveryResult(SaveOrShare.SAVE, android.app.Activity.RESULT_CANCELED, false))
        assertEquals(DeliveryResult.FAILED, net.bible.android.platform.androidDeliveryResult(SaveOrShare.SAVE, android.app.Activity.RESULT_OK, false))
        assertEquals(DeliveryResult.DELIVERED, net.bible.android.platform.androidDeliveryResult(SaveOrShare.SAVE, android.app.Activity.RESULT_OK, true))
        assertEquals(DeliveryResult.DELIVERED, net.bible.android.platform.androidDeliveryResult(SaveOrShare.SAVE, android.app.Activity.RESULT_CANCELED, true))
    }

    @Test fun androidShareOkAndCancelAreDoneButOtherResultsFail() {
        assertEquals(DeliveryResult.DELIVERED, net.bible.android.platform.androidDeliveryResult(SaveOrShare.SHARE, android.app.Activity.RESULT_OK, false))
        assertEquals(DeliveryResult.DELIVERED, net.bible.android.platform.androidDeliveryResult(SaveOrShare.SHARE, android.app.Activity.RESULT_CANCELED, false))
        assertEquals(DeliveryResult.FAILED, net.bible.android.platform.androidDeliveryResult(SaveOrShare.SHARE, android.app.Activity.RESULT_FIRST_USER, false))
    }

    @Test fun cancelledDeliveryIsCancelled() =
        assertEquals(PickerOutcome.CANCELLED, pickerOutcome(DeliveryResult.CANCELLED))

    @Test fun failedDeliveryIsAnError() =
        assertEquals(PickerOutcome.FAILED, pickerOutcome(DeliveryResult.FAILED))

    @Test fun deliveredFileIsDone() =
        assertEquals(PickerOutcome.DONE, pickerOutcome(DeliveryResult.DELIVERED))
}
