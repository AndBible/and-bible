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
package net.bible.android.view.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.control.widget.VerseWidgetContent
import net.bible.android.database.IdType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget of the add-on collections check showed the default "Random verse" after "Add widget" although the
 * configuration screen had saved an add-on label: Glance does not rerun `provideGlance` of a session that is still
 * running, so a configuration read once at its top stayed stale. [verseWidgetLoads] must follow later saves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = Application::class)
class VerseWidgetLoadsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val widgetId = 41
    private val unavailable = VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.LABEL_MISSING)

    @Test fun aSaveAfterTheFirstLoadIsLoadedAndNotLostToTheDefault() = runBlocking {
        VerseWidgetConfigStore.delete(context, widgetId)
        val seen = mutableListOf<VerseWidgetConfig>()
        val loads = verseWidgetLoads(context, widgetId, { VerseWidgetConfig(source = VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE), seed = 1L) }) {
            seen += it; unavailable
        }
        // The first draw: nothing saved yet, so the default (the widget was just placed).
        assertTrue(loads.initial.config.source is VerseSource.Random)

        // The configuration screen saves the user's choice after that first draw, while the session is still alive.
        val label = IdType()
        VerseWidgetConfigStore.save(context, widgetId, VerseWidgetConfig(source = VerseSource.Label(label), seed = 2L))

        val latest = withTimeout(10_000) { loads.updates.first { (it.config.source as? VerseSource.Label)?.labelId == label } }
        assertEquals(VerseSource.Label(label), latest.config.source)
        assertEquals(VerseSource.Label(label), seen.last().source)
        VerseWidgetConfigStore.delete(context, widgetId)
    }

    @Test fun aShuffleIsLoadedToo() = runBlocking {
        VerseWidgetConfigStore.save(context, widgetId, VerseWidgetConfig(source = VerseSource.Label(IdType()), seed = 3L))
        val loads = verseWidgetLoads(context, widgetId, { error("has a config") }) { unavailable }
        VerseWidgetConfigStore.shuffle(context, widgetId)
        val latest = withTimeout(10_000) { loads.updates.first { it.config.shuffleCount == 1 } }
        assertEquals(1, latest.config.shuffleCount)
        VerseWidgetConfigStore.delete(context, widgetId)
    }
}
