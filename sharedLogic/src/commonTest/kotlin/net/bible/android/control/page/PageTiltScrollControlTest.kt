package net.bible.android.control.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A hand-driven [TiltSensor]: the test pushes pitch samples like the platform would. */
private class FakeTiltSensor(override val isAvailable: Boolean = true) : TiltSensor {
    var onSample: ((Float) -> Unit)? = null
    var starts = 0
    var stops = 0
    val running get() = onSample != null

    override fun start(onSample: (pitch: Float) -> Unit) {
        starts++
        this.onSample = onSample
    }

    override fun stop() {
        stops++
        onSample = null
    }

    /** Samples are ignored once stopped, like a real unregistered listener. */
    fun emit(pitch: Float) { onSample?.invoke(pitch) }
}

class PageTiltScrollControlTest {
    private val sensor = FakeTiltSensor()
    private var settingOn = true
    private val control = PageTiltScrollControl(sensor) { settingOn }
    private val scrolls = mutableListOf<Pair<Boolean, Int>>()
    private var appActive = true
    private val driver = TiltScrollDriver(control, scroll = { forward, px -> scrolls += forward to px; true })

    /** Establish the neutral viewing pitch: two samples so the control treats the sensors as triggered. */
    private fun startAndCalibrate(viewingPitch: Float = -38f) {
        driver.setAppActive(true)
        sensor.emit(viewingPitch)
        driver.step()
    }

    @Test
    fun tiltForwardScrollsDown() {
        startAndCalibrate(-38f)
        assertTrue(sensor.running)
        scrolls.clear()
        sensor.emit(-60f) // 22 degrees further forward than the viewing pitch
        val delay = driver.step()
        assertEquals(listOf(true to 1), scrolls)
        assertTrue(delay in 4L..48L, "delay $delay")
    }

    @Test
    fun tiltBackwardDoesNotScroll() {
        startAndCalibrate(-38f)
        scrolls.clear()
        sensor.emit(-10f)
        driver.step()
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun withinToleranceDoesNotScroll() {
        startAndCalibrate(-38f)
        scrolls.clear()
        sensor.emit(-39f)
        driver.step()
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun steeperTiltScrollsFaster() {
        startAndCalibrate(-38f)
        sensor.emit(-45f)
        val slow = driver.step()
        sensor.emit(-80f)
        val fast = driver.step()
        assertTrue(fast < slow, "fast $fast slow $slow")
    }

    @Test
    fun stopsInBackground() {
        startAndCalibrate(-38f)
        assertTrue(sensor.running)
        driver.setAppActive(false)
        assertFalse(sensor.running)
        assertEquals(1, sensor.stops)
        scrolls.clear()
        sensor.emit(-80f) // a stray late sample must not move anything
        driver.step()
        assertTrue(scrolls.isEmpty())
        // repeated background notifications do not stop twice
        driver.setAppActive(false)
        driver.step()
        assertEquals(1, sensor.stops)
    }

    @Test
    fun resumesWhenActiveAgainAndRecalibrates() {
        startAndCalibrate(-38f)
        driver.setAppActive(false)
        driver.setAppActive(true)
        assertTrue(sensor.running)
        assertEquals(2, sensor.starts)
        sensor.emit(-10f) // new neutral pose after resume
        driver.step()
        scrolls.clear()
        sensor.emit(-10f)
        driver.step()
        assertTrue(scrolls.isEmpty())
    }

    @Test
    fun disabledSettingNeverStartsSensor() {
        settingOn = false
        driver.setAppActive(true)
        driver.step()
        assertEquals(0, sensor.starts)
    }

    @Test
    fun settingTurnedOffStopsSensor() {
        startAndCalibrate()
        settingOn = false
        driver.step()
        assertFalse(sensor.running)
    }

    @Test
    fun unavailableSensorNeverStarts() {
        val none = FakeTiltSensor(isAvailable = false)
        val c = PageTiltScrollControl(none) { true }
        assertFalse(c.enableTiltScroll(true))
        assertFalse(c.isTiltScrollEnabled)
        assertEquals(0, none.starts)
    }
}
