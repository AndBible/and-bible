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

package net.bible.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.DocumentSyncPreferences
import net.bible.android.database.DocumentSyncPreferencesDao
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A reset stuck at stage `settings-mutex` (49 suite reds, twice on 2026-10-23, never reproduced alone) must name the
 * holder of [DocumentSyncSettings]' database lock instead of only reporting that it is locked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncSettingsLockOwnerTest {
    @Test
    fun aLoadThatNeverFinishesNamesItselfAsTheLockHolder() = runBlocking {
        val readStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dao = object : DocumentSyncPreferencesDao {
            override suspend fun get(): DocumentSyncPreferences? { readStarted.complete(Unit); release.await(); return null }
            override suspend fun set(prefs: DocumentSyncPreferences) = Unit
            override suspend fun clear() = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settings = DocumentSyncSettings(
            { dao }, { error("unused") }, { error("unused") }, OrderedLauncher(scope),
            KoinJavaComponent.get(AppEnvironment::class.java),
        )
        val load = scope.launch { settings.ensureLoaded() }
        try {
            assertTrue("the load never reached its read", withTimeoutOrNull(10_000) { readStarted.await() } != null)
            val owner = DatabaseResetter.lockOwner(settings)
            assertTrue("the held lock names no owner: $owner", owner?.contains("DocumentSyncSettings.ensureLoaded") == true)
        } finally {
            release.complete(Unit)
            load.join()
            scope.cancel()
        }
        assertTrue("a free lock reports no owner", DatabaseResetter.lockOwner(settings) == null)
    }
}
