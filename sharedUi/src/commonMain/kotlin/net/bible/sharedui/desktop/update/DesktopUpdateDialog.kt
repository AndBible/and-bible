package net.bible.sharedui.desktop.update

import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.toAnswers
import net.bible.sharedui.strings.keyed.StringTable

/** The desktop update offer, shared with screenshot tests so its copy and choices cannot drift. */
fun desktopUpdateDialog(table: StringTable, available: String, current: String, dontNotify: Boolean = false): AppDialogRequest.Options =
    AppDialogRequest.Options(
        title = table.text("desktop_update_available_title"),
        message = "${table.fmt("desktop_update_available_message", available, current)}<br><br>${table.text("desktop_update_reenable")}",
        options = listOf(
            SettingsItem.Choice("download", table.text("desktop_update_download")),
            SettingsItem.Choice("whats_new", table.text("desktop_update_whats_new")),
            SettingsItem.Choice("snooze_day", table.text("desktop_update_snooze_day")),
            SettingsItem.Choice("snooze_week", table.text("desktop_update_snooze_week")),
            SettingsItem.Choice("snooze_month", table.text("desktop_update_snooze_month")),
            SettingsItem.Choice("skip", table.text("desktop_update_skip")),
        ).toAnswers(),
        dismissText = null, asActionSheet = false, cancellable = false,
        checkboxLabel = table.text("desktop_update_dont_notify"), checkboxChecked = dontNotify,
    )
