package net.bible.sharedui.app

/** Finite real-feature entrypoints for debug UI tests; never accepts raw navigation payloads. */
internal enum class DebugFeatureScenario {
    CHOOSER, PLAN_SETTINGS, LABELS, DOWNLOAD;

    companion object {
        fun parse(debug: Boolean, arguments: List<String>): DebugFeatureScenario? {
            if (!debug) return null
            val index = arguments.indexOf("-featureScenario")
            if (index < 0) return null
            return when (arguments.getOrNull(index + 1)) {
                "chooser" -> CHOOSER
                "plan-settings" -> PLAN_SETTINGS
                "labels" -> LABELS
                "download" -> DOWNLOAD
                else -> null
            }
        }
    }
}

internal fun debugSettingsLauncherVisible(route: String?, loaded: Boolean): Boolean =
    loaded && route == net.bible.sharedcore.nav.NavRoutes.DAILY_READING_PATTERN

internal fun debugStandardFixture(debug: Boolean, arguments: List<String>): Boolean =
    debug && "-standardTestFixture" in arguments

/** Device defaults are opt-in; -resetData alone must continue to leave them untouched. */
internal fun applyDebugStandardFixture(
    debug: Boolean, arguments: List<String>,
    setString: (String, String) -> Unit, setBoolean: (String, Boolean) -> Unit,
) {
    if (!debugStandardFixture(debug, arguments)) return
    setString("locale_pref", "en")
    setBoolean("discrete_mode", false)
    setBoolean("show_calculator", false)
}

internal fun consumeDebugDocument(
    scenario: DebugFeatureScenario?, ready: Boolean,
    consume: () -> net.bible.sharedcore.nav.DocumentResult?, select: (String) -> Unit,
) {
    if (scenario == DebugFeatureScenario.CHOOSER && ready) consume()?.let { select(it.book) }
}

/** Claim only after both normal reading initialization and the existing reference request finish. */
internal class DebugFeatureOnce {
    private var used = false
    fun take(ready: Boolean, referenceApplied: Boolean): Boolean {
        if (used || !ready || !referenceApplied) return false
        used = true
        return true
    }
}
