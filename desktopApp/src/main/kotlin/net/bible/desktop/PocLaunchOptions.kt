package net.bible.desktop

import net.bible.sharedui.poc.PocScenario

/**
 * Run-time switches for the D1 PoC, read from system properties (spike measurements; D1 only):
 * - `andbible.poc.scenario`: a [PocScenario] id or name (`single`, `split2`, `split3`, ...); default `split2`.
 * - `andbible.poc.dark`: `true` starts in the dark theme.
 * - `andbible.poc.spike`: `true` logs spike timings (`[spike] <ms since JVM start> ...`: pane creation, the first
 *   bridge message of each kind, first verses in the DOM).
 * - `andbible.poc.themeFlipMs`: flips the theme every N ms (theme flip without pane recreation).
 * - `andbible.poc.overlay`: `popup` or `dialog` shows a Compose overlay over the panes (z-order check).
 *
 * An unknown scenario fails fast instead of silently falling back.
 */
data class PocLaunchOptions(
    val scenario: PocScenario = PocScenario.SPLIT2,
    val dark: Boolean = false,
    val spikeLog: Boolean = false,
    val themeFlipMs: Long? = null,
    val overlay: String? = null,
) {
    companion object {
        fun from(property: (String) -> String?): PocLaunchOptions {
            val scenarioText = property("andbible.poc.scenario")?.trim()?.takeIf { it.isNotEmpty() }
            val scenario = scenarioText?.let { text ->
                PocScenario.entries.firstOrNull { it.id.equals(text, true) || it.name.equals(text, true) }
                    ?: throw IllegalArgumentException(
                        "Unknown andbible.poc.scenario '$text' (one of ${PocScenario.entries.joinToString { it.id }})",
                    )
            } ?: PocScenario.SPLIT2
            val overlay = property("andbible.poc.overlay")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            require(overlay == null || overlay == "popup" || overlay == "dialog") {
                "Unknown andbible.poc.overlay '$overlay' (popup or dialog)"
            }
            return PocLaunchOptions(
                scenario = scenario,
                dark = property("andbible.poc.dark").toBoolean(),
                spikeLog = property("andbible.poc.spike").toBoolean(),
                themeFlipMs = property("andbible.poc.themeFlipMs")?.toLongOrNull()?.takeIf { it > 0 },
                overlay = overlay,
            )
        }
    }
}
