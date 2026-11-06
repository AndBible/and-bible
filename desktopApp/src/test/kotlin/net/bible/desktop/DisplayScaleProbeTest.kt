package net.bible.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review findings on the first #4006 fix: the probe's decisions, tested with fakes (the X11 reads themselves are live-only).
 */
class DisplayScaleProbeTest {
    private val gnomeWayland = mapOf("WAYLAND_DISPLAY" to "wayland-0", "XDG_CURRENT_DESKTOP" to "ubuntu:GNOME", "DISPLAY" to ":0")
    private val empty = X11DisplayScaleProbe.Result(xsettings = null, xResources = null, settingsOwner = false)
    private val ready = X11DisplayScaleProbe.Result(xsettings = XSettingsValues(1, 144.0), xResources = "Xft.dpi:\t144\n", settingsOwner = true)

    private class FakeClock {
        var nowMs = 0L
        val sleeps = mutableListOf<Long>()
        fun sleep(ms: Long) { sleeps += ms; nowMs += ms }
        fun nanoTime() = nowMs * 1_000_000
    }

    private fun settle(env: Map<String, String>, results: List<X11DisplayScaleProbe.Result>, clock: FakeClock = FakeClock()): Pair<X11DisplayScaleProbe.Result, Int> {
        var calls = 0
        val r = X11DisplayScaleProbe.readUntilSettled(env, { results[minOf(calls++, results.size - 1)] }, clock::sleep, clock::nanoTime)
        return r to calls
    }

    /**
     * First launch on GNOME Wayland: the probe's XOpenDisplay starts Xwayland on demand, before gsd-xsettings owns
     * `_XSETTINGS_S0` and writes `Xft.dpi`. A one-shot read gave scale 1; the probe now waits for the settings.
     */
    @Test fun gnomeWaylandWaitsForTheSettingsDaemon() {
        val clock = FakeClock()
        val (r, calls) = settle(gnomeWayland, listOf(empty, empty, empty, ready), clock)
        assertEquals(4, calls)
        assertEquals(ready.xsettings, r.xsettings)
        assertEquals(300L, r.waitedMs)
        assertEquals(1.5, DisplayScaleDetector.detect(inputs(r))!!.scale)
    }

    @Test fun gnomeWaylandWaitIsBounded() {
        val clock = FakeClock()
        val (r, calls) = settle(gnomeWayland, listOf(empty), clock)
        assertTrue(clock.nowMs in X11DisplayScaleProbe.GNOME_WAIT_MS..X11DisplayScaleProbe.GNOME_WAIT_MS + X11DisplayScaleProbe.GNOME_POLL_MS, "${clock.nowMs}")
        assertTrue(calls in 2..30, "$calls")
        val source = DisplayScaleDetector.detect(inputs(r))!!.source
        assertTrue("no XSETTINGS manager and no X resources" in source && "waited" in source, source)
    }

    @Test fun noWaitOutsideGnomeWayland() {
        assertEquals(1, settle(mapOf("XDG_CURRENT_DESKTOP" to "GNOME", "DISPLAY" to ":0"), listOf(empty, ready)).second, "GNOME on X11")
        assertEquals(1, settle(gnomeWayland + ("XDG_CURRENT_DESKTOP" to "KDE"), listOf(empty, ready)).second, "KDE Wayland")
    }

    /** The read itself takes time (opening the display): that is not a wait and must not be reported as one. */
    @Test fun aSlowFirstReadIsNotAWait() {
        val clock = FakeClock()
        val r = X11DisplayScaleProbe.readUntilSettled(mapOf("DISPLAY" to ":0"), { clock.nowMs += 80; ready }, clock::sleep, clock::nanoTime)
        assertEquals(0L, r.waitedMs)
        assertEquals(null, r.note())
    }

    @Test fun noWaitWhenAnySettingIsThereOrTheDisplayFailed() {
        assertEquals(1, settle(gnomeWayland, listOf(ready, empty)).second)
        assertEquals(1, settle(gnomeWayland, listOf(empty.copy(xResources = "Xft.antialias: 1\n"), ready)).second)
        assertEquals(1, settle(gnomeWayland, listOf(empty.copy(settingsOwner = true), ready)).second)
        assertEquals(1, settle(gnomeWayland, listOf(X11DisplayScaleProbe.Result.failed("XOpenDisplay(:0) failed"), ready)).second)
    }

    /** The log line names why no scale was found instead of one generic text for every case. */
    @Test fun probeFailureReasonIsInTheSource() {
        val s = DisplayScaleDetector.detect(inputs(X11DisplayScaleProbe.Result.failed("libX11 not loadable: boom")))!!
        assertEquals(1.0, s.scale)
        assertEquals("default (no system scale found: libX11 not loadable: boom)", s.source)
        val none = DisplayScaleDetector.detect(inputs(empty))!!
        assertEquals("default (no system scale found: no XSETTINGS manager and no X resources)", none.source)
        val unset = DisplayScaleDetector.detect(inputs(empty.copy(settingsOwner = true, xsettings = XSettingsValues(null, null))))!!
        assertEquals("default (no system scale found)", unset.source)
    }

    @Test fun xErrorEventErrorCodeOffset() {
        assertEquals(32, X11DisplayScaleProbe.errorCodeOffset(pointerSize = 8, longSize = 8))
        assertEquals(16, X11DisplayScaleProbe.errorCodeOffset(pointerSize = 4, longSize = 4))
    }

    private fun inputs(r: X11DisplayScaleProbe.Result) =
        DisplayScaleInputs(true, emptyMap(), { null }, r.xsettings, r.xResources, probeNote = r.note())
}
