package net.bible.android.platform

import net.bible.android.control.speak.PERSIST_SETTINGS
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.service.common.CommonUtils

/** Widgets need these raw preferences before the database is available. */
class AndroidSpeakSettingsStore : SpeakSettingsStore {
    override fun load(): String? = CommonUtils.realSharedPreferences.getString(PERSIST_SETTINGS, "")
    override fun save(json: String) {
        CommonUtils.realSharedPreferences.edit().putString(PERSIST_SETTINGS, json).apply()
    }
}
