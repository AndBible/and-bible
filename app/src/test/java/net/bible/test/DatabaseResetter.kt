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

package net.bible.test

import android.os.Looper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import net.bible.service.db.DatabaseContainer
import org.robolectric.Shadows
import java.lang.IllegalStateException
import java.lang.reflect.Field

/**
 * Reset db between tests @see https://github.com/robolectric/robolectric/issues/1890
 *
 * Created by mjden on 31/08/2017.
 */
object DatabaseResetter {
    @JvmStatic
    fun resetDatabase() {
        try {
            GlobalScope.cancel("Time to stop! Test already ended...")
        } catch (e: IllegalStateException) {
            if(e.message?.startsWith("Scope cannot be cancelled because") == false) {
                throw e
            }
        }
        //DatabaseContainer.db.openHelper.close()
        //val looper = Shadows.shadowOf(Looper.getMainLooper())
        //looper.idle()
        //val stacks = Thread.getAllStackTraces()

        // Something is hanging there still due to kotlin coroutines. This seem to help.
        // Sorry, not motivated at this time to investigate this any further if this workaround works.

        Thread.sleep(300)
        resetSingleton(DatabaseContainer::class.java, "instance")
    }

    private fun resetSingleton(class_: Class<*>, fieldName: String) {
        val instance: Field
        try {
            instance = class_.getDeclaredField(fieldName)
            instance.isAccessible = true
            instance[null] = null
        } catch (e: Exception) {
            throw RuntimeException()
        }
    }
}
