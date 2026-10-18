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
import org.junit.Assert.assertNull
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import net.bible.sharedui.app.bookmark.BookmarkHost
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.strings.AndroidStrings

/**
 * SU-14 D5: pressing OK in "Edit label" must write an existing label to Room right then, not only
 * when the label manager is left (a process kill between the two lost the edit).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class LabelEditPersistenceTest {
    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private val bookmarkHosts = java.util.WeakHashMap<NavHostComposeActivity, BookmarkHost>()

    /**
     * The bookmark host of an Activity that was only constructed (never `create()`d, so it has no `appHosts`):
     * built from the same Koin, ports and strings the Activity would give it, once per Activity.
     */
    private fun bookmarkOf(host: NavHostComposeActivity): BookmarkHost = bookmarkHosts.getOrPut(host) {
        AppHosts(GlobalContext.get(), host, host.hostPorts, AndroidStrings(host)).bookmark
    }

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    /** Opens the manager's session (WORKSPACE mode) on [host] and returns it, seeded with [label]. */
    private fun openSession(host: NavHostComposeActivity, label: Label, isNew: Boolean) {
        val koin = GlobalContext.get()
        val sessionClass = Class.forName("net.bible.sharedui.app.bookmark.ManageLabelsSession")
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
        val pendingClass = Class.forName("net.bible.sharedui.app.bookmark.PendingLabelEdit")
        val pending = pendingClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(label, isNew)
        sessionClass.getDeclaredField("pendingEdit").apply { isAccessible = true }.set(session, pending)
        BookmarkHost::class.java.getDeclaredField("manageLabelsSession")
            .apply { isAccessible = true }.set(bookmarkOf(host), "key" to session)
    }

    private fun pressOk(host: NavHostComposeActivity, edited: Label) {
        val data = LabelEditContract.LabelData(false, edited, false, false, false, false)
        BookmarkHost::class.java.getDeclaredMethod("applyLabelEditResult", LabelEditResult::class.java)
            .apply { isAccessible = true }.invoke(bookmarkOf(host), LabelEditResult.Saved(data.toJSON()))
    }

    /** Deterministic barrier: the early writes run FIFO on the "workspace" key, so this returns after them. */
    private fun awaitQueuedWrites() = runBlocking {
        withTimeout(10_000) { GlobalContext.get().get<OrderedLauncher>().launch("workspace") {}.join() }
    }

    private fun newHost() = Robolectric.buildActivity(NavHostComposeActivity::class.java)

    @Test fun `OK in Edit label persists an existing label before the manager is left`() {
        val original = Label(name = "D5 existing", color = 0xFF0000FF.toInt())
        runBlocking { dao.insert(original) }
        val activity = newHost()
        try {
            val host = activity.get()
            openSession(host, original, isNew = false)

            val newColor = 0xFFFF9800.toInt()
            pressOk(host, original.copy(color = newColor))
            // No saveManageLabelsAndExit: the user is still on the label list (the "force-stop" point).

            awaitQueuedWrites()
            assertEquals(newColor, runBlocking { dao.labelById(original.id)!!.color })
        } finally {
            activity.close()
        }
    }

    @Test fun `a new label stays staged across two OKs and the exit save inserts it once`() {
        val fresh = Label(new = true, name = "D5 fresh")
        val activity = newHost()
        try {
            val host = activity.get()
            openSession(host, fresh, isNew = true)

            pressOk(host, fresh.copy(color = 0xFF4CAF50.toInt(), new = true))
            awaitQueuedWrites()
            assertNull("first OK must not write a new label", runBlocking { dao.labelById(fresh.id) })

            // Re-edit the same still-new label (the manager's list now shows it as a normal row).
            val pendingClass = Class.forName("net.bible.sharedui.app.bookmark.PendingLabelEdit")
            val sessionField = BookmarkHost::class.java.getDeclaredField("manageLabelsSession").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST") val session = (sessionField.get(bookmarkOf(host)) as Pair<String, Any>).second
            val staged = (session.javaClass.getDeclaredField("labelsById").apply { isAccessible = true }
                .get(session) as Map<String, Label>)[fresh.id.toString()]!!
            session.javaClass.getDeclaredField("pendingEdit").apply { isAccessible = true }
                .set(session, pendingClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(staged, false))
            pressOk(host, staged.copy(color = 0xFF9C27B0.toInt(), new = true))
            awaitQueuedWrites()
            assertNull("second OK must not write a still-new label", runBlocking { dao.labelById(fresh.id) })

            // The exit save inserts it exactly once.
            BookmarkHost::class.java.getDeclaredMethod(
                "saveManageLabelsAndExit", session.javaClass, kotlin.jvm.functions.Function1::class.java,
            ).apply { isAccessible = true }.invoke(bookmarkOf(host), session, { _: Any -> })
            runBlocking {
                withTimeout(10_000) { while (dao.labelById(fresh.id) == null) delay(20) }
            }
            assertEquals(1, runBlocking { dao.allLabelsSortedByName() }.count { it.name == "D5 fresh" })
            assertEquals(0xFF9C27B0.toInt(), runBlocking { dao.labelById(fresh.id)!!.color })
        } finally {
            activity.close()
        }
    }
}
