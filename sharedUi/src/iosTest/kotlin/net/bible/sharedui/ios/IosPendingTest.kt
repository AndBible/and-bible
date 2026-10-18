package net.bible.sharedui.ios

import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.ios.ports.IosAppSettingsService
import net.bible.sharedui.ios.ports.IosDebugActions
import net.bible.sharedui.ios.ports.IosDocumentInstallStarter
import net.bible.sharedui.ios.ports.IosExternalLinks
import net.bible.sharedui.ios.ports.IosPlatformText
import net.bible.sharedui.ios.ports.IosTextDisplayPlatform
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPendingTest {
    @BeforeTest fun reset() = IosPending.resetForTest()
    @AfterTest fun clean() = IosPending.resetForTest()

    @Test
    fun logsEachMemberOnce() {
        val emitted = mutableListOf<String>()
        IosPending.onFirstReport = { emitted += it }
        IosPending.log("X.y")
        IosPending.log("X.y")
        assertEquals(listOf("X.y"), emitted)
        assertEquals(setOf("X.y"), IosPending.reported)
    }

    @Test
    fun pendingPortReturnsNoOpValues() {
        IosExternalLinks().open("https://example.org")
        assertFalse(IosExternalLinks().askBeforeOpening)
        IosDebugActions().showTestError("x")
        IosDocumentInstallStarter().start(emptyList<DocumentRef>(), null)
        assertEquals("1", IosPlatformText().plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT, 1))
        assertNull(IosTextDisplayPlatform().thumbnail("t"))
        val emitted = mutableListOf<String>()
        IosPending.onFirstReport = { emitted += it }
        val entry = IosTextDisplayPlatform().entryFor(TextDisplaySettingsArgs())
        IosTextDisplayPlatform().entryFor(TextDisplaySettingsArgs())
        assertEquals(1, emitted.count { it == "TextDisplayPlatform.entryFor" })
        assertEquals(0, entry.service.loadText(entry.scope).rows.size)
        assertNull(entry.resultOnLeave())
        val settings = IosAppSettingsService()
        settings.setBool("k", true)
        assertFalse(settings.snapshot.value.navigateToVerse)
        assertTrue(settings.snapshot.value.openLinksInSpecialWindow)
        assertTrue("ExternalLinks.open" in IosPending.reported)
        assertTrue("AppSettingsService.setBool" in IosPending.reported)
    }
}

class IosHostPortsTest {
    @BeforeTest fun reset() = IosPending.resetForTest()
    @AfterTest fun clean() = IosPending.resetForTest()

    private class FakeBackupHost : net.bible.android.control.backup.BackupHost {
        override suspend fun showErrorReport(message: String, cause: Throwable?) = Unit
        override suspend fun restoreModules() = Unit
        override suspend fun pickBackupFile(mime: String): net.bible.sharedcore.platform.files.PickedSource? = null
        override suspend fun openInput(source: net.bible.sharedcore.platform.files.PickedSource): okio.Source =
            throw okio.IOException("unused")
        override suspend fun deliver(
            file: String, fileName: String, mode: net.bible.sharedcore.platform.files.SaveOrShare,
            shareMimeType: String, saveMimeType: String, subject: String?, message: String?, chooserTitle: String,
        ): net.bible.sharedcore.platform.files.DeliveryResult = throw okio.IOException("unused")
    }

    @Test
    fun everyHostPortClassIsConstructible() {
        val koin = org.koin.core.context.startKoin {
            modules(org.koin.dsl.module { single<net.bible.android.control.backup.BackupHost> { FakeBackupHost() } })
        }.koin
        try {
            val scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)
            val shell = net.bible.sharedui.ios.app.IosHostShell(scope, object : net.bible.sharedui.ios.app.IosNavigator {
                override fun navigate(route: String) = Unit
                override fun popBackStack() = false
                override fun popBackStackTo(route: String, inclusive: Boolean) = false
            })
            val ports = net.bible.sharedui.ios.app.buildIosHostPorts(shell, koin)
            ports.welcomePlatform().onDownload()
            assertEquals("", ports.welcomePlatform().windowTitle)
            ports.welcomePlatform().controller.refresh()
            ports.chooserPlatform().backupModules()
            assertEquals(emptyList(), ports.chooserPlatform().docRows(emptyList()))
            ports.bookmarkPlatform().documentsRecreation()
            assertEquals(true, ports.readingPlanPlatform().isPortrait)
            assertEquals(emptyList(), ports.installZipPlatform().jobs.value)
            ports.installZipPlatform().initialiseIfStartedUninitialised()
                        assertEquals("", ports.promptFiles().suggestedExportName())
            assertEquals(emptyList(), ports.aiPlatform().languageChoices())
            ports.textShare.shareText("a", "b")
            ports.systemScreens.openAppLinkSettings()
            ports.orientationLock.lockPortrait().invoke()
            assertEquals(null, ports.myDocumentFiles.displayName(DocumentRef("x")))
            assertTrue(ports.labelIcons.iconKeys.isEmpty())
            ports.cloudDocumentsPlatform()
            ports.bugReport()
            assertTrue("WelcomeHostPlatform.onDownload" in IosPending.reported)
            assertTrue("TextShare.shareText" in IosPending.reported)
        } finally {
            org.koin.core.context.stopKoin()
        }
    }
}
