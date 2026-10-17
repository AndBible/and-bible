package net.bible.service.device.speak

import net.bible.sharedcore.platform.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SpeakTimingTest {
    private fun timing(cpms: Double): SpeakTiming {
        val settings = mock(AppSettings::class.java)
        `when`(settings.getDouble("SpeakCPMS", 0.016f.toDouble())).thenReturn(cpms)
        return SpeakTiming(settings)
    }

    @Test
    fun secsForCharsMatchesMathRoundForNormalValues() {
        assertEquals(63L, timing(0.016).getSecsForChars(1000)) // 62.5 rounds up like Math.round
        assertEquals(0L, timing(0.016).getSecsForChars(0))
    }

    /** `Math.round(NaN)` was 0; `roundToLong()` throws on NaN (0 chars at 0 cpms is 0/0). */
    @Test
    fun secsForCharsIsZeroForNaN() {
        assertEquals(0L, timing(0.0).getSecsForChars(0))
    }

    /** `Math.round(+Infinity)` was `Long.MAX_VALUE`. */
    @Test
    fun secsForCharsSaturatesForInfinity() {
        assertEquals(Long.MAX_VALUE, timing(0.0).getSecsForChars(10))
    }
}
