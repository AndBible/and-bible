package net.bible.desktop

import androidx.compose.ui.unit.IntSize
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #4006: on Linux the JDK 17 X11 toolkit takes its scale only from `sun.java2d.uiScale` / `J2D_UISCALE` / `GDK_SCALE`,
 * as an integer. GNOME (Wayland with Xwayland native scaling, and X11), KDE and Xfce publish the desktop's scale through
 * XSETTINGS and the `Xft.dpi` X resource instead, so the app stayed at scale 1 on a scaled desktop.
 */
class DisplayScaleTest {
    private fun detect(
        env: Map<String, String> = emptyMap(),
        props: Map<String, String> = emptyMap(),
        xsettings: XSettingsValues? = null,
        resources: String? = null,
        linux: Boolean = true,
    ) = DisplayScaleDetector.detect(DisplayScaleInputs(linux, env, props::get, xsettings, resources))

    @Test fun noSystemScaleIsOne() {
        val s = detect()!!
        assertEquals(1.0, s.scale)
        assertEquals(1, s.awtScale)
        assertEquals(1.0, s.composeFactor)
        assertEquals("default (no system scale found)", s.source)
    }

    @Test fun notLinuxIsLeftToTheJdk() {
        assertNull(detect(env = mapOf("ANDBIBLE_UI_SCALE" to "2"), linux = false))
    }

    /** GNOME at 200 %: gsd-xsettings publishes Xft/DPI = 96 * 2 * 1024 and Gdk/WindowScalingFactor = 2. */
    @Test fun xsettingsXftDpiGivesTheScale() {
        val s = detect(xsettings = XSettingsValues(windowScalingFactor = 2, xftDpi = 192.0), resources = "Xft.dpi:\t192\n")!!
        assertEquals(2.0, s.scale)
        assertEquals(2, s.awtScale)
        assertEquals(1.0, s.composeFactor)
        assertEquals("XSETTINGS Xft/DPI 192", s.source)
    }

    /** KDE or Xfce at 150 % (and plain X11 with an Xresources DPI): only the `Xft.dpi` resource is set. */
    @Test fun fractionalXftDpiResourceSplitsIntoAwtAndComposeScale() {
        val s = detect(resources = "Xft.antialias:\t1\nXft.dpi:\t144\nXft.hinting:\t1\n")!!
        assertEquals(1.5, s.scale)
        assertEquals(1, s.awtScale)
        assertEquals(1.5, s.composeFactor)
        assertEquals("X resource Xft.dpi 144", s.source)
    }

    @Test fun scaleAboveTwoKeepsTheIntegerPartInAwt() {
        val s = detect(resources = "Xft.dpi: 240")!!
        assertEquals(2.5, s.scale)
        assertEquals(2, s.awtScale)
        assertEquals(1.25, s.composeFactor)
    }

    @Test fun windowScalingFactorAloneIsUsed() {
        val s = detect(xsettings = XSettingsValues(windowScalingFactor = 2, xftDpi = null))!!
        assertEquals(2.0, s.scale)
        assertEquals("XSETTINGS Gdk/WindowScalingFactor 2", s.source)
    }

    /** A DPI below 96 (a bogus or physical-DPI Xresources line) never shrinks the UI. */
    @Test fun lowDpiIsClampedToOne() {
        assertEquals(1.0, detect(resources = "Xft.dpi: 72")!!.scale)
    }

    @Test fun gdkScaleBeatsTheDesktopSettings() {
        val s = detect(env = mapOf("GDK_SCALE" to "2"), resources = "Xft.dpi: 144")!!
        assertEquals(2.0, s.scale)
        assertEquals("GDK_SCALE=2", s.source)
    }

    @Test fun overrideBeatsEverythingAndMayBeFractional() {
        val s = detect(
            env = mapOf("ANDBIBLE_UI_SCALE" to "1.25", "GDK_SCALE" to "2"),
            xsettings = XSettingsValues(2, 192.0),
        )!!
        assertEquals(1.25, s.scale)
        assertEquals(1, s.awtScale)
        assertEquals(1.25, s.composeFactor)
        assertEquals("ANDBIBLE_UI_SCALE=1.25", s.source)
    }

