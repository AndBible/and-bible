package net.bible.sharedui.settings

/** How often the desktop app checks github.com for a newer release. [value] is the stored string. */
enum class DesktopUpdateInterval(val value: String, val intervalMs: Long?) {
    DAILY("daily", 24 * 60 * 60 * 1000L),
    WEEKLY("weekly", 7 * 24 * 60 * 60 * 1000L),
    MONTHLY("monthly", 30 * 24 * 60 * 60 * 1000L),
    NEVER("never", null);

    companion object {
        /**
         * The stored value, or null only when it is absent (not asked yet). The legacy `yes`/`no` map to
         * weekly/never; any other unknown value is weekly, so a damaged or future value never leaves checks off silently.
         */
        fun fromStored(stored: String?): DesktopUpdateInterval? = when (stored) {
            "yes" -> WEEKLY
            "no" -> NEVER
            null -> null
            else -> entries.firstOrNull { it.value == stored } ?: WEEKLY
        }
    }
}

/** Device-local update check frequency; absent on platforms managed by a package store. */
interface DesktopUpdateCheckSetting {
    val interval: DesktopUpdateInterval
    fun setInterval(interval: DesktopUpdateInterval)
    fun observeChanges(onChange: () -> Unit): () -> Unit
}
