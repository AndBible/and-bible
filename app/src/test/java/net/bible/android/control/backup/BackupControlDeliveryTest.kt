package net.bible.android.control.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupControlDeliveryTest {
    private val dispatcher = StandardTestDispatcher()
    private val dialogs = AppDialogController()
    private lateinit var previous: AppDialogController
    private lateinit var overrideModule: org.koin.core.module.Module
    private lateinit var isolated: org.koin.core.KoinApplication

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        previous = GlobalContext.get().get()
        isolated = koinApplication { modules(module { single { dialogs } }) }
        overrideModule = module { single { isolated.koin.get<AppDialogController>() } }
        GlobalContext.get().loadModules(listOf(overrideModule))
    }
    @After fun cleanup() {
        dialogs.cancelAll()
        GlobalContext.get().unloadModules(listOf(overrideModule))
        GlobalContext.get().loadModules(listOf(module { single { previous } }))
        isolated.close()
        Dispatchers.resetMain()
    }

    private class Host(val result: DeliveryResult) : BackupHost {
        val errors = mutableListOf<String>()
        val mimes = mutableListOf<Pair<String, String>>()
        override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
            mimes += shareMimeType to saveMimeType
            return result
        }
        override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
        override suspend fun openInput(source: PickedSource): InputStream = error("unused")
        override suspend fun sniffFileType(source: PickedSource): BackupFileType = error("unused")
        override suspend fun showErrorReport(message: String, cause: Throwable?) { errors += message }
    }
    private suspend fun deliver(host: Host, mode: SaveOrShare = SaveOrShare.SAVE) = BackupControl.saveOrShare(
        host = host, file = File("backup.zip"), fileName = "backup.zip", chooserTitle = "Backup",
        successMsg = R.string.backup_success2, shareMimeType = "*/*", saveMimeType = "application/vnd.android.package-archive",
        chooseDestination = { mode },
    )

    @Test fun cancelledDeliveryIsSilent() = runTest(dispatcher) {
        val host = Host(DeliveryResult.CANCELLED)
        assertFalse(deliver(host))
        assertTrue(host.errors.isEmpty())
        assertNull(dialogs.pending.value)
    }
    @Test fun failedDeliveryReportsOnceWithoutSuccess() = runTest(dispatcher) {
        val host = Host(DeliveryResult.FAILED)
        assertFalse(deliver(host))
        assertEquals(listOf(RuntimeEnvironment.getApplication().getString(R.string.error_occurred)), host.errors)
        assertNull(dialogs.pending.value)
    }
    @Test fun deliveredSaveShowsSuccessAndPreservesDistinctMimes() = runTest(dispatcher) {
        val host = Host(DeliveryResult.DELIVERED)
        val result = async { deliver(host) }
        advanceUntilIdle()
        val pending = dialogs.pending.value!!
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.backup_success2), (pending.request as AppDialogRequest.Message).message)
        dialogs.respond(pending.id, AppDialogResult.Ok)
        assertTrue(result.await())
        assertTrue(host.errors.isEmpty())
        assertEquals(listOf("*/*" to "application/vnd.android.package-archive"), host.mimes)
    }
}
