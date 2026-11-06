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

package net.bible.service.sword.epub

import net.bible.android.BibleApplication.Companion.application
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * What an EPUB test fixture opened, retired before its files go (#3947).
 *
 * Deleting an `epub-*.sqlite3` under a live [EpubBackendState] lets a producer that outlived its test write
 * through a connection to an unlinked file (SQLite 1032, `SQLITE_READONLY_DBMOVED`), or reopen and recreate
 * the database empty for the next test that builds an EPUB of the same name. [cleanUp] therefore closes every
 * tracked state (which waits for its leases), then retires each module under [epubRoot] through the production
 * path ([deleteEpubModule]: unregister from the JVM-wide `Books`, close, delete the database and files).
 */
internal class EpubFixtureOwnership(private val retireTimeoutMs: Long = TimeUnit.SECONDS.toMillis(60)) {
    private val states = mutableListOf<EpubBackendState>()

    fun <T : EpubBackendState> track(state: T): T = state.also { synchronized(states) { states += it } }

    /**
     * Retires and deletes everything the fixture owns, then [deleteDatabases] leftovers.
     * Fails, without deleting anything still leased, when a lease outlives [retireTimeoutMs].
     */
    fun cleanUp(epubRoot: File, dbFilenames: Collection<String>) {
        val owned = synchronized(states) { states.toList().also { states.clear() } }
        var failure: Throwable? = null
        // Retirement waits for leases without a bound; a bounded join turns a leaked lease into a red, not a hang.
        val worker = thread(name = "epub-fixture-retire", isDaemon = true) {
            try {
                owned.forEach { it.close() }
                epubRoot.listFiles()?.filter { it.isDirectory }?.forEach { deleteEpubModule(it.toOkioPath()) }
                epubRoot.deleteRecursively()
                dbFilenames.forEach { application.deleteDatabase(it) }
            } catch (e: Throwable) {
                failure = e
            }
        }
        worker.join(retireTimeoutMs)
        if (worker.isAlive) {
            throw AssertionError("HARNESS FAILURE: an EPUB state is still leased after $retireTimeoutMs ms; " +
                "its files stay until the lease is released. Retirer stack:\n" +
                worker.stackTrace.joinToString("\n") { "\tat $it" })
        }
        failure?.let { throw it }
    }
}
