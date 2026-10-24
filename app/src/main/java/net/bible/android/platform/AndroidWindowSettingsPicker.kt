package net.bible.android.platform

import net.bible.android.activity.R
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.SettingLabels
import net.bible.android.control.page.window.WindowSettingsPicker
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.service.common.GlobalTextDisplaySettingsStore

/** [SettingLabels] over the Android settings menu items. */
class AndroidSettingLabels : SettingLabels {
    override fun label(bundle: SettingsBundle, type: TextDisplaySettings.Types): String = getPrefItem(bundle, type).title.toString()
}

/**
 * The copy-settings picker (spec §3.2 finding 9 — one of three hand-written copies of the same
 * select-all/none toggle), on [Dialogs.multiselect]: Cancel and "OK with nothing checked" both come back
 * as an empty list. Every caller treats an empty selection as "nothing to copy".
 */
class AndroidWindowSettingsPicker(private val globalSettings: GlobalTextDisplaySettingsStore) : WindowSettingsPicker {
    override suspend fun chooseSettingsToCopy(window: Window, types: List<TextDisplaySettings.Types>): List<TextDisplaySettings.Types> {
        val context = CurrentActivityHolder.currentActivity!!
        val repository = window.windowRepository
        return Dialogs.multiselect(
            context,
            context.getString(R.string.copy_settings_title),
            types,
            itemToString = {
                // OptionsMenuItemInterface.title is nullable in general; every TextDisplaySettings.Types
                // row carries one in practice, so the fallback is only there to satisfy the type.
                getPrefItem(SettingsBundle(level = SettingsLevel.WORKSPACE, workspaceId = repository.id, workspaceName = repository.name,
                    workspaceSettings = window.pageManager.textDisplaySettings, globalSettings = globalSettings.current), it).title ?: it.name
            },
        )
    }
}
