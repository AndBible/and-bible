package net.bible.desktop

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The XSETTINGS values the scale is read from (null when absent). [xftDpi] and [unscaledDpi] are in dots per inch
 * (`Xft/DPI` / 1024, `Gdk/UnscaledDPI` / 1024; GNOME's unscaled one is the text-scaled DPI before the window scale).
 */
internal data class XSettingsValues(val windowScalingFactor: Int?, val xftDpi: Double?, val unscaledDpi: Double? = null)

/**
 * Everything the Linux display scale is decided from: [env] and [property] are the process environment and the system
 * properties, [xsettings] the parsed `_XSETTINGS_SETTINGS` of the screen's XSETTINGS manager, [xResources] the root
 * window's `RESOURCE_MANAGER` text (what `xrdb -query` prints).
 */
internal data class DisplayScaleInputs(
    val isLinux: Boolean,
    val env: Map<String, String>,
    val property: (String) -> String?,
    val xsettings: XSettingsValues?,
    val xResources: String?,
    /** Why the probe found nothing, for the default's source (see [X11DisplayScaleProbe.Result.note]). */
    val probeNote: String? = null,
)

/**
 * The UI scale of the desktop app and where it came from ([source], for the log line and bug reports).
 *
 * The JDK 17 X11 toolkit supports only integer scales (`sun.java2d.uiScale=1.5` renders at 1), so the scale is split:
 * [awtScale], the integer part, goes to the JDK ([applyToJdk]), and the rest, [composeFactor], multiplies the Compose
 * density of the window content (and with it the off-screen CEF pane's device scale). Hosts whose popups are own windows
 * use [integerOnly] instead; the rounding's leftover is kept as [textFactor] (WebKitGTK's font DPI).
 *
 * [jdkScaling] false: the user switched JDK scaling off (`-Dsun.java2d.uiScale.enabled=false`); AWT stays at 1 and the
 * whole scale goes through the Compose density.
 */
internal data class DisplayScale(
    val scale: Double,
    val source: String,
    val textFactor: Double = 1.0,
    val jdkScaling: Boolean = true,
) {
    val awtScale: Int get() = if (!jdkScaling) 1 else floor(scale + EPSILON).toInt().coerceAtLeast(1)
    val composeFactor: Double get() = round4(scale / awtScale)

    /** Sets the JDK's own scale and keeps skiko from replacing it. Before AWT initialises (it reads `sun.java2d.uiScale` once). */
    fun applyToJdk(setProperty: (String, String) -> Unit) {
        // Compose's Swing globals turn skiko's Linux auto-DPI on by default; it would overwrite the scale with its own
        // (Xft.dpi only) whenever skiko initialises before AWT (the WebKitGTK app does). This decision replaces it.
        setProperty("skiko.linux.autodpi", "false")
        if (!jdkScaling) return
        setProperty("sun.java2d.uiScale.enabled", "true")
        setProperty("sun.java2d.uiScale", awtScale.toString())
    }

    /**
     * This scale rounded to a whole number (at least 1), for hosts whose Compose popups and dialogs are own windows
     * (`compose.layers.type=WINDOW`): such a window takes its density from AWT, so a [composeFactor] would mis-size them.
     * The leftover (`scale / whole`) becomes [textFactor]. Without [jdkScaling] the whole number is 1.
     */
    fun integerOnly(): DisplayScale {
        val whole = if (!jdkScaling) 1.0 else floor(scale + 0.5).coerceAtLeast(1.0)
        return if (whole == scale) this else
            copy(scale = whole, source = "$source, ${fmt(scale)} rounded: Compose popups are own windows", textFactor = round4(scale / whole))
    }

    fun describe(): String = "display scale ${fmt(scale)} ($source): AWT $awtScale, Compose x${fmt(composeFactor)}" +
        if (textFactor != 1.0) ", text x${fmt(textFactor)}" else ""

    private companion object {
        const val EPSILON = 1e-6
    }
}

private fun round4(v: Double): Double = (v * 10_000).roundToInt() / 10_000.0

private fun fmt(v: Double): String = if (v == floor(v)) v.toInt().toString() else v.toString()

/**
 * Decides the Linux UI scale (#4006). Pure: the X11 reads are done by [X11DisplayScaleProbe].
 *
 * In this order, the first usable one wins:
 * 1. the manual override, `-Dandbible.uiScale` or `ANDBIBLE_UI_SCALE` (0.5 to 4, fractional allowed);
 * 2. `-Dsun.java2d.uiScale.enabled=false`: the user switched JDK scaling off, scale 1 (the override above still works);
 * 3. an explicit JDK scale, `-Dsun.java2d.uiScale` or `J2D_UISCALE`, then `GDK_SCALE` (what the JDK itself reads);
 * 4. the desktop's scale, the largest of: XSETTINGS `Xft/DPI` / 96 (GNOME's gsd-xsettings, Xfce, KDE's xsettingsd: what
 *    GTK and Chromium scale by), else the `Xft.dpi` X resource / 96 (KDE, Xfce, `~/.Xresources`); and XSETTINGS
 *    `Gdk/WindowScalingFactor` times `Gdk/UnscaledDPI` / 96 (GNOME publishes both consistently; an xsettingsd may set the
 *    factor with an unscaled `Xft/DPI`);
 * 5. 1.
 *
 * Scale values take the JDK's forms: `1.5`, `1.5x`, `150%`, `144dpi`.
 *
 * GNOME on Wayland reaches X11 apps through Xwayland: with Xwayland native scaling, GNOME publishes the scale in
 * XSETTINGS/`Xft.dpi` and expects the app to scale itself; without it, Mutter scales X11 windows itself and publishes 96 DPI.
 * Reading the Mutter monitor scale (D-Bus) would therefore double the scale in the second mode, so it is not used.
 * A desktop-derived scale below 1 is raised to 1. Not Linux: null, the JDK's own (Windows) scaling stays.
 */
internal object DisplayScaleDetector {
    const val OVERRIDE_PROPERTY = "andbible.uiScale"
    const val OVERRIDE_ENV = "ANDBIBLE_UI_SCALE"
    private const val JDK_ENABLED_PROPERTY = "sun.java2d.uiScale.enabled"
    private const val MIN = 0.5
    private const val MAX = 4.0
    private const val BASE_DPI = 96.0

    fun detect(inputs: DisplayScaleInputs): DisplayScale? {
        if (!inputs.isLinux) return null
        val ignored = mutableListOf<String>()
        fun explicit(name: String, raw: String?, label: String): DisplayScale? {
            raw ?: return null
            val v = parseScale(raw)?.takeIf { it in MIN..MAX }
            if (v == null) { ignored += "ignored $name=$raw"; return null }
            return DisplayScale(round(v), label)
        }
        fun note(source: String) = if (ignored.isEmpty()) source else "$source; ${ignored.joinToString("; ")}"
        val env = inputs.env
        val jdkScaling = inputs.property(JDK_ENABLED_PROPERTY)?.trim()?.equals("false", ignoreCase = true) != true
        val chosen = (
            explicit(OVERRIDE_PROPERTY, inputs.property(OVERRIDE_PROPERTY), "-D$OVERRIDE_PROPERTY=${inputs.property(OVERRIDE_PROPERTY)?.trim()}")
                ?: explicit(OVERRIDE_ENV, env[OVERRIDE_ENV], "$OVERRIDE_ENV=${env[OVERRIDE_ENV]?.trim()}")
            )?.copy(jdkScaling = jdkScaling)
            ?: (if (!jdkScaling) DisplayScale(1.0, "-D$JDK_ENABLED_PROPERTY=false", jdkScaling = false) else null)
            ?: explicit("sun.java2d.uiScale", inputs.property("sun.java2d.uiScale"), "-Dsun.java2d.uiScale=${inputs.property("sun.java2d.uiScale")?.trim()}")
            ?: explicit("J2D_UISCALE", env["J2D_UISCALE"], "J2D_UISCALE=${env["J2D_UISCALE"]?.trim()}")
            ?: env["GDK_SCALE"]?.let { raw ->
                raw.trim().toIntOrNull()?.takeIf { it in 1..MAX.toInt() }?.let { DisplayScale(it.toDouble(), "GDK_SCALE=$it") }
                    ?: null.also { ignored += "ignored GDK_SCALE=$raw" }
            }
            ?: desktopScale(inputs.xsettings, inputs.xResources)
            ?: DisplayScale(1.0, "default (no system scale found${inputs.probeNote?.let { ": $it" }.orEmpty()})")
        return chosen.copy(source = note(chosen.source))
    }

    /** Step 4 of [detect]: the largest scale the desktop publishes (the first named wins a tie), or null. */
    private fun desktopScale(xsettings: XSettingsValues?, xResources: String?): DisplayScale? {
        val dpi = xsettings?.xftDpi?.let { DisplayScale(fromDpi(it), "XSETTINGS Xft/DPI ${fmt(it)}") }
            ?: xResources?.let(::parseXftDpi)?.let { DisplayScale(fromDpi(it), "X resource Xft.dpi ${fmt(it)}") }
        val factor = xsettings?.windowScalingFactor?.takeIf { it in 1..MAX.toInt() }?.let { f ->
            val unscaled = xsettings.unscaledDpi
            if (unscaled == null) DisplayScale(f.toDouble(), "XSETTINGS Gdk/WindowScalingFactor $f")
            else DisplayScale(round((f * unscaled / BASE_DPI).coerceIn(1.0, MAX)), "XSETTINGS Gdk/WindowScalingFactor $f x Gdk/UnscaledDPI ${fmt(unscaled)}")
        }
        return listOfNotNull(dpi, factor).maxByOrNull { it.scale }
    }

    /** A scale in one of the JDK's `sun.java2d.uiScale` forms (`2`, `2x`, `200%`, `192dpi`), or null. */
    fun parseScale(raw: String): Double? {
        val t = raw.trim().lowercase()
        return when {
            t.endsWith("dpi") -> t.removeSuffix("dpi").trim().toDoubleOrNull()?.div(BASE_DPI)
            t.endsWith("%") -> t.removeSuffix("%").trim().toDoubleOrNull()?.div(100)
            t.endsWith("x") -> t.removeSuffix("x").trim().toDoubleOrNull()
            else -> t.toDoubleOrNull()
        }
    }

    private fun fromDpi(dpi: Double): Double = round((dpi / BASE_DPI).coerceIn(1.0, MAX))

    private fun round(v: Double): Double = (v * 100).roundToInt() / 100.0

    /** The `Xft.dpi` value of a `RESOURCE_MANAGER` string, or null. */
    fun parseXftDpi(resources: String): Double? = resources.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("Xft.dpi:") }
        ?.substringAfter(':')?.trim()?.toDoubleOrNull()
        ?.takeIf { it > 0 }

    /**
     * Parses an XSETTINGS `_XSETTINGS_SETTINGS` property (byte order, 3 pad, serial, count, then each setting: type,
     * pad, name length, name padded to 4, last-change serial, value) for `Gdk/WindowScalingFactor`, `Gdk/UnscaledDPI` and
     * `Xft/DPI`. Null when
     * the header is unreadable; settings after a truncated one are dropped.
     */
    fun parseXSettings(data: ByteArray): XSettingsValues? {
        if (data.size < 12) return null
        val buf = ByteBuffer.wrap(data).order(if (data[0].toInt() == 0) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        buf.position(8)
        val count = buf.int
        var scaling: Int? = null
        var dpi: Double? = null
        var unscaled: Double? = null
        try {
            repeat(count) {
                val type = buf.get().toInt()
                buf.get()
                val nameLength = buf.short.toInt() and 0xffff
                val nameBytes = ByteArray(nameLength).also { buf.get(it) }
                buf.position(buf.position() + pad(nameLength) - nameLength)
                buf.int // last change serial
                val name = String(nameBytes, Charsets.UTF_8)
                when (type) {
                    0 -> {
                        val v = buf.int
                        if (name == "Gdk/WindowScalingFactor") scaling = v
                        if (name == "Xft/DPI" && v > 0) dpi = v / 1024.0
                        if (name == "Gdk/UnscaledDPI" && v > 0) unscaled = v / 1024.0
                    }
                    1 -> { val len = buf.int; buf.position(buf.position() + pad(len)) }
                    2 -> buf.position(buf.position() + 8)
                    else -> return XSettingsValues(scaling, dpi, unscaled)
                }
            }
        } catch (_: RuntimeException) {
            // Truncated or malformed: keep what was read.
        }
        return XSettingsValues(scaling, dpi, unscaled)
    }

    private fun pad(n: Int) = (n + 3) / 4 * 4
}

/**
 * The process's display scale (#4006), decided once by [init] at the start of [runDesktopApp], before AWT. Read by the
 * window (Compose density, window size), the log and the engines (KCEF windowed switch, WebKitGTK `GDK_SCALE`).
 */
object DesktopDisplayScale {
    @Volatile private var decided: DisplayScale? = null

    /** The integer scale given to the JDK (and to GTK as `GDK_SCALE`); 1 off Linux or before [init]. */
    val awtScale: Int get() = decided?.awtScale ?: 1

    /** The fractional rest, applied to the Compose density; 1 off Linux, before [init] or with integerOnly. */
    val composeFactor: Double get() = decided?.composeFactor ?: 1.0

    /** The rest a whole-number ([DisplayScale.integerOnly]) host leaves to the text size; 1 otherwise. */
    val textFactor: Double get() = decided?.textFactor ?: 1.0

    /** The whole scale; 1 off Linux or before [init]. */
    val scale: Double get() = decided?.scale ?: 1.0

    /** The log line naming the scale and its source, or null off Linux. */
    val description: String? get() = decided?.describe()

    /**
     * Detects the Linux scale and sets the JDK's integer scale. [integerOnly]: Compose layers are own windows (windowed
     * web panes), see [DisplayScale.integerOnly]. Idempotent (the first call decides); must run before any AWT use.
     */
    @Synchronized
    fun init(integerOnly: Boolean) {
        if (decided != null) return
        val linux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
        if (!linux) return
        val env = System.getenv()
        val x11 = X11DisplayScaleProbe.read(env)
        decided = DisplayScaleDetector.detect(
            DisplayScaleInputs(true, env, System::getProperty, x11.xsettings, x11.xResources, x11.note()),
        )?.let { if (x11.waitedMs > 0 && "waited" !in it.source) it.copy(source = "${it.source}; ${x11.note()}") else it }
            ?.let { if (integerOnly) it.integerOnly() else it }?.also { it.applyToJdk(System::setProperty) }
    }
}

/** [content] with the Compose density multiplied by [factor] (the fractional part of the display scale). */
@androidx.compose.runtime.Composable
internal fun ScaledDensity(factor: Double, content: @androidx.compose.runtime.Composable () -> Unit) {
    if (factor == 1.0) return content()
    val base = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * factor.toFloat(), base.fontScale),
        net.bible.sharedui.desktop.webengine.LocalDensityOverAwt provides factor,
        content = content,
    )
}
