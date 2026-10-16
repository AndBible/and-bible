package net.bible.android.view.activity.bookmark

import com.nhaarman.mockitokotlin2.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.*
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceOverrideOrderingTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() {
        Dispatchers.resetMain()
        DatabaseResetter.resetDatabase()
    }

    @Test fun heldSaveThenUpdateFinishesWithTheNewOverrideAndOrderedNotifications() = verifyOrder(delete = false)
    @Test fun heldSaveThenDeleteDoesNotResurrectTheOverride() = verifyOrder(delete = true)

    private fun verifyOrder(delete: Boolean) = runTest(dispatcher, timeout = 30.seconds) {
        val koin = GlobalContext.get()
        val originalScope = koin.get<AppCoroutineScope>()
        val originalLauncher = koin.get<OrderedLauncher>()
        val ownedJob = SupervisorJob(coroutineContext[Job])
        val scope = AppCoroutineScope(coroutineContext + ownedJob)
        val queue = OrderedLauncher(scope)
        val database = DatabaseContainer.instance
        val originalDb = database.workspaceDb
        val realDao = originalDb.workspaceDao()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val modesAtNotification = Collections.synchronizedList(mutableListOf<Int?>())
        val label = Label(name = "Override FIFO")
        val workspace = WorkspaceEntities.Workspace(name = "FIFO workspace")
        realDao.insertWorkspace(workspace)
        val old = WorkspaceEntities.WorkspaceLabelOverride(workspace.id, label.id, 0)
        val newer = old.copy(overrideMode = if (delete) null else 2)
        val heldDao = object : WorkspaceDao by realDao {
            override suspend fun insertOrUpdateLabelOverride(override: WorkspaceEntities.WorkspaceLabelOverride) {
                if (override.overrideMode == 0) { entered.complete(Unit); release.await() }
                realDao.insertOrUpdateLabelOverride(override)
            }
        }
        val heldDb = mock<WorkspaceDatabase>()
        whenever(heldDb.workspaceDao()).thenReturn(heldDao)
        val activity = Robolectric.buildActivity(NavHostComposeActivity::class.java)
        var subscription: net.bible.sharedcore.event.Subscription? = null
        try {
            koin.loadModules(listOf(module {
                single { scope }
                single { queue }
            }))
            val host = activity.get()
            val sessionClass = Class.forName("net.bible.android.view.activity.nav.NavHostComposeActivity\$ManageLabelsSession")
            val session = sessionClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(
                ManageLabelsContract.ManageLabelsData(ManageLabelsContract.Mode.WORKSPACE), mutableMapOf(label.id.toString() to label),
                ManageLabelsWrites(koin.get(), scope)
            )
            val service = koin.get<net.bible.sharedcore.bookmark.ManageLabelsService>()
            val controller = net.bible.sharedcore.bookmark.ManageLabelsController(
                net.bible.sharedcore.bookmark.ManageLabelsMode.WORKSPACE, service, scope,
                emptySet(), emptySet(), null, null, null, {}, { _, _ -> }, {}, {}
            )
            sessionClass.getDeclaredField("controller").apply { isAccessible = true }.set(session, controller)
            NavHostComposeActivity::class.java.getDeclaredField("manageLabelsSession").apply { isAccessible = true }.set(host, "fifo" to session)
            val pendingClass = Class.forName("net.bible.android.view.activity.nav.NavHostComposeActivity\$PendingLabelEdit")
            fun apply(value: WorkspaceEntities.WorkspaceLabelOverride) {
                val pending = pendingClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(label, true) // staged label: this test is about the override queue only
                sessionClass.getDeclaredField("pendingEdit").apply { isAccessible = true }.set(session, pending)
                val data = LabelEditContract.LabelData(false, label, false, false, false, false, workspaceOverride = value)
                NavHostComposeActivity::class.java.getDeclaredMethod("applyLabelEditResult", LabelEditResult::class.java)
                    .apply { isAccessible = true }.invoke(host, LabelEditResult.Saved(data.toJSON()))
            }
            subscription = koin.get<BookmarkControl>().changes.subscribe { change ->
                if (change is BookmarkChange.LabelUpserted && change.label.id == label.id) {
                    modesAtNotification += runBlocking { realDao.labelOverrides(workspace.id).singleOrNull()?.overrideMode }
                }
            }
            database.workspaceDb = heldDb
            apply(old)
            runCurrent()
            assertTrue("first actual DAO write must be held", entered.isCompleted)
            apply(newer)
            runCurrent()
            release.complete(Unit)
            ownedJob.children.toList().forEach { it.join() }
            assertEquals(newer.overrideMode, realDao.labelOverrides(workspace.id).singleOrNull()?.overrideMode)
            assertEquals(if (delete) 0 else 1, realDao.labelOverrides(workspace.id).size)
            assertEquals(listOf(0, newer.overrideMode), modesAtNotification)
        } finally {
            release.complete(Unit)
            try {
                ownedJob.cancelAndJoin()
                subscription?.cancel()
                activity.close()
            } finally {
                database.workspaceDb = originalDb
                koin.loadModules(listOf(module { single { originalScope }; single { originalLauncher } }))
            }
        }
    }
}