    @Test fun overridePropertyBeatsOverrideEnvironment() {
        val s = detect(env = mapOf("ANDBIBLE_UI_SCALE" to "3"), props = mapOf("andbible.uiScale" to "1.5"))!!
        assertEquals(1.5, s.scale)
        assertEquals("-Dandbible.uiScale=1.5", s.source)
    }

    /** A user's own JVM option keeps working, now also fractionally (the JDK alone truncates 1.5 to 1). */
    @Test fun explicitJdkUiScaleIsHonoured() {
        val s = detect(props = mapOf("sun.java2d.uiScale" to "1.5"), resources = "Xft.dpi: 192")!!
        assertEquals(1.5, s.scale)
        assertEquals("-Dsun.java2d.uiScale=1.5", s.source)
    }

    @Test fun invalidOverrideIsIgnoredAndNamed() {
        val s = detect(env = mapOf("ANDBIBLE_UI_SCALE" to "huge"), resources = "Xft.dpi: 192")!!
        assertEquals(2.0, s.scale)
        assertTrue("ignored ANDBIBLE_UI_SCALE=huge" in s.source, s.source)
        // Out of range (0.5..4) is invalid too.
        assertEquals(1.0, detect(env = mapOf("ANDBIBLE_UI_SCALE" to "10"))!!.scale)
    }

    @Test fun overrideBelowOneShrinksThroughCompose() {
        val s = detect(env = mapOf("ANDBIBLE_UI_SCALE" to "0.75"))!!
        assertEquals(1, s.awtScale)
        assertEquals(0.75, s.composeFactor)
    }

    /**
     * With Compose layers as own windows (WebKitGTK, windowed CEF) a popup window takes the AWT density, not the content's,
     * so a Compose factor would break popups: the scale is rounded to an integer there.
     */
    @Test fun integerOnlyRoundsToTheNearestWholeScale() {
        val r = detect(resources = "Xft.dpi: 144")!!.integerOnly()
        assertEquals(2.0, r.scale)
        assertEquals(2, r.awtScale)
        assertEquals(1.0, r.composeFactor)
        assertEquals("X resource Xft.dpi 144, 1.5 rounded: Compose popups are own windows", r.source)
        assertEquals(1.0, detect(resources = "Xft.dpi: 120")!!.integerOnly().scale)
        assertEquals(1.0, detect(env = mapOf("ANDBIBLE_UI_SCALE" to "0.75"))!!.integerOnly().scale)
        val whole = detect(resources = "Xft.dpi: 192")!!
        assertEquals(whole, whole.integerOnly())
    }

    @Test fun describeNamesScaleSourceAndSplit() {
        assertEquals(
            "display scale 1.5 (X resource Xft.dpi 144): AWT 1, Compose x1.5",
            detect(resources = "Xft.dpi: 144")!!.describe(),
        )
    }

    @Test fun xftDpiResourceParsing() {
        assertEquals(192.0, DisplayScaleDetector.parseXftDpi("Xft.dpi:\t192\n"))
        assertEquals(120.5, DisplayScaleDetector.parseXftDpi("*.foo: 1\nXft.dpi:   120.5  \n"))
        assertNull(DisplayScaleDetector.parseXftDpi("Xft.antialias: 1\n"))
        assertNull(DisplayScaleDetector.parseXftDpi("Xft.dpi: lots\n"))
        assertNull(DisplayScaleDetector.parseXftDpi("XXft.dpi: 192\n"))
    }

