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

package net.bible.android.view.activity.bookmark

import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings

import java.util.Collections
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter.resetDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix round 1, finding 3: the label manager's exit-time writes run once per visit, and a failure is
 * logged by the application scope even though nobody may await it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class ManageLabelsWritesTest {
    private val appScope = AppCoroutineScope() // the production context, including its logging handler
    private lateinit var control: BookmarkControl
    private val errors = Collections.synchronizedList(mutableListOf<String>())
    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    @Before fun setUp() {
        Log.sinkOverride = { level, tag, msg, tr -> if (level == LogLevel.ERROR) errors += "$tag: $msg ${tr ?: ""}" }
        control = BookmarkControl(mock(WindowControl::class.java), testAppSettings(), testCoreStrings(), OrderedLauncher(appScope))
    }

    @After fun tearDown() {
        try {
            appScope.cancel()
            resetDatabase()
        } finally {
            Log.sinkOverride = null
        }
    }

    @Test fun `a second save while the first is writing is refused and the new label is written once`() {
        val writes = ManageLabelsWrites(control, appScope)
        val fresh = Label(new = true, name = "Fresh")
        val tempId = fresh.id.toString()

        val first = writes.start(emptyList(), emptyList(), listOf(fresh))
        val second = writes.start(emptyList(), emptyList(), listOf(fresh))

        assertNotNull(first)
        assertNull("a second save must be refused while the first runs", second)
        val remap = runBlocking { withTimeout(10_000) { first!!.await() } }
        assertEquals(fresh.id.toString(), remap[tempId])
        assertEquals(1, runBlocking { dao.allLabelsSortedByName() }.count { it.name == "Fresh" })
        assertTrue("no ERROR expected, got $errors", errors.isEmpty())
    }

    @Test fun `a failed save is logged by the app scope and can be retried`() {
        val writes = ManageLabelsWrites(control, appScope)
        val broken = Label(id = IdType.empty(), new = true, name = "Broken") // insertOrUpdateLabel refuses an empty id

        val first = writes.start(emptyList(), emptyList(), listOf(broken))!!
        runBlocking {
            withTimeout(10_000) {
                try { first.await(); fail("the write should have failed") } catch (e: RuntimeException) { }
            }
            // The handler runs after the deferred completed; give it a moment.
            withTimeout(10_000) { while (errors.none { it.startsWith("AppCoroutineScope:") }) kotlinx.coroutines.delay(10) }
        }

        val retry = writes.start(emptyList(), emptyList(), listOf(Label(new = true, name = "Retry")))
        assertNotNull("a failed save must release the guard", retry)
        runBlocking { withTimeout(10_000) { retry!!.await() } }
        assertEquals(1, runBlocking { dao.allLabelsSortedByName() }.count { it.name == "Retry" })
    }
}
