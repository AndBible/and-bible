package net.bible.sharedui.poc

/** I0 PoC launch scenarios, chosen on iOS by the SCREENSHOT_SCENARIO environment variable (XCUITest). */
enum class PocScenario(val id: String, val windowCount: Int, val startRoute: String) {
    SINGLE("single", 1, "reading"),
    SPLIT2("split2", 2, "reading"),
    SPLIT3("split3", 3, "reading"),
    BOOKMARKS("bookmarks", 1, "bookmarks"),
    HISTORY("history", 1, "history"),
    SETTINGS("settings", 1, "settings");

    companion object {
        fun fromName(name: String?): PocScenario = entries.firstOrNull { it.id == name } ?: SINGLE
    }
}
