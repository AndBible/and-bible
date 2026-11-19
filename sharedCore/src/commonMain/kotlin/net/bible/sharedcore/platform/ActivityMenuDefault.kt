package net.bible.sharedcore.platform

/**
 * Per-platform default of the "Show background activity in the menu" preference (#4007): true on every
 * platform (#4157); on Android the in-menu activity complements the system notifications. A stored user choice always wins over this default.
 */
fun interface ActivityMenuDefault {
    fun enabledByDefault(): Boolean
}
