/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedui.platform.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetUrlQueueTest {
    private val a: WidgetUrlAction = WidgetUrlAction.SpeakBookmark("a")
    private val b: WidgetUrlAction = WidgetUrlAction.SpeakBookmark("b")

    @Test fun actionsWaitUntilReadyThenRunInOrder() {
        val ran = mutableListOf<WidgetUrlAction>()
        val q = WidgetUrlQueue { action -> ran.add(action) }
        q.submit(a); q.submit(b)
        assertEquals(emptyList<WidgetUrlAction>(), ran)
        q.markReady()
        assertEquals(listOf(a, b), ran)
    }

    @Test fun actionsAfterReadyRunAtOnce() {
        val ran = mutableListOf<WidgetUrlAction>()
        val q = WidgetUrlQueue { action -> ran.add(action) }
        q.markReady()
        q.submit(a)
        assertEquals(listOf(a), ran)
    }

    @Test fun markReadyTwiceDoesNotRunAnythingTwice() {
        val ran = mutableListOf<WidgetUrlAction>()
        val q = WidgetUrlQueue { action -> ran.add(action) }
        q.submit(a); q.markReady(); q.markReady()
        assertEquals(listOf(a), ran)
    }
}
