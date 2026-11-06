package net.bible.sharedui.reading.toolbar

import net.bible.sharedcore.reading.OptionsMenuItem

const val SYNC_NOW_MENU_ID = "syncNow"

/** The row, or null when sync is not set up or the activity section shows it instead. */
fun syncNowMenuItem(available: Boolean, running: Boolean, inActivitySection: Boolean, label: String): OptionsMenuItem? =
    if (!available || inActivitySection) null else OptionsMenuItem(
        SYNC_NOW_MENU_ID, label, checkable = false, checked = false, enabled = !running,
        opensDialog = false, iconKey = "ic_sync_white_24dp",
    )