    @Test fun xsettingsBlobParsingBothByteOrders() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val blob = xsettingsBlob(order) {
                string("Net/ThemeName", "Adwaita")
                color("Gtk/Fg")
                int("Gdk/WindowScalingFactor", 2)
                int("Xft/DPI", 147456) // 144 * 1024
            }
            assertEquals(XSettingsValues(2, 144.0), DisplayScaleDetector.parseXSettings(blob), "$order")
        }
    }

    @Test fun xsettingsParsingSurvivesTruncatedData() {
        val blob = xsettingsBlob(ByteOrder.LITTLE_ENDIAN) { int("Gdk/WindowScalingFactor", 2); int("Xft/DPI", 196608) }
        assertNull(DisplayScaleDetector.parseXSettings(blob.copyOf(10)))
        // The first setting is complete, the second is cut: what was read is kept.
        assertEquals(XSettingsValues(2, null), DisplayScaleDetector.parseXSettings(blob.copyOf(blob.size - 2)))
        assertNull(DisplayScaleDetector.parseXSettings(ByteArray(0)))
    }

    @Test fun applyingSetsTheJdkIntegerScale() {
        val set = mutableMapOf<String, String>()
        detect(resources = "Xft.dpi: 288")!!.applyToJdk { k, v -> set[k] = v }
        assertEquals(mapOf("sun.java2d.uiScale" to "3", "sun.java2d.uiScale.enabled" to "true", "skiko.linux.autodpi" to "false"), set)
    }

    @Test fun initialWindowSizeFollowsTheComposeFactor() {
        assertEquals(IntSize(1800, 1200), AwtWindowHandle.initialSizeDp(Rectangle(0, 0, 2560, 1400), factor = 1.5))
        // Never larger than the work area, never below the scaled minimum.
        assertEquals(
            IntSize(1280, (AwtWindowHandle.MIN_HEIGHT_DP * 1.5).toInt()),
            AwtWindowHandle.initialSizeDp(Rectangle(0, 0, 1280, 700), factor = 1.5),
        )
        assertEquals(IntSize(1800, 1200), AwtWindowHandle.initialSizeDp(null, factor = 1.5))
    }

    /** Shared code sizes its layout from the handle: dp are AWT units divided by the Compose factor. */
    @Test fun windowHandleReportsScaledDensity() {
        val handle = AwtWindowHandle(factor = 1.5)
        assertEquals(1.5f, handle.density)
        assertEquals(IntSize(AwtWindowHandle.DEFAULT_WIDTH_DP, AwtWindowHandle.DEFAULT_HEIGHT_DP), handle.sizeDp)
    }

    @Test fun windowedCefGetsTheWholeScale() {
        assertEquals(listOf("--force-device-scale-factor=1.5"), windowedScaleArgs(windowed = true, scale = 1.5))
        assertEquals(emptyList(), windowedScaleArgs(windowed = false, scale = 1.5))
        assertEquals(emptyList(), windowedScaleArgs(windowed = true, scale = 1.0))
    }

    /**
     * Xft/DPI used to beat Gdk/WindowScalingFactor unconditionally: an xsettingsd with a scaling factor 2 and the
     * default Xft/DPI 96 gave 1. The XSETTINGS scale is now the larger of Xft/DPI / 96 and the factor times
     * Gdk/UnscaledDPI / 96 (GNOME publishes both consistently: 2 x 96 = 192).
     */
    @Test fun windowScalingFactorIsNotLostToAnUnscaledXftDpi() {
        val s = detect(xsettings = XSettingsValues(windowScalingFactor = 2, xftDpi = 96.0))!!
        assertEquals(2.0, s.scale)
        assertEquals("XSETTINGS Gdk/WindowScalingFactor 2", s.source)
        // GNOME X11 200 % with Large Text (1.25): factor 2, unscaled 120, Xft/DPI 240.
        assertEquals(2.5, detect(xsettings = XSettingsValues(2, 240.0, unscaledDpi = 120.0))!!.scale)
        assertEquals(2.5, detect(xsettings = XSettingsValues(2, 96.0, unscaledDpi = 120.0))!!.scale)
        assertEquals(2.0, detect(xsettings = XSettingsValues(2, 192.0, unscaledDpi = 96.0))!!.scale)
    }

    @Test fun unscaledDpiIsParsed() {
        val blob = xsettingsBlob(ByteOrder.LITTLE_ENDIAN) { int("Gdk/WindowScalingFactor", 2); int("Gdk/UnscaledDPI", 122880); int("Xft/DPI", 245760) }
        assertEquals(XSettingsValues(2, 240.0, unscaledDpi = 120.0), DisplayScaleDetector.parseXSettings(blob))
    }

    /** The JDK accepts sun.java2d.uiScale as 2, 2x, 200% and 192dpi; so does the app now. */
    @Test fun jdkUiScaleForms() {
        assertEquals(2.0, detect(props = mapOf("sun.java2d.uiScale" to "2x"))!!.scale)
        assertEquals(1.5, detect(props = mapOf("sun.java2d.uiScale" to "150%"))!!.scale)
        assertEquals(2.0, detect(props = mapOf("sun.java2d.uiScale" to "192dpi"))!!.scale)
        assertEquals(1.25, detect(env = mapOf("J2D_UISCALE" to "1.25x"))!!.scale)
        assertEquals(2.0, detect(env = mapOf("ANDBIBLE_UI_SCALE" to "200%"))!!.scale)
    }

    /** An explicit -Dsun.java2d.uiScale.enabled=false switches JDK scaling off; the app no longer turns it back on. */
    @Test fun explicitlyDisabledJdkScalingIsRespected() {
        val s = detect(props = mapOf("sun.java2d.uiScale.enabled" to "false"), resources = "Xft.dpi: 192")!!
        assertEquals(1.0, s.scale)
        assertEquals("-Dsun.java2d.uiScale.enabled=false", s.source)
        val set = mutableMapOf<String, String>()
        s.applyToJdk { k, v -> set[k] = v }
        assertEquals(mapOf("skiko.linux.autodpi" to "false"), set)
        // The app's own override still works, entirely through the Compose density.
        val o = detect(props = mapOf("sun.java2d.uiScale.enabled" to "false", "andbible.uiScale" to "2"))!!
        assertEquals(1, o.awtScale)
        assertEquals(2.0, o.composeFactor)
        // A host that cannot take a Compose factor (popups as own windows) stays at 1, the rest goes to the text.
        assertEquals(1.0, o.integerOnly().composeFactor)
        assertEquals(1, o.integerOnly().awtScale)
        assertEquals(2.0, o.integerOnly().textFactor)
    }

    /**
     * WebKitGTK rounds the scale (Compose popups are own windows) and sets GTK's font DPI to 96: a 125 % desktop lost
     * its text scaling entirely. The rounding's leftover goes to the font DPI instead.
     */
    @Test fun integerOnlyKeepsTheLeftoverAsTextFactor() {
        assertEquals(1.25, detect(resources = "Xft.dpi: 120")!!.integerOnly().textFactor)
        assertEquals(0.75, detect(resources = "Xft.dpi: 144")!!.integerOnly().textFactor)
        assertEquals(1.0, detect(resources = "Xft.dpi: 192")!!.integerOnly().textFactor)
        assertEquals(1.0, detect(resources = "Xft.dpi: 120")!!.textFactor)
    }

    private class Blob(val order: ByteOrder) {
        val parts = mutableListOf<ByteArray>()
        var count = 0
        private fun buf(size: Int) = ByteBuffer.allocate(size).order(order)
        private fun header(type: Int, name: String): ByteBuffer {
            val n = name.toByteArray()
            val padded = (n.size + 3) / 4 * 4
            return buf(4 + padded + 4).apply { put(type.toByte()); put(0); putShort(n.size.toShort()); put(n); position(4 + padded); putInt(0) }
        }
        fun int(name: String, v: Int) { parts += header(0, name).array(); parts += buf(4).putInt(v).array(); count++ }
        fun string(name: String, v: String) {
            val b = v.toByteArray()
            parts += header(1, name).array()
            parts += buf(4 + (b.size + 3) / 4 * 4).apply { putInt(b.size); put(b) }.array()
            count++
        }
        fun color(name: String) { parts += header(2, name).array(); parts += buf(8).array(); count++ }
    }

    private fun xsettingsBlob(order: ByteOrder, build: Blob.() -> Unit): ByteArray {
        val b = Blob(order).apply(build)
        val head = ByteBuffer.allocate(12).order(order).apply {
            put(if (order == ByteOrder.LITTLE_ENDIAN) 0 else 1); put(0); put(0); put(0); putInt(7); putInt(b.count)
        }.array()
        return (listOf(head) + b.parts).reduce { a, c -> a + c }
    }
}
