package net.bible.sharedui.app.installzip

import androidx.navigation.NavHostController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.JobId
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentInstallStarter
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.android.view.activity.installzip.INSTALL_ZIP_SAF_MIME_TYPES
import net.bible.sharedui.app.ports.OrientationLock
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InstallZipHostTest {
    private val strings = iosStrings("en")
    private var discrete = false
    private val navigated = mutableListOf<String>()
    private val started = mutableListOf<Pair<List<DocumentRef>, String?>>()
    private var initialised = 0
    private var notificationRequests = 0

    private fun shell(scope: CoroutineScope) = object : HostShell {
        override val hostScope: CoroutineScope = scope
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) { navigated += route }
    }

    private val settings = object : SettingsPlatform {
        override fun resetAllSettings() = Unit
        override val isDiscreteBuild: Boolean get() = discrete
        override val confirmText = "OK"
        override val dismissText = "Cancel"
    }

    private val platform = object : InstallZipHostPlatform {
        override val jobs: StateFlow<List<InstallJobState>> = MutableStateFlow(emptyList())
        override suspend fun isStudyPadExport(uri: DocumentRef) = false
        override fun displayName(uri: DocumentRef): String? = "module.zip"
        override fun formatsText() = "formats"
        override fun resolveDecision(jobId: JobId, proceed: Boolean) = Unit
        override fun mapPhase(job: InstallJobState) = InstallUiState.Progress(job.displayName, "status", percent = null, indeterminate = true)
        override fun requestNotificationPermission() { notificationRequests++ }
        override fun noFileManager() = Unit
        override fun initialiseIfStartedUninitialised() { initialised++ }
    }

    private val starter = object : DocumentInstallStarter {
        override fun start(uris: List<DocumentRef>, action: String?) { started += uris to action }
        override fun hostBound() = Unit
        override fun hostUnbound() = Unit
    }

    private class FakePicker(val answer: List<DocumentRef>) : DocumentPicker {
        val asked = mutableListOf<Pair<List<String>, Boolean>>()
        override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef> {
            asked += mimeTypes to multiple
            return answer
        }
        override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? = null
        override suspend fun openDocumentTree(): DocumentRef? = null
    }

    private fun host(scope: CoroutineScope, picker: FakePicker = FakePicker(emptyList())) = InstallZipHost(
        shell = shell(scope),
        strings = strings,
        settings = settings,
        platform = platform,
        starter = starter,
        documentPicker = picker,
        orientation = object : OrientationLock { override fun lockPortrait(): () -> Unit = { } },
    )

    @Test
    fun discreteTitle() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        discrete = true
        assertEquals(strings.installZipModuleDiscrete, host(scope).windowTitle())
        discrete = false
        assertEquals(strings.installZipModule, host(scope).windowTitle())
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun startsInstallThroughPort() = runTest(UnconfinedTestDispatcher()) {
        val picked = DocumentRef("content://x/module.zip")
        val picker = FakePicker(listOf(picked))
        val h = host(backgroundScope, picker)
        val session = h.sessionFor(null, emptyList()) { }
        session.start()
        assertEquals(InstallUiState.FormatInfo("formats"), session.state.value)
        session.confirm() // the formats prelude, then the picker
        assertEquals(listOf<Pair<List<DocumentRef>, String?>>(listOf(picked) to null), started)
        assertEquals(listOf(INSTALL_ZIP_SAF_MIME_TYPES to false), picker.asked)
        assertEquals(1, notificationRequests, "the session asks for the notification permission through the platform")
        assertTrue(initialised > 0, "the session initialises an uninitialised host before touching anything")
    }

    @Test
    fun openBeforeTheGraphComposedIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val h = host(backgroundScope)
        h.open { } // before the graph composed: ignored, no route
        assertEquals(emptyList<String>(), navigated)
    }
}
