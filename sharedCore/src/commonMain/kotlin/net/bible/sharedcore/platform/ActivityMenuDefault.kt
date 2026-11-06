package net.bible.sharedcore.platform

/**
 * Per-platform default of the "Show background activity in the menu" preference (#4007): true where the activity
 * center is the only place that shows background work (desktop, iOS), false on Android, which has notifications.
 * A stored user choice always wins over this default.
 */
fun interface ActivityMenuDefault {
    fun enabledByDefault(): Boolean
}
