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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.bible.android.view.activity.installzip.InstallZipFlow
import net.bible.service.installzip.InstallPhase
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
        orientation = object : OrientationLock { override fun lockCurrentOrientation(): () -> Unit = { } },
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
    fun legacyDrainWatcherLosesConflatedJobTransitions() = runTest(StandardTestDispatcher()) {
        val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
        val answers = mutableListOf<InstallZipResult>()
        val running = InstallJobState("fast-invalid", "invalid.zip", InstallPhase.Queued)
        val flow = drainFlow(backgroundScope, jobs, answers) {
            // A fast worker can publish both transitions before the Main collector resumes.
            jobs.value = listOf(running)
            jobs.value = emptyList()
        }
        try {
            flow.start()
            runCurrent() // Drain watcher has observed the initial empty list before confirmation.
            assertEquals(InstallUiState.ConfirmInstall("invalid.zip"), flow.state.value)
            assertEquals(emptyList(), answers)
            flow.confirm()
            runCurrent()
            // Characterization of the unchanged legacy Android drain path, not the required iOS behavior.
            assertEquals(emptyList(), answers, "Legacy emission-only completion cannot observe a conflated round trip")
        } finally {
            flow.close()
        }
    }

    @Test
    fun separatelyObservedJobDrainFinishesTheConfirmedFlowOnce() = runTest(StandardTestDispatcher()) {
        val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
        val answers = mutableListOf<InstallZipResult>()
        val running = InstallJobState("observed-invalid", "invalid.zip", InstallPhase.Queued)
        val flow = drainFlow(backgroundScope, jobs, answers) { jobs.value = listOf(running) }
        try {
            flow.start()
            runCurrent()
            assertEquals(InstallUiState.ConfirmInstall("invalid.zip"), flow.state.value)
            flow.confirm()
            runCurrent()
            assertEquals(emptyList(), answers, "A running job must not finish its entry")
            jobs.value = emptyList()
            runCurrent()
            assertEquals(listOf(InstallZipResult.OK), answers)
            flow.back()
            runCurrent()
            assertEquals(listOf(InstallZipResult.OK), answers, "A finished flow answers only once")
        } finally {
            flow.close()
        }
    }

    @Test
    fun ownedCompletionFinishesEvenWhenJobTransitionsConflate() = runTest(StandardTestDispatcher()) {
        val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
        val answers = mutableListOf<InstallZipResult>()
        var submissions = 0
        val flow = drainFlow(backgroundScope, jobs, answers, enqueueAndAwait = { refs, action ->
            submissions++
            assertEquals(listOf(DocumentRef("file:///tmp/invalid.zip")), refs)
            assertEquals("android.intent.action.VIEW", action)
            jobs.value = listOf(InstallJobState("fast-own", "invalid.zip", InstallPhase.Queued))
            jobs.value = emptyList()
        }) { error("An opted-in entry must not also use legacy start") }
        try {
            flow.start()
            runCurrent()
            flow.confirm()
            runCurrent()
            assertEquals(1, submissions)
            assertEquals(listOf(InstallZipResult.OK), answers)
            flow.back()
            runCurrent()
            assertEquals(listOf(InstallZipResult.OK), answers)
        } finally { flow.close() }
    }

    @Test
    fun ownedCompletionWaitsForActualRegistrationAndThenGlobalDrain() = runTest(StandardTestDispatcher()) {
        val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
        val answers = mutableListOf<InstallZipResult>()
        val registered = CompletableDeferred<Unit>()
        val ownTerminal = CompletableDeferred<Unit>()
        val foreign = InstallJobState("foreign", "other.zip", InstallPhase.Queued)
        val flow = drainFlow(backgroundScope, jobs, answers, enqueueAndAwait = { _, _ ->
            registered.await() // Mirrors Default dispatch before the actual enqueue registers jobs.
            jobs.value = listOf(foreign)
            ownTerminal.await()
        }) { error("Legacy start is not the opted-in completion path") }
        try {
            flow.start()
            runCurrent()
            flow.confirm()
            runCurrent()
            assertTrue(jobs.value.isEmpty())
            assertEquals(emptyList(), answers, "Initial empty jobs must not answer before actual submission")
            registered.complete(Unit)
            runCurrent()
            assertEquals(emptyList(), answers)
            ownTerminal.complete(Unit)
            runCurrent()
            assertEquals(emptyList(), answers, "Own aggregate completion must still wait for global drain parity")
            jobs.value = emptyList()
            runCurrent()
            assertEquals(listOf(InstallZipResult.OK), answers)
            jobs.value = listOf(foreign)
            jobs.value = emptyList()
            runCurrent()
            assertEquals(listOf(InstallZipResult.OK), answers)
        } finally { flow.close() }
    }

    @Test
    fun backWhileOwnedCompletionWaitsAnswersCanceledOnlyOnce() = runTest(StandardTestDispatcher()) {
        val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
        val answers = mutableListOf<InstallZipResult>()
        val terminal = CompletableDeferred<Unit>()
        val flow = drainFlow(backgroundScope, jobs, answers, enqueueAndAwait = { _, _ -> terminal.await() }) {
            error("Legacy start is not the opted-in completion path")
        }
        try {
            flow.start()
            runCurrent()
            flow.confirm()
            runCurrent()
            flow.back()
            runCurrent()
            assertEquals(listOf(InstallZipResult.CANCELED), answers)
            terminal.complete(Unit)
            runCurrent()
            assertEquals(listOf(InstallZipResult.CANCELED), answers)
        } finally { flow.close() }
    }

    @Test
    fun closeWhileOwnedCompletionWaitsNeverAnswers() = runTest(StandardTestDispatcher()) {
        val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
        val answers = mutableListOf<InstallZipResult>()
        val terminal = CompletableDeferred<Unit>()
        val flow = drainFlow(backgroundScope, jobs, answers, enqueueAndAwait = { _, _ -> terminal.await() }) {
            error("Legacy start is not the opted-in completion path")
        }
        try {
            flow.start()
            runCurrent()
            flow.confirm()
            runCurrent()
            flow.close()
            terminal.complete(Unit)
            runCurrent()
            flow.back()
            assertEquals(emptyList(), answers, "A removed entry must not answer into a later opener")
        } finally { flow.close() }
    }

    private fun drainFlow(
        scope: CoroutineScope,
        jobs: StateFlow<List<InstallJobState>>,
        answers: MutableList<InstallZipResult>,
        enqueueAndAwait: (suspend (List<DocumentRef>, String?) -> Unit)? = null,
        enqueue: () -> Unit,
    ) = InstallZipFlow(
        scope = scope,
        action = "android.intent.action.VIEW",
        uris = listOf(DocumentRef("file:///tmp/invalid.zip")),
        seams = InstallZipFlow.Seams(
            jobs = jobs,
            isStudyPadExport = { false },
            displayName = { "invalid.zip" },
            formatsText = { error("External VIEW must not show the picker prelude") },
            pickFile = { error("External VIEW must not invoke a picker") },
            enqueue = { _, _ -> enqueue() },
            enqueueAndAwait = enqueueAndAwait,
            resolveDecision = { _, _ -> error("No decision was requested") },
            mapPhase = { InstallUiState.Progress(it.displayName, "status", percent = null, indeterminate = true) },
            requestNotificationPermission = {},
            noFileManager = { error("No picker was invoked") },
        ),
        onFinished = { answers += it },
    )

    @Test
    fun openBeforeTheGraphComposedIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val h = host(backgroundScope)
        h.open { } // before the graph composed: ignored, no route
        assertEquals(emptyList<String>(), navigated)
    }
}
