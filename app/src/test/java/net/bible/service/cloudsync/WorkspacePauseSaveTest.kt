package net.bible.service.cloudsync

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.SyncNetworkState
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [35])
class WorkspacePauseSaveTest {
    private val hosts = mutableListOf<ActivityController<NavHostComposeActivity>>()
    @After fun tearDown() = net.bible.test.closeReadingHostsBeforeReset(hosts)

    @Test fun pausePersistsWorkspaceWhenSyncIsDisabled() {
        val settings = GlobalContext.get().get<net.bible.sharedcore.platform.AppSettings>()
        val previous = SyncableDatabaseDefinition.ALL.associateWith { it.isSyncEnabled(settings) }
        try {
            previous.keys.forEach { it.setSyncEnabled(settings, false) }
            assertPausePersists("sync-disabled")
        } finally { previous.forEach { (category, enabled) -> category.setSyncEnabled(settings, enabled) } }
    }

    @Test fun pausePersistsWorkspaceWhenOffline() {
        val state = GlobalContext.get().get<SyncNetworkState>()
        val previous = state.available
        try {
            state.available = false
            assertPausePersists("offline")
        } finally { state.available = previous }
    }

    @Test fun pausePersistsWorkspaceAfterRefusedServiceStart() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        var attempts = 0
        val refusingContext = object : android.content.ContextWrapper(app) {
            override fun startForegroundService(intent: android.content.Intent): android.content.ComponentName? {
                attempts++
                throw IllegalStateException("service start refused")
            }
        }
        val schedulerSnapshot = net.bible.test.KoinFactorySnapshot.of<net.bible.android.platform.AndroidSyncScheduler>()
        val driver = GlobalContext.get().get<SyncTriggerDriver>()
        runBlocking { driver.stopAndJoin() }
        val scheduler = net.bible.android.platform.AndroidSyncScheduler(refusingContext)
        try {
            org.koin.core.context.loadKoinModules(org.koin.dsl.module { single { scheduler } })
            assertPausePersists("refused") {
                driver.onBackground()
                assertEquals(1, attempts)
            }
        } finally {
            runBlocking { driver.stopAndJoin() }
            scheduler.complete(false)
            schedulerSnapshot.restore()
        }
    }

    private fun assertPausePersists(name: String, afterPause: () -> Unit = {}) {
        resetComposeUiDispatcher()
        firstTime = false
        val host = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { hosts += it }.apply { create().start().resume() }
        val repository = host.get().hostWindowRepository
        repository.name = name
        host.pause()
        afterPause()
        runBlocking {
            repository.persistence.awaitPersisted()
            assertEquals(name, DatabaseContainer.instance.workspaceDb.workspaceDao().workspace(repository.id)?.name)
        }
    }
}
