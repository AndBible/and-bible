package net.bible.android.platform

import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.GlobalTextDisplaySettingsStore

/** Reads the [GlobalTextDisplaySettingsStore] snapshot. */
class AndroidGlobalTextDisplaySettingsSource(private val store: () -> GlobalTextDisplaySettingsStore) : GlobalTextDisplaySettingsSource {
    override val current: WorkspaceEntities.TextDisplaySettings
        get() = store().current
}
