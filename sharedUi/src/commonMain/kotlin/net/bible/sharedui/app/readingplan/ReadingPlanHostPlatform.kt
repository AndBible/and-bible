package net.bible.sharedui.app.readingplan


/**
 * The platform half of the reading plan cluster: what [ReadingPlanHost] cannot do in common code because it needs
 * the Activity or JVM-only calendar and configuration types. A host-scoped port, built on first use (see
 * `HostPorts.readingPlanPlatform`).
 */
interface ReadingPlanHostPlatform {
    /** Whether the screen is in portrait; the day's passage names are the full book names only in landscape. */
    val isPortrait: Boolean

    /** The local-time-zone (year, month 1-12, day of month) that [epochMs] falls on. */
    fun localYmd(epochMs: Long): Triple<Int, Int, Int>

    /**
     * [epochMs] moved to the given local date, keeping its local time of day (`Calendar.set(y, m, d)`: the plan's
     * stored start keeps the time of day it had).
     */
    fun withLocalYmd(epochMs: Long, year: Int, month1to12: Int, day: Int): Long
}
