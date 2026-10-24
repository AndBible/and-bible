package net.bible.sharedui.poc

/** I0 PoC launch scenarios, chosen on iOS by the SCREENSHOT_SCENARIO environment variable (XCUITest). */
enum class PocScenario(val id: String, val windowCount: Int, val startRoute: String) {
    SINGLE("single", 1, "reading"),
    SPLIT2("split2", 2, "reading"),
    SPLIT3("split3", 3, "reading"),
    BOOKMARKS("bookmarks", 1, "bookmarks"),
    HISTORY("history", 1, "history"),
    SETTINGS("settings", 1, "settings"),
    // iOS dispatches the probe before the PoC host; other hosts retain a registered route.
    PROBE("probe", 1, "reading");

    /** Both diagnostic entry routes are disabled in a release binary. */
    fun shouldShowPlatformProbe(debugBinary: Boolean, environmentFlag: String?): Boolean =
        debugBinary && (environmentFlag == "1" || this == PROBE)

    companion object {
        fun fromName(name: String?): PocScenario = entries.firstOrNull { it.id == name } ?: SINGLE
    }
}
