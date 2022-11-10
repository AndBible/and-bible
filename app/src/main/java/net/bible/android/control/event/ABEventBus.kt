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
package net.bible.android.control.event

import de.greenrobot.event.EventBus
import java.util.ArrayList

object ABEventBus  {
    private val subscribers = ArrayList<Any>()

    /**
     * Check not registered before registering to avoid exception
     */
    fun safelyRegister(subscriber: Any) {
        val defaulteventBus = EventBus.getDefault()
        if (!defaulteventBus.isRegistered(subscriber)) {
            defaulteventBus.register(subscriber)
            subscribers.add(subscriber)
        }
    }

    fun register(subscriber: Any) {
        EventBus.getDefault().register(subscriber)
        subscribers.add(subscriber)
    }

    fun unregister(subscriber: Any) {
        EventBus.getDefault().unregister(subscriber)
        subscribers.remove(subscriber)
    }

    /**
     * Between tests we need to clean up
     */
    fun unregisterAll() {
        for (subscriber in ArrayList(subscribers)) {
            unregister(subscriber)
        }
    }

    fun post(event: Any) {
        EventBus.getDefault().post(event)
    }
}
