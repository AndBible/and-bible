package net.bible.sharedui.app.welcome

import kotlinx.coroutines.test.runTest
import net.bible.android.control.download.REQUIRED_MEGS_FOR_DOWNLOADS
import net.bible.android.database.SwordDocumentInfo
import net.bible.sharedcore.nav.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WelcomeFlowCoreTest {
    private class Fixture {
        var language = "en"
        var space = 50L
        var books = emptyList<SwordDocumentInfo>()
        val events = mutableListOf<String>()
        val core = WelcomeFlowCore(
            versionText = { "version" }, supportedFormatsText = { "formats" },
            language = { language }, isDiscrete = { false },
            knownInstalled = { books }, megabytesFree = { space },
            refuseDownload = { events += "storage warning" },
            initialise = { events += "initialise" }, ensureSyncLoaded = { events += "sync" },
            navigate = { events += it },
            bookLabel = { "${it.name} (${it.language})" },
        )
    }

    @Test fun easyStartOnlyForEnglish() {
        val f = Fixture()
        for (language in listOf("en", "en-US", "en_GB", "fi", "sv", "eng")) {
            f.language = language
            assertEquals(language in listOf("en", "en-US", "en_GB"), f.core.loadInfo().easyStartAvailable, language)
        }
    }

    @Test fun downloadRefusedWithoutSpace() = runTest {
        val f = Fixture()
        assertEquals(50L, REQUIRED_MEGS_FOR_DOWNLOADS, "Android's documented threshold")
        f.space = REQUIRED_MEGS_FOR_DOWNLOADS - 1
        f.core.onDownload()
        assertEquals(listOf("storage warning"), f.events)
        f.events.clear()
        f.space = REQUIRED_MEGS_FOR_DOWNLOADS
        f.core.onDownload()
        assertEquals(listOf("initialise", "sync", NavRoutes.download(firstDownload = true)), f.events)
    }

    @Test fun previousInstallOffersRedownload() = runTest {
        val f = Fixture()
        f.core.refreshInfo()
        assertFalse(f.core.controller.state.value.showRedownload)
        val english = SwordDocumentInfo("KJV", "King James", "KJV", "en", "Crosswire")
        val finnish = SwordDocumentInfo("Fin", "Finnish", "Fin", "fi", "Crosswire")
        f.books = listOf(finnish, english)
        f.core.refreshInfo()
        assertTrue(f.core.controller.state.value.showRedownload)
        assertTrue(f.core.controller.state.value.showRedownloadHint)
        f.core.onRedownload()
        assertEquals(listOf(english, finnish), f.core.redownloadBooks)
        f.core.confirmRedownload(setOf("KJV"))
        assertEquals(listOf("initialise", "sync"), f.events.take(2))
        assertTrue(f.events.last().contains("KJV"))
        assertFalse(f.events.last().contains("Finnish"))
        assertEquals(null, f.core.redownloadBooks)
    }

    @Test fun preCreatedControllerRelocalisesWithoutReadingDatabaseAgain() = runTest {
        val f = Fixture()
        f.books = listOf(SwordDocumentInfo("KJV", "King James", "KJV", "en", "Crosswire"))
        f.core.refreshInfo()
        val controller = f.core.controller
        controller.setProgress("in progress")
        assertTrue(controller.state.value.showTabs)
        f.language = "fi"
        f.books = emptyList()
        f.core.refreshTextIfCreated()
        assertFalse(controller.state.value.showTabs)
        assertTrue(controller.state.value.showRedownload, "text refresh must keep the last database answer")
        assertEquals("in progress", controller.state.value.progressText)
    }

    @Test fun emptyRedownloadSelectionDoesNotLeaveWelcome() = runTest {
        val f = Fixture()
        f.books = listOf(SwordDocumentInfo("KJV", "King James", "KJV", "en", "Crosswire"))
        f.core.onRedownload()
        f.core.confirmRedownload(emptySet())
        assertEquals(emptyList(), f.events)
        assertEquals(null, f.core.redownloadBooks)
    }

    @Test fun importInitialisesBeforeOpeningPicker() = runTest {
        val f = Fixture()
        f.core.initialiseThen { f.events += "picker" }
        assertEquals(listOf("initialise", "sync", "picker"), f.events)
    }

    @Test fun leaveForInitialisesBeforeNavigating() = runTest {
        val f = Fixture()
        f.core.leaveFor("destination")
        assertEquals(listOf("initialise", "sync", "destination"), f.events)
    }
}
