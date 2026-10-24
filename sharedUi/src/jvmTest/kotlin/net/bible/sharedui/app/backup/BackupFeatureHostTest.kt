package net.bible.sharedui.app.backup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.sharedcore.backup.ToggleKind
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BackupFeatureHostTest {
    private val titles = mutableListOf<String>()
    private var exited = 0

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(
            // restore() reloads the screen state afterwards; the stub collaborators cannot serve that and it is not under test.
            Dispatchers.Unconfined + Job() + CoroutineExceptionHandler { _, _ -> },
        )
        override fun exitHost() { exited++ }
        override fun setWindowTitle(title: String) { titles += title }
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    /** Records restoreModules; every other member is unused by the documents-only restore. */
    private class FakeBackupHost : BackupHost {
        var restoreModulesCalls = 0
        override suspend fun showErrorReport(message: String, cause: Throwable?) = Unit
        override suspend fun restoreModules() { restoreModulesCalls++ }
        override suspend fun pickBackupFile(mime: String): PickedSource? = null
        override suspend fun openInput(source: PickedSource): okio.Source = error("unused")
        override suspend fun deliver(
            file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
            subject: String?, message: String?, chooserTitle: String,
        ): DeliveryResult = error("unused")
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            // An uninitialised environment makes the service skip persisting toggles.
            if (m.name == "isInitialized" || m.name == "getIsInitialized") false else error("unexpected ${m.name}")
        } as T

    /** Never invoked: a documents-only restore goes through the host port and does not reach BackupControl. */
    private val bareBackupControl: BackupControl by lazy {
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        sun.misc.Unsafe::class.java.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, BackupControl::class.java) as BackupControl
    }

    private fun host(backupHost: BackupHost) = BackupFeatureHost(
        shell = shell,
        strings = iosStrings("en"),
        backupHost = backupHost,
        backupControlProvider = { bareBackupControl },
        settingsProvider = { unused<AppSettings>() },
        environmentProvider = { unused<AppEnvironment>() },
        coreStringsProvider = { unused<CoreStrings>() },
        directoriesProvider = { unused<AppDirectories>() },
        dateTimeFormatsProvider = { unused<DateTimeFormats>() },
    )

    private fun navController(): NavHostController {
        lateinit var nav: NavHostController
        runComposeUiTest { setContent { nav = rememberNavController() } }
        return nav
    }

    @Test
    fun controllerUsesPortBackupHost() {
        val fake = FakeBackupHost()
        val deps = host(fake).navDeps(navController())
        val controller = deps.controllerFor()
        controller.setToggle(ToggleKind.RestoreDatabase, false)
        controller.setToggle(ToggleKind.RestoreDocuments, true)
        controller.restore()
        // The service restores documents through the host the port supplied, not one the host builds itself.
        assertEquals(1, fake.restoreModulesCalls)
    }

    @Test
    fun depsReportTitleAndExit() {
        val deps = host(FakeBackupHost()).navDeps(navController())
        assertEquals(iosStrings("en").backupAndRestoreTitle, deps.windowTitle)
        deps.setWindowTitle("T")
        deps.exitHost()
        assertEquals(listOf("T"), titles)
        assertEquals(1, exited)
    }

    @Test
    fun awaitDestinationNavigatesAndReturnsWhenLeft() = runBlocking {
        val host = host(FakeBackupHost())
        var navigated = 0
        val waiting = async(Dispatchers.Unconfined) { host.awaitDestination(currentRoute = "reading") { navigated++ } }
        assertEquals(1, navigated)
        assertTrue(!waiting.isCompleted)
        host.completeReturn()
        waiting.await()
    }

    @Test
    fun awaitDestinationReturnsAtOnceWhenAlreadyOnBackup() = runBlocking {
        val host = host(FakeBackupHost())
        var navigated = 0
        host.awaitDestination(currentRoute = net.bible.sharedcore.nav.NavRoutes.BACKUP) { navigated++ }
        assertEquals(0, navigated)
    }

    @Test
    fun secondRequestSupersedesTheFirst() = runBlocking {
        val host = host(FakeBackupHost())
        val first = async(Dispatchers.Unconfined) { host.awaitDestination("reading") { } }
        val second = async(Dispatchers.Unconfined) { host.awaitDestination("reading") { } }
        first.await()
        assertTrue(!second.isCompleted)
        host.completeReturn()
        second.await()
    }
}
