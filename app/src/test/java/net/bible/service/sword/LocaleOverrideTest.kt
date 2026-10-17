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

package net.bible.service.sword

import net.bible.android.MyLocaleProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `resolveRef` retries in the document language through [MyLocaleProvider]'s override and always resets it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LocaleOverrideTest {
    private val v11n get() = Versifications.instance().getVersification("KJV")!!

    @Test fun aReferenceInANonUiLanguageResolvesAndTheOverrideIsClearedAfterwards() {
        assertNull(MyLocaleProvider.override)
        val key = SwordContentFacade.resolveRef("Johannes 3:16", "de", v11n)
        assertNotNull("German book name resolves through the override", key)
        assertEquals("John.3.16", key!!.getOsisID())
        assertNull(MyLocaleProvider.override)
    }

    @Test fun anUnresolvableReferenceAlsoLeavesTheOverrideCleared() {
        val key = SwordContentFacade.resolveRef("Zzzz 99:99", "de", v11n)
        assertNull(key)
        assertNull(MyLocaleProvider.override)
    }
}
