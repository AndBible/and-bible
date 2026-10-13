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
            val appScope = koin?.getOrNull<net.bible.sharedcore.platform.AppCoroutineScope>()
            val appJob = appScope?.coroutineContext?.get(kotlinx.coroutines.Job)
            val jobs = (scopes_.mapNotNull { it.coroutineContext[kotlinx.coroutines.Job] } +
                (appJob?.children?.toList() ?: emptyList())).distinct()
            checkpoint("cancel-producers", "appContext=${appScope?.coroutineContext} explicitContexts=${scopes_.map { it.coroutineContext }} jobs=${jobs.joinToString { jobState(it) }}")
            jobs.forEach { it.cancel() }
            jobs.forEach {
                checkpoint("join-producer", jobState(it))
                it.join()
            }
            checkpoint("producers-joined")
            val settings = koin?.getOrNull<net.bible.service.cloudsync.documents.DocumentSyncSettings>()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (settings == null) commitReset { DatabaseContainer.reset() }
            else resetDocumentSyncFixture(settings, koin.get()) { DatabaseContainer.reset() }
        }
        DatabaseContainer.forgetReplacesForTest()
        //resetSingleton(DatabaseContainer::class.java, "instance")
    }

    /** Drive one Main task at a time; idle() can loop forever with a Compose popup open. */
    internal fun runResetBlocking(timeoutMs: Long = 30_000, block: suspend ResetDecision.() -> Unit) {
        val decision = ResetDecision()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val cleanup = scope.async(decision) { decision.block() }
        val main = android.os.Looper.getMainLooper()
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        try {
            while (!cleanup.isCompleted) {
                if (System.nanoTime() >= deadline && decision.abortBeforeCommit()) {
                    cleanup.cancel()
                    throw IllegalStateException("Database fixture cleanup timed out before reset (${timeoutMs}ms); ${decision.diagnostic()}")
                }
                if (android.os.Looper.myLooper() === main) {
                    org.robolectric.Shadows.shadowOf(main).runOneTask()
                }
                Thread.sleep(1)
            }
            // Already complete: await only propagates an error; it cannot block Main.
            kotlinx.coroutines.runBlocking { cleanup.await() }
        } catch (failure: Throwable) {
            if (decision.abortBeforeCommit()) {
                cleanup.cancel()
            } else {
                // A callback/interrupt cannot abandon an irreversible reset. Keep Main available
                // until it finishes, but report the original pump failure rather than mask it.
                var interrupted = failure is InterruptedException
                while (!cleanup.isCompleted) {
                    try {
                        if (android.os.Looper.myLooper() === main) {
                            org.robolectric.Shadows.shadowOf(main).runOneTask()
                        }
                        Thread.sleep(1)
                    } catch (secondary: Throwable) {
                        if (secondary is InterruptedException) interrupted = true
                        if (secondary !== failure) failure.addSuppressed(secondary)
                    }
                }
                try { kotlinx.coroutines.runBlocking { cleanup.await() } }
                catch (secondary: Throwable) { if (secondary !== failure) failure.addSuppressed(secondary) }
                if (interrupted) Thread.currentThread().interrupt()
            }
            throw failure
        } finally { scope.cancel() }
    }

    /** Deadline is abortable only before the irreversible DB reset starts. */
    internal class ResetDecision : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
        companion object Key : kotlin.coroutines.CoroutineContext.Key<ResetDecision>
        private enum class Phase { PREPARING, COMMITTED, ABORTED }
        private var phase = Phase.PREPARING
        private var stage = "starting"
        private var details = ""

        @Synchronized fun checkpoint(stage: String, details: String) {
            this.stage = stage
            this.details = details
            println("DB_RESET stage=$stage $details")
        }

        @Synchronized fun diagnostic() = "stage=$stage $details"

        @Synchronized fun abortBeforeCommit(): Boolean {
            if (phase == Phase.COMMITTED) return false
            phase = Phase.ABORTED
            return true
        }

        private fun beginCommit() = synchronized(this) {
            check(phase == Phase.PREPARING) { "Database fixture reset cannot commit after timeout or twice" }
            phase = Phase.COMMITTED
        }

        suspend fun commitReset(reset: suspend () -> Unit) {
            beginCommit()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { reset() }
        }
    }

    internal suspend fun resetDocumentSyncFixture(
        settings: net.bible.service.cloudsync.documents.DocumentSyncSettings,
        launcher: net.bible.sharedcore.platform.OrderedLauncher,
        reset: suspend () -> Unit,
    ) {
        val capturedLauncher = settings.javaClass.getDeclaredField("launcher").apply { isAccessible = true }.get(settings)
        val tailsField = launcher.javaClass.getDeclaredField("tails").apply { isAccessible = true }
        val lockField = launcher.javaClass.getDeclaredField("lock").apply { isAccessible = true }
        val tail = synchronized(requireNotNull(lockField.get(launcher))) {
            (tailsField.get(launcher) as Map<*, *>)["document-sync-settings"] as kotlinx.coroutines.Job?
        }
        val mutexField = settings.javaClass.getDeclaredField("dbMutex").apply { isAccessible = true }
        val mutex = mutexField.get(settings) as kotlinx.coroutines.sync.Mutex
        val queueScope = launcher.javaClass.getDeclaredField("scope").apply { isAccessible = true }.get(launcher) as CoroutineScope
        checkpoint("settings-tail", "queue=${System.identityHashCode(launcher)} capturedQueue=${System.identityHashCode(capturedLauncher)} queueContext=${queueScope.coroutineContext} tail=${tail?.let { jobState(it) }} mutexLocked=${mutex.isLocked}")
        launcher.join("document-sync-settings")
        checkpoint("settings-mutex", "mutexLocked=${mutex.isLocked}")
        mutex.lock()
        checkpoint("settings-reset")
        try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val commit: suspend () -> Unit = {
                reset()
                val field = settings.javaClass.getDeclaredField("state").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val state = field.get(settings) as kotlinx.coroutines.flow.MutableStateFlow<net.bible.android.database.DocumentSyncPreferences?>
                state.value = null
            }
            val decision = kotlinx.coroutines.currentCoroutineContext()[ResetDecision]
            if (decision == null) commit() else decision.commitReset(commit)
        } finally { mutex.unlock() }
    }

    private fun jobState(job: kotlinx.coroutines.Job): String =
        "$job identity=${System.identityHashCode(job)} active=${job.isActive} completed=${job.isCompleted} cancelled=${job.isCancelled} context=${(job as? CoroutineScope)?.coroutineContext} children=${job.children.toList()}"

    private suspend fun checkpoint(stage: String, details: String = "") {
        kotlinx.coroutines.currentCoroutineContext()[ResetDecision]?.checkpoint(stage, details)
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
