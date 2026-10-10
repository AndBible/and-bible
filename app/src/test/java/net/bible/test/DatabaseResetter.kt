/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import java.lang.IllegalStateException
import java.lang.reflect.Field

/**
 * Reset db between tests @see https://github.com/robolectric/robolectric/issues/1890
 *
 * Created by mjden on 31/08/2017.
 */
object DatabaseResetter {
    @JvmStatic
    fun resetDatabase(vararg scopes_: CoroutineScope) {
        val scopes = ArrayList<CoroutineScope>()
        scopes.addAll(scopes_)
        scopes.add(GlobalScope)
        for(scope in scopes) {
            try {
                scope.cancel("Time to stop! Test already ended...")
            } catch (e: IllegalStateException) {
                if (e.message?.startsWith("Scope cannot be cancelled because") == false) {
                    throw e
                }
            }
        }
        //val looper = Shadows.shadowOf(Looper.getMainLooper())
        //looper.idle()
        //val stacks = Thread.getAllStackTraces()

        // Something is hanging there still due to kotlin coroutines. This seem to help.
        // Sorry, not motivated at this time to investigate this any further if this workaround works.

        Thread.sleep(300)
        val koin = org.koin.core.context.GlobalContext.getOrNull()
        runResetBlocking {
            // Cancel producers, then wait for their finally blocks and late submissions before draining.
            val appJob = koin?.getOrNull<net.bible.sharedcore.platform.AppCoroutineScope>()
                ?.coroutineContext?.get(kotlinx.coroutines.Job)
            val jobs = (scopes_.mapNotNull { it.coroutineContext[kotlinx.coroutines.Job] } +
                (appJob?.children?.toList() ?: emptyList())).distinct()
            jobs.forEach { it.cancel() }
            jobs.forEach { it.join() }
            val settings = koin?.getOrNull<net.bible.service.cloudsync.documents.DocumentSyncSettings>()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (settings == null) DatabaseContainer.reset()
            else resetDocumentSyncFixture(settings, koin.get()) { DatabaseContainer.reset() }
        }
        DatabaseContainer.forgetReplacesForTest()
        //resetSingleton(DatabaseContainer::class.java, "instance")
    }

    /** Drive one Main task at a time; idle() can loop forever with a Compose popup open. */
    internal fun runResetBlocking(timeoutMs: Long = 30_000, block: suspend () -> Unit) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val cleanup = scope.async { block() }
        val main = android.os.Looper.getMainLooper()
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        try {
            while (!cleanup.isCompleted) {
                if (System.nanoTime() >= deadline) {
                    cleanup.cancel()
                    throw IllegalStateException("Database fixture cleanup timed out before reset (${timeoutMs}ms)")
                }
                if (android.os.Looper.myLooper() === main) {
                    org.robolectric.Shadows.shadowOf(main).runOneTask()
                }
                Thread.sleep(1)
            }
            // Already complete: await only propagates an error; it cannot block Main.
            kotlinx.coroutines.runBlocking { cleanup.await() }
        } finally { scope.cancel() }
    }

    internal suspend fun resetDocumentSyncFixture(
        settings: net.bible.service.cloudsync.documents.DocumentSyncSettings,
        launcher: net.bible.sharedcore.platform.OrderedLauncher,
        reset: suspend () -> Unit,
    ) {
        launcher.join("document-sync-settings")
        val mutexField = settings.javaClass.getDeclaredField("dbMutex").apply { isAccessible = true }
        val mutex = mutexField.get(settings) as kotlinx.coroutines.sync.Mutex
        mutex.lock()
        try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            reset()
            val field = settings.javaClass.getDeclaredField("state").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(settings) as kotlinx.coroutines.flow.MutableStateFlow<net.bible.android.database.DocumentSyncPreferences?>
            state.value = null
        } finally { mutex.unlock() }
    }

    private fun resetSingleton(class_: Class<*>, fieldName: String) {
        val instance: Field
        try {
            instance = class_.getDeclaredField(fieldName)
            instance.isAccessible = true
            instance[null] = null
        } catch (e: Exception) {
            throw RuntimeException()
        }
    }
}
