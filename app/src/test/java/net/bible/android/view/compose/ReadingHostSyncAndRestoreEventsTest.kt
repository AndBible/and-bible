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

import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages
import net.bible.android.control.page.window.WindowSync
import net.bible.android.database.IdType
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.base.AppPosition
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.SystemBarSettingChanges
import net.bible.service.device.ScreenSettings
import net.bible.android.view.activity.page.ReadingAppBootstrap
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.event.Subscription
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Slice 8 final review, Important 1: **the `MainBibleActivity` event subscriptions the reading host
 * lost when F4 deleted classic.**
 *
 * Classic `MainBibleActivity.eventSubscriptions` (at `7ac0b64fa`) handled five events the nav host's
 * `subscribeReadingHost` did not: `MainBibleAfterRestore` (now `DatabaseContainer.databaseRestored`, notified by `BackupControl` and
 * `SyncSettingsServiceImpl` after a database restore), `AppToBackgroundEvent` (now `CurrentActivityHolder.appPositionChanges`: the cloud-sync
 * background/foreground pair, and the night-mode refresh `onRestart` owes a return from background),
 * `WorkspacesUpdatedViaSyncEvent` (now `DatabaseContainer.workspacesSynced`), and `WorkspaceRefreshRequired` (now `CloudSync.workspaceRefreshRequired`). (The cloud-sync write of
 * `globalLastSynchronized`, which `ReadingAppBootstrap.synchronize` reads, moved to `SyncService`; see `SyncFinishedTest`.) With the only reading
 * host not listening, a restored backup was overwritten by the live workspace's next save and cloud
 * sync never ran on background/foreground.
 *
 * Each event is posted at a composed reading host and its classic effect asserted. The workspace
 * reloads are anchored on the `UserMessages` toast `ReadingCommands.currentWorkspaceId`'s setter posts, the
 * one loud synchronous effect of a workspace switch (as `ReadingHostResumeReconciliationTest` does).
 *
 * The second half is the gate: a host that has NOT run the reading bootstrap (here one started on
 * a light non-reading route) owns no window repository, so every handler would reach `hostWindowRepository` and
 * throw — an exception `EventSource` catches and prints. Those tests assert both that nothing
 * observable happened and that no handler threw.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostSyncAndRestoreEventsTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private var toasts = 0
    private var toastSubscription: Subscription? = null

    @After
    fun tearDown() {
        toastSubscription?.cancel()
        ABEventBus.unregister(this)
        controllers.forEach { runCatching { it.get().readingAppBootstrap.stopPeriodicSync() } }
        controllers.forEach { it.close() }
        controllers.clear()
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = false
        CommonUtils.realSharedPreferences.edit().remove("night_mode_pref").remove("night_mode_pref3").commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun host(route: String): ActivityController<NavHostComposeActivity> {
        resetComposeUiDispatcher()
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).also { controllers += it }.apply { create().start().resume().visible() }
    }

    private fun readingHost() = host(NavRoutes.READING)

    /**
     * A host with no reading bootstrap, never navigated to reading. A light destination on purpose: the
     * real Download destination starts a repository refresh that never finishes without a network.
     */
    private fun nonReadingHost() = host(NavRoutes.AI_TOOL_INFO)

    private fun countToasts() {
        toasts = 0
        toastSubscription = UserMessages.messages.subscribe { if (it is UserMessage.Toast) toasts++ }
    }

    /**
     * Bounded, never `idle()`: with the Compose dispatcher reset the composed reading host keeps
     * scheduling frames, and an unbounded idle spins on them forever.
     */
    private fun idleMain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

    private var lastSynchronized: Long
        get() = CommonUtils.settings.getLong("globalLastSynchronized", 0L)
        set(value) = CommonUtils.settings.setLong("globalLastSynchronized", value)

    private val syncJobField = ReadingAppBootstrap::class.java.getDeclaredField("syncJob").apply { isAccessible = true }
    private fun syncJobOf(a: NavHostComposeActivity): Job? = syncJobField.get(a.readingAppBootstrap) as Job?

    private val lastForceSyncAllField = WindowSync::class.java.getDeclaredField("lastForceSyncAll").apply { isAccessible = true }

    /** Runs [notify] and returns what `EventSource` printed for a handler that threw. */
    private fun notifyCapturingErrors(notify: () -> Unit): String {
        val captured = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(captured, true))
        try {
            notify()
            idleMain()
        } finally {
            System.setErr(original)
        }
        return captured.toString()
    }

    private fun logEntry(table: String, id: IdType, type: LogEntryTypes = LogEntryTypes.UPSERT) =
        LogEntry(table, id, IdType.empty(), type, 0L, "other-device")

    // ——— DatabaseContainer.databaseRestored ———————————————————————————————————————————————————————————————————

    /**
     * Classic: `bookmarkControl.reset(); bibleViewFactory.clear(); windowSync.setResyncRequired();
     * currentWorkspaceId = IdType.empty()`. The last step is the one that matters: it reloads the
     * workspace from the RESTORED database instead of letting the live repository's next save
     * overwrite it.
     */
    @Test
    fun aRestoreReloadsTheWorkspaceFromTheRestoredDatabase() {
        val activity = readingHost().get()
        val windowSync = CommonUtils.windowControl.windowSync
        lastForceSyncAllField.setLong(windowSync, 0L)
        countToasts()

        DatabaseContainer.notifyDatabaseRestored()
        idleMain()

        assertTrue(toasts > 0, "databaseRestored must reload the workspace (its setter posts a toast)")
        assertNotEquals(0L, lastForceSyncAllField.getLong(windowSync), "…and flag every window for a forced resync")
        assertNotNull(activity.hostWindowRepository.activeWindow, "sanity: the reloaded workspace has an active window")
    }

    // ——— CloudSync.workspaceRefreshRequired / DatabaseContainer.workspacesSynced ———————————————————————————————

    @Test
    fun aWorkspaceRefreshRequiredReloadsTheFirstWorkspace() {
        readingHost()
        countToasts()
        CloudSync.notifyWorkspaceRefreshRequired()
        idleMain()
        assertTrue(toasts > 0, "workspaceRefreshRequired must switch to the first workspace, as classic's did")
    }

    @Test
    fun aSyncUpsertOfTheCurrentWorkspaceReloadsIt() {
        val activity = readingHost().get()
        countToasts()
        DatabaseContainer.emitWorkspacesSyncedForTest(listOf(logEntry("Workspace", activity.hostWindowRepository.id)))
        idleMain()
        assertEquals(1, toasts, "a synced change to the current workspace must reload it")
    }

    @Test
    fun aSyncChangeToAWindowOfTheCurrentWorkspaceReloadsIt() {
        val activity = readingHost().get()
        countToasts()
        val windowId = activity.hostWindowRepository.windowList.first().id
        DatabaseContainer.emitWorkspacesSyncedForTest(listOf(logEntry("Window", windowId)))
        idleMain()
        assertEquals(1, toasts, "a synced change to one of the current workspace's windows must reload it")
    }

    @Test
    fun aSyncDeleteOfTheCurrentWorkspaceSwitchesToTheFirstOne() {
        val activity = readingHost().get()
        countToasts()
        DatabaseContainer.emitWorkspacesSyncedForTest(listOf(logEntry("Workspace", activity.hostWindowRepository.id, LogEntryTypes.DELETE)))
        idleMain()
        assertEquals(1, toasts, "deleting the current workspace elsewhere must switch this host to the first workspace")
    }

    @Test
    fun anUnrelatedSyncChangeReloadsNothing() {
        readingHost()
        countToasts()
        DatabaseContainer.emitWorkspacesSyncedForTest(listOf(logEntry("Workspace", IdType()), logEntry("Window", IdType())))
        idleMain()
        assertEquals(0, toasts, "a synced change to some other workspace must not reload this one")
    }

    // ——— CurrentActivityHolder.appPositionChanges ————————————————————————————————————————————————————————————————————

    @Test
    fun goingToBackgroundStopsThePeriodicSync() {
        val activity = readingHost().get()
        val seeded = Job()
        syncJobField.set(activity.readingAppBootstrap, seeded)

        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)

        assertTrue(seeded.isCancelled, "going to background must cancel the periodic sync job")
        assertNull(syncJobOf(activity), "…and forget it, so the return to foreground can start a new one")
    }

    @Test
    fun returningToForegroundStartsSync() {
        val activity = readingHost().get()
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = true
        assertNull(syncJobOf(activity), "sanity: no periodic sync before the event")

        CurrentActivityHolder.notifyAppPosition(AppPosition.FOREGROUND)

        // startSync runs on syncScope (Dispatchers.IO); bounded wait for its synchronous prefix.
        val deadline = System.currentTimeMillis() + 5_000
        while (syncJobOf(activity) == null && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertNotNull(syncJobOf(activity), "returning to foreground must start the cloud-sync loop")
    }

    /**
     * Classic `onRestart`: a return from a whole-app background re-applies the theme
     * (`refreshIfNightModeChange` -> `applyTheme`). The night-mode preference is flipped while the host is
     * stopped WITHOUT firing `ScreenSettings.nightModeChanges`, so only the restart's refresh can carry it into
     * `AppCompatDelegate`'s default mode. (A sentinel written straight into `setDefaultNightMode` cannot
     * serve: AppCompat recreates live Activities on that call, and `onCreate` applies the theme itself.)
     *
     * No "ordinary restart does nothing" twin: with a single Activity, stopping it IS the whole app going
     * to background (`CurrentActivityHolder` fires the real `appPositionChanges` BACKGROUND), so Robolectric cannot
     * stage a restart that is not one. The test is instead proven by mutation (see the commit message).
     */
    @Test
    fun aRestartAfterTheAppWasInBackgroundRefreshesTheNightMode() {
        val controller = restartFixture()
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        controller.pause().stop()
        CommonUtils.realSharedPreferences.edit().putBoolean("night_mode_pref", true).commit()
        controller.restart().start().resume()
        idleMain()
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode(),
            "a restart after the whole app was in background must re-apply the theme (refreshIfNightModeChange)",
        )
    }

    private fun restartFixture(): ActivityController<NavHostComposeActivity> {
        CommonUtils.realSharedPreferences.edit().putString("night_mode_pref3", "manual").putBoolean("night_mode_pref", false).commit()
        val controller = readingHost()
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode(), "sanity: onCreate applied day mode")
        return controller
    }

    // ——— The gate: a host without a reading bootstrap ignores all of them ——————————————————————

    @Test
    fun aHostWithoutAReadingBootstrapIgnoresTheReadingEvents() {
        val activity = nonReadingHost().get()
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = true
        lastSynchronized = 0L
        val windowSync = CommonUtils.windowControl.windowSync
        lastForceSyncAllField.setLong(windowSync, 0L)
        countToasts()

        val errors = listOf<() -> Unit>(
            { DatabaseContainer.notifyDatabaseRestored() },
            { CloudSync.notifyWorkspaceRefreshRequired() },
            { DatabaseContainer.emitWorkspacesSyncedForTest(listOf(logEntry("Workspace", IdType()))) },
            { CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND) },
            { CurrentActivityHolder.notifyAppPosition(AppPosition.FOREGROUND) },
        ).joinToString("") { notifyCapturingErrors(it) }
        Thread.sleep(200) // let anything syncScope was handed run
        idleMain()

        assertFalse(
            errors.contains("Exception"),
            "a host that owns no window repository must not run the reading handlers at all; one threw:\n$errors",
        )
        assertEquals(0, toasts, "no workspace switch on a host without a reading bootstrap")
        assertEquals(0L, lastSynchronized, "no globalLastSynchronized write on a host without a reading bootstrap")
        assertEquals(0L, lastForceSyncAllField.getLong(windowSync), "no forced resync on a host without a reading bootstrap")
        assertNull(syncJobOf(activity), "no cloud-sync loop on a host without a reading bootstrap")
    }

    /** Review Focus 1: onDestroy must cancel every host subscription. */
    @Test
    fun aDestroyedHostReactsToNothing() {
        val controller = readingHost()
        val activity = controller.get()
        idleMain()
        SyncableDatabaseDefinition.BOOKMARKS.syncEnabled = true
        controller.pause().stop().destroy()
        controllers.remove(controller)
        lastSynchronized = 0L
        countToasts()

        val errors = listOf<() -> Unit>(
            { DatabaseContainer.notifyDatabaseRestored() },
            { CloudSync.notifyWorkspaceRefreshRequired() },
            { DatabaseContainer.emitWorkspacesSyncedForTest(listOf(logEntry("Workspace", IdType()))) },
            { CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND) },
            { CurrentActivityHolder.notifyAppPosition(AppPosition.FOREGROUND) },
            { ScreenSettings.notifyNightModeChanged() },
            { SystemBarSettingChanges.notifyChanged() },
        ).joinToString("") { notifyCapturingErrors(it) }
        Thread.sleep(200)
        idleMain()

        assertFalse(errors.contains("Exception"), "a destroyed host must not run its handlers:\n$errors")
        assertEquals(0, toasts)
        assertEquals(0L, lastSynchronized)
        assertNull(syncJobOf(activity))
    }
}
