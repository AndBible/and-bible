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
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * F92 (fix batch 1, spec §2.10): switching document in a window records the OLD document's position
 * BEFORE the switch (`CurrentPageManager.setCurrentDocument` calls `HistoryManager.recordIfCreated` first), so Back
 * walks KJV Gen 2:4 -> KJV Gen 1:1 -> FinRK Gen 1:1. That is the intended classic behaviour a review
 * misread as a bug; this test pins it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSwitchHistoryPinTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private var history: HistoryManager? = null

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        history = null
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun verse(osis: String) =
        VerseFactory.fromString(Versifications.instance().getVersification("KJV"), osis)

    @Test
    fun backAfterADocumentSwitchWalksKjvGen24ThenKjvGen11ThenFinRkGen11() {
        // Compose's JVM-static UI dispatcher can be left stuck by an earlier host test in the same JVM.
        resetComposeUiDispatcher()
        firstTime = false
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().get()
        idle()
        val kjv = assertNotNull(SwordDocumentFacade.getDocumentByInitials("KJV"), "KJV test module missing (~/.sword)")
        val finRk = assertNotNull(SwordDocumentFacade.getDocumentByInitials("FinRK"), "FinRK test module missing (~/.sword)")
        // A fresh manager becomes the live instance on construction. The Koin singleton is not
        // live after TestBibleApplication.onTerminate resets the holder; constructing a manager
        // here re-arms history recording, as ReadingHistoryAnchorTest does too.
        val history = HistoryManager(CommonUtils.windowControl).also { this.history = it }
        val window = CommonUtils.windowControl.activeWindow
        val pm = window.pageManager

        pm.setCurrentDocumentAndKey(finRk, verse("Gen.1.1"))
        idle()
        history.clear()

        pm.setCurrentDocument(kjv)   // the chooser path
        idle()
        pm.currentBible.setKey(verse("Gen.2.4"), true)   // search-result tap path
        idle()
        pm.currentBible.setKey(verse("Exod.2.10"), true)
        idle()

        fun here() = pm.currentBible.currentDocument?.initials to pm.currentBible.singleKey?.osisID
        val seen = mutableListOf<Pair<String?, String?>>()
        repeat(3) {
            history.goBack()
            idle()
            seen += here()
        }
        assertEquals(
            listOf<Pair<String?, String?>>("KJV" to "Gen.2.4", "KJV" to "Gen.1.1", "FinRK" to "Gen.1.1"),
            seen,
            "Back sequence after a document switch -- F92",
        )
    }
}
