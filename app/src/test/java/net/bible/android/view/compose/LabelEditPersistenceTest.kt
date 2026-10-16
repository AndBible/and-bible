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
package net.bible.android.view.compose

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.view.activity.bookmark.LabelEditContract
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsWrites
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SU-14 D5: pressing OK in "Edit label" must write an existing label to Room right then, not only
 * when the label manager is left (a process kill between the two lost the edit).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class LabelEditPersistenceTest {
    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    /** Opens the manager's session (WORKSPACE mode) on [host] and returns it, seeded with [label]. */
    private fun openSession(host: NavHostComposeActivity, label: Label, isNew: Boolean) {
        val koin = GlobalContext.get()
        val sessionClass = Class.forName("net.bible.android.view.activity.nav.NavHostComposeActivity\$ManageLabelsSession")
        val session = sessionClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(
            ManageLabelsContract.ManageLabelsData(ManageLabelsContract.Mode.WORKSPACE),
            mutableMapOf(label.id.toString() to label),
            ManageLabelsWrites(koin.get(), koin.get<AppCoroutineScope>()),
        )
        val controller = ManageLabelsController(
            ManageLabelsMode.WORKSPACE, koin.get<ManageLabelsService>(), koin.get<AppCoroutineScope>(),
            emptySet(), emptySet(), null, null, null, {}, { _, _ -> }, {}, {},
        )
        sessionClass.getDeclaredField("controller").apply { isAccessible = true }.set(session, controller)
        val pendingClass = Class.forName("net.bible.android.view.activity.nav.NavHostComposeActivity\$PendingLabelEdit")
        val pending = pendingClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(label, isNew)
        sessionClass.getDeclaredField("pendingEdit").apply { isAccessible = true }.set(session, pending)
        NavHostComposeActivity::class.java.getDeclaredField("manageLabelsSession")
            .apply { isAccessible = true }.set(host, "key" to session)
    }

    private fun pressOk(host: NavHostComposeActivity, edited: Label) {
        val data = LabelEditContract.LabelData(false, edited, false, false, false, false)
        NavHostComposeActivity::class.java.getDeclaredMethod("applyLabelEditResult", LabelEditResult::class.java)
            .apply { isAccessible = true }.invoke(host, LabelEditResult.Saved(data.toJSON()))
    }

    @Test fun `OK in Edit label persists an existing label before the manager is left`() {
        val original = Label(name = "D5 existing", color = 0xFF0000FF.toInt())
        runBlocking { dao.insert(original) }
        val host = Robolectric.buildActivity(NavHostComposeActivity::class.java).get()
        openSession(host, original, isNew = false)

        val newColor = 0xFFFF9800.toInt()
        pressOk(host, original.copy(color = newColor))
        // No saveManageLabelsAndExit here: the user is still on the label list ("force-stop" point).

        runBlocking {
            withTimeout(10_000) { while (dao.labelById(original.id)!!.color != newColor) delay(20) }
        }
        assertEquals(newColor, runBlocking { dao.labelById(original.id)!!.color })
    }

    @Test fun `OK on a new label stays staged until the manager is left`() {
        val fresh = Label(new = true, name = "D5 fresh")
        val host = Robolectric.buildActivity(NavHostComposeActivity::class.java).get()
        openSession(host, fresh, isNew = true)

        pressOk(host, fresh.copy(color = 0xFF4CAF50.toInt(), new = true))
        runBlocking { delay(500) }

        assertEquals(null, runBlocking { dao.labelById(fresh.id) })
    }
}
