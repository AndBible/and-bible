/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android

import android.content.res.Resources
import android.util.Log
import net.bible.android.control.event.ABEventBus
import net.bible.service.common.CommonUtils

/**
 * Override settings if required
 */
class TestBibleApplication : BibleApplication() {
    init {
        println("TestBibleApplication BibleApplication subclass being used.")
    }

    override val isRunningTests: Boolean = true

    override fun getLocalizedResources(language: String): Resources {
        return application.getResources()
    }

    override fun onCreate() {
        super.onCreate()
        CommonUtils.initializeApp()
    }

    /**
     * This is never called in real system (only in tests). See parent documentation.
     */
    override fun onTerminate() {
        CommonUtils.destroy()
        super.onTerminate()
        ABEventBus.getDefault().unregisterAll()
    }
}
