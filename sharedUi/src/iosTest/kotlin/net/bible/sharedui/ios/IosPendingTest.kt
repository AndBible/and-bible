package net.bible.sharedui.ios

import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.host.ports.IosDocumentInstallStarter
import net.bible.sharedui.ios.ports.IosTextDisplayPlatform
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import net.bible.sharedlogic.platform.host.IosUserNotifier
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.bible.sharedui.host.app.IosHostShell
import net.bible.sharedui.host.app.IosNavigator
import net.bible.sharedui.host.ports.IosChooserHostPlatform
import net.bible.sharedui.host.IosPending

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
        IosDocumentInstallStarter().start(emptyList<DocumentRef>(), null)
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
        val permission = object : NotificationPermission {
            override suspend fun request() = Unit
        }
        val koin = org.koin.core.context.startKoin {
            modules(org.koin.dsl.module {
                single<net.bible.android.control.backup.BackupHost> { FakeBackupHost() }
                single<NotificationPermission> { permission }
                single<net.bible.sharedlogic.platform.LocalCalendar> { net.bible.sharedlogic.platform.ios.IosLocalCalendar() }
                single<net.bible.sharedlogic.platform.LocaleSupport> { net.bible.sharedlogic.platform.ios.IosLocaleSupport }
                single<net.bible.sharedcore.platform.CoreStrings> {
                    net.bible.sharedui.strings.keyed.KeyedCoreStrings(net.bible.sharedui.assembly.iosStringTable("en"))
                }
                single<net.bible.sharedcore.platform.AppSettings> {
                    net.bible.sharedlogic.settings.DatabaseAppSettings(
                        { net.bible.service.db.DatabaseContainer.instance.settingsStore },
                        { net.bible.sharedui.assembly.IosAppStartup.databasesOpen },
                    )
                }
                single<net.bible.service.sword.ContentSource> { net.bible.sharedlogic.platform.ios.IosContentSource() }
                // Document ports now use the real app-wide dialog queue and error notifier.
                single { net.bible.sharedcore.ui.dialog.AppDialogController() }
                single<net.bible.sharedcore.platform.UserNotifier> {
                    IosUserNotifier(get())
                }
                single<net.bible.sharedcore.platform.AppEnvironment> {
                    net.bible.sharedcore.platform.IosAppEnvironment(net.bible.sharedui.assembly.InMemorySecretStore(), isDiscrete = { false })
                }
            })
        }.koin
        try {
            val scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)
            val shell = IosHostShell(scope, object : IosNavigator {
                override fun navigate(route: String) = Unit
                override fun popBackStack() = false
                override fun popBackStackTo(route: String, inclusive: Boolean) = false
            })
            val ports = net.bible.sharedui.ios.app.buildIosHostPorts(shell, koin)
            assertSame(permission, ports.notificationPermission)
            assertTrue(ports.welcomePlatform().windowTitle.isNotEmpty())
            assertTrue(ports.chooserPlatform() is IosChooserHostPlatform)
            ports.bookmarkPlatform().documentsRecreation()
            assertEquals(true, ports.readingPlanPlatform().isPortrait)
            assertEquals(emptyList(), ports.installZipPlatform().jobs.value)
            ports.installZipPlatform().initialiseIfStartedUninitialised()
            assertTrue(Regex("ai_prompts_[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}\\.csv").matches(ports.promptFiles().suggestedExportName()))
            assertEquals("", ports.aiPlatform().languageChoices().first().value)
            assertEquals(net.bible.sharedui.app.ai.AI_CUSTOM_LANGUAGE_TAG, ports.aiPlatform().languageChoices().last().value)
            ports.orientationLock.lockCurrentOrientation().invoke()
            assertEquals(null, ports.myDocumentFiles.displayName(DocumentRef("x")))
            assertEquals(net.bible.sharedui.icons.AbIcons.labelIconResources.keys.toList() + null,
                ports.labelIcons.iconKeys)
            ports.cloudDocumentsPlatform()
            ports.bugReport()
            assertFalse("TextShare.shareText" in IosPending.reported)
        } finally {
            org.koin.core.context.stopKoin()
        }
    }
}
