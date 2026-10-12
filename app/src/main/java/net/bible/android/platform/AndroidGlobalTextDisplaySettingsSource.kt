package net.bible.android.platform

import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils

/** Reads the live global row rather than caching a copy. */
class AndroidGlobalTextDisplaySettingsSource : GlobalTextDisplaySettingsSource {
    override val current: WorkspaceEntities.TextDisplaySettings
        get() = CommonUtils.globalTextDisplaySettings
}
