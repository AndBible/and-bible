/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import net.bible.service.common.Logger
import java.util.*

/**
 * Change locale on older Nougat+ Android devices.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class NougatPlusLocaleChanger : LocaleChanger {
    private val logger = Logger(this.javaClass.name)
    @TargetApi(Build.VERSION_CODES.N)
    override fun changeLocale(context: Context, language: String): Context {
        logger.debug("Update resources N plus")
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
