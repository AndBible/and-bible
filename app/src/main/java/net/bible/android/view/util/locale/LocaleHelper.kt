/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.util.locale

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.preference.PreferenceManager
import org.apache.commons.lang3.StringUtils
import java.util.Locale

/**
 * This class is used to change your application locale.
 * @see [article](http://gunhansancar.com/change-language-programmatically-in-android/)
 */
object LocaleHelper {
    private const val SELECTED_LANGUAGE = "locale_pref"
    fun translateTitle(activity: Activity) {
        if (isLocaleOverridden(activity)) {
            // http://stackoverflow.com/questions/22884068/troubles-with-activity-title-language
            try {
                val labelRes =
                    activity.packageManager.getActivityInfo(activity.componentName, 0).labelRes
                if (labelRes != 0) activity.setTitle(labelRes)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    /** Fix batch 5 §1.2: one path for API 23-36 (`createConfigurationContext` exists since 17). */
    fun localized(base: Context): Context {
        val locale = uiLocaleFor(getOverrideLanguage(base))
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration) // a COPY: never mutate the shared one
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    /** The override, or -- for "" (Default) -- the SYSTEM locale, so switching back really switches back. */
    fun uiLocaleFor(language: String): Locale =
        if (language.isNotEmpty()) Locale.forLanguageTag(language)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Resources.getSystem().configuration.locales[0]
        else @Suppress("DEPRECATION") Resources.getSystem().configuration.locale

    private fun isLocaleOverridden(context: Context): Boolean {
        return StringUtils.isNotEmpty(getOverrideLanguage(context))
    }

    fun getOverrideLanguage(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(SELECTED_LANGUAGE, "")!!
    }
}
