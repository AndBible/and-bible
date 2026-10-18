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

package net.bible.sharedui.ios.app

import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The iOS start route: READING only with a usable (unlocked) Bible, else WELCOME through the shared gate. The
 * Bibles come from the resolver's seam; the real Book is the module `IosAssemblyTest` installs (K4RawText), and
 * the locked one is a delegating stand-in.
 */
class IosStartRouteTest {
    private fun fixtureBible(): Book {
        // Same process-wide sandbox as IosAssemblyTest (JSword's book list is global); start() is idempotent.
        IosAppStartup.start(
            IosAppConfig(
                root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
            ),
        )
        return assertNotNull(Books.installed().getBook("K4RawText"), "fixture Bible K4RawText is not installed")
    }

    private fun locked(real: Book): Book = object : Book by real {
        override val isLocked: Boolean get() = true
    }

    @Test
    fun noBibleStartsAtWelcome() {
        // A workspace may exist whose Bible was deleted: nothing may look up a default Bible here.
        assertEquals(NavRoutes.WELCOME, runBlocking { resolveIosStartRoute { emptyList() } })
    }

    @Test
    fun usableBibleStartsAtReading() {
        val bible = fixtureBible()
        assertEquals(NavRoutes.READING, runBlocking { resolveIosStartRoute { listOf(bible) } })
    }

    @Test
    fun lockedOnlyBibleStartsAtWelcome() {
        val bible = locked(fixtureBible())
        assertEquals(NavRoutes.WELCOME, runBlocking { resolveIosStartRoute { listOf(bible) } })
    }
}
