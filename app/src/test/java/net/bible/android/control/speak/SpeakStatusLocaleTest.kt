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
package net.bible.android.control.speak

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The in-app speak transport bar must re-resolve its cached status text when the in-app language changes. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SpeakStatusLocaleTest {
    private val app get() = ApplicationProvider.getApplicationContext<BibleApplication>()

    /**
     * The Application the process-wide Koin graph was started with. The :app suite runs in one JVM and Koin
     * outlives the per-test Application, so the service's strings resolve against this one, not [app].
     */
    private val koinApp get() = GlobalContext.get().get<Context>() as BibleApplication
    private val defaultLocale = Locale.getDefault()

    @After
    fun tearDown() {
        try {
            app.applyUiLocale("", refresh = false)
            koinApp.applyUiLocale("", refresh = false)
            Locale.setDefault(defaultLocale)
        } finally {
            SpeakTransportServiceImpl.instance = null
            DatabaseResetter.resetDatabase()
        }
    }

    @Test
    fun transportStatusTextFollowsTheInAppLanguageWithoutASpeakEvent() {
        val service = GlobalContext.get().get<SpeakTransportServiceImpl>()
        // The Koin single may predate this test (init ran earlier), so register it as the live instance explicitly.
        SpeakTransportServiceImpl.instance = service
        koinApp.applyUiLocale("en")
        assertEquals("- stopped -", service.state.value.statusText)

        koinApp.applyUiLocale("fi")
        assertEquals("- pysäytetty -", service.state.value.statusText)

        koinApp.applyUiLocale("en")
        assertEquals("- stopped -", service.state.value.statusText)
    }

    @Test
    fun languageChangeWithoutATransportServiceInstanceDoesNotThrow() {
        SpeakTransportServiceImpl.instance = null
        koinApp.applyUiLocale("fi")
    }
}
