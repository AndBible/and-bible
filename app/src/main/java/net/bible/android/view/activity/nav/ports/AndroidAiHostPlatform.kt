package net.bible.android.view.activity.nav.ports

import net.bible.android.activity.R
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.app.ai.AI_CUSTOM_LANGUAGE_TAG
import net.bible.sharedui.app.ai.AiHostPlatform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Android half of the AI cluster: locale arrays, the date format and the error dialog. */
class AndroidAiHostPlatform(private val activity: ActivityBase) : AiHostPlatform {
    /**
     * Builds the language option list from the `prefs_interface_locale_*` string-arrays: mirrors classic
     * `AiConnectionSettingsActivity.setupAiLanguage`'s option set (an "app default" entry, one per non-empty locale
     * code, and a trailing [AI_CUSTOM_LANGUAGE_TAG] sentinel).
     */
    override fun languageChoices(): List<SettingsItem.Choice> {
        val descriptions = activity.resources.getStringArray(R.array.prefs_interface_locale_descriptions)
        val codes = activity.resources.getStringArray(R.array.prefs_interface_locale_values)
        val choices = mutableListOf<SettingsItem.Choice>()
        choices.add(
            SettingsItem.Choice(
                value = "",
                label = activity.getString(R.string.ai_language_app_default, Locale.getDefault().displayLanguage),
            ),
        )
        for (i in codes.indices) {
            val code = codes[i]
            if (code.isNotEmpty()) choices.add(SettingsItem.Choice(value = code, label = descriptions[i]))
        }
        choices.add(SettingsItem.Choice(value = AI_CUSTOM_LANGUAGE_TAG, label = activity.getString(R.string.ai_language_custom)))
        return choices
    }

    override fun formatLogTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

    override suspend fun showErrorDialog(message: String, cause: Throwable) {
        ErrorReportControl.showErrorDialog(activity, message, exception = cause)
    }
}
