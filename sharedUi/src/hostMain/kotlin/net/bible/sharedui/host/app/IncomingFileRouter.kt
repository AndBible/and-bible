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

package net.bible.sharedui.host.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.DocumentRef

/**
 * Routes files handed to the app from outside: a database backup goes to the restore flow (the one the Welcome
 * screen's "Restore Database File" uses), everything else (module archives and the rest) to InstallZip.
 *
 * One batch is handled once: its backups are restored one after another, then the remaining files go to [install] in
 * a single call, and [route]'s `complete` runs exactly once, after the last restore result or, when files remain,
 * when InstallZip reports its result. A failed classification counts as "not a backup", so the file still reaches
 * InstallZip, which reports what is wrong with it.
 */
internal class IncomingFileRouter(
    private val scope: CoroutineScope,
    private val isBackup: suspend (DocumentRef) -> Boolean,
    private val restore: suspend (DocumentRef) -> Unit,
    private val install: (List<DocumentRef>, () -> Unit) -> Unit,
) {
    fun route(refs: List<DocumentRef>, complete: () -> Unit) {
        scope.launch {
            val backups = ArrayList<DocumentRef>()
            val others = ArrayList<DocumentRef>()
            for (ref in refs) {
                val backup = try { isBackup(ref) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                catch (e: Exception) { Log.w(TAG, "Could not classify ${ref.uri}: ${e.message}"); false }
                (if (backup) backups else others).add(ref)
            }
            for (ref in backups) {
                try { restore(ref) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                catch (e: Exception) { Log.e(TAG, "Restore of ${ref.uri} failed", e) }
            }
            if (others.isEmpty()) complete() else install(others, complete)
        }
    }

    private companion object { const val TAG = "IncomingFileRouter" }
}

/** Only [NavRoutes.WELCOME] is stale after a restore: elsewhere nothing waits for it ([afterWelcomeRestore] is Welcome's `afterRestore`). */
internal fun afterIncomingRestore(currentRoute: String?, afterWelcomeRestore: () -> Unit) {
    if (currentRoute?.substringBefore('?') == NavRoutes.WELCOME) afterWelcomeRestore()
}
