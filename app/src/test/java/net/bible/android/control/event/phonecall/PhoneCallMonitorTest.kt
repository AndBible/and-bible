package net.bible.android.control.event.phonecall

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec §2.3: the legacy (API 23-25) call-state mapping that now feeds a callback instead of the bus. */
class PhoneCallMonitorTest {
    @Test fun ringingAndOffHookActivateIdleDeactivates() {
        assertEquals(true, PhoneCallMonitor.callActivating(TelephonyManager.CALL_STATE_RINGING))
        assertEquals(true, PhoneCallMonitor.callActivating(TelephonyManager.CALL_STATE_OFFHOOK))
        assertEquals(false, PhoneCallMonitor.callActivating(TelephonyManager.CALL_STATE_IDLE))
        assertEquals(null, PhoneCallMonitor.callActivating(-1))
    }
}
