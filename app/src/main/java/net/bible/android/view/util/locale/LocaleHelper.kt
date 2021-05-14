/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.view.util.locale

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import net.bible.service.common.CommonUtils
import org.apache.commons.lang3.StringUtils

/**
 * This class is used to change your application locale.
 * @see [article](http://gunhansancar.com/change-language-programmatically-in-android/)
 */
object LocaleHelper {
    private val localeChangerFactory = LocaleChangerFactory()
    private const val SELECTED_LANGUAGE = "locale_pref"
    fun translateTitle(activity: Activity) {
        if (isLocaleOverridden(activity)) {
            // http://stackoverflow.com/questions/22884068/troubles-with-activity-title-language
            try {
                val labelRes = activity.packageManager.getActivityInfo(activity.componentName, 0).labelRes
                activity.setTitle(labelRes)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    fun onAttach(context: Context?): Context? {
        val overrideLang = getOverrideLanguage(context)
        return if (StringUtils.isNotEmpty(overrideLang)) {
            localeChangerFactory.localeChanger.changeLocale(context, overrideLang)
        } else {
            context
        }
    }

    private fun isLocaleOverridden(context: Context): Boolean {
        return StringUtils.isNotEmpty(getOverrideLanguage(context))
    }

    fun getOverrideLanguage(context: Context?): String? {
        return CommonUtils.sharedPreferences.getString(SELECTED_LANGUAGE, "")
    }
}
