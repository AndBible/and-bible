package net.bible.android.platform

import android.content.Context
import android.text.format.Formatter
import android.webkit.URLUtil
import net.bible.android.activity.R
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.common.CommonUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidSyncSettingsPlatform(private val context: Context) : SyncSettingsPlatform {
    override fun credential(key: String): String = CommonUtils.realSharedPreferences.getString(key, "") ?: ""
    override fun setCredential(key: String, value: String) {
        CommonUtils.realSharedPreferences.edit().putString(key, value).apply()
    }
    override fun isValidUrl(value: String): Boolean = URLUtil.isValidUrl(value)
    override fun formatFileSize(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)
    override fun formatSyncTime(timestamp: Long): String =
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/** The disguised variant must not reveal the Bible app name in the Drive explanation. */
@androidx.annotation.StringRes
internal fun syncSummaryAppName(discrete: Boolean): Int =
    if (discrete) R.string.app_name_calculator else R.string.app_name_medium
