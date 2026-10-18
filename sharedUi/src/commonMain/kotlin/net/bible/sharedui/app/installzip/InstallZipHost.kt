package net.bible.sharedui.app.installzip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.view.activity.installzip.INSTALL_ZIP_SAF_MIME_TYPES
import net.bible.android.view.activity.installzip.InstallZipFlow
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.JobId
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentInstallStarter
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.OrientationLock
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.nav.InstallZipNavDeps
import net.bible.sharedui.installzip.nav.InstallZipSession
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.strings.Strings

private const val TAG = "InstallZipHost"

/** How a host opens InstallZip in-graph: (action, files already picked, answer callback); see [InstallZipHost.open]. */
typealias InstallZipOpener = (String?, List<DocumentRef>, (InstallZipResult) -> Unit) -> Unit

/** What the InstallZip destination needs from the platform beyond the generic ports. */
interface InstallZipHostPlatform {
    /** The app-wide install queue (Android: `DocumentInstallService.controller.jobs`). */
    val jobs: StateFlow<List<InstallJobState>>

    /** Whether [uri] is a StudyPad export (a cheap manifest peek), which skips the "install this?" confirm. */
    suspend fun isStudyPadExport(uri: DocumentRef): Boolean

    fun displayName(uri: DocumentRef): String?

    /** The supported-formats blurb of the picker prelude. */
    fun formatsText(): String

    /** Answers a job's ask-back (overwrite / StudyPad import / EPUB upgrade). */
    fun resolveDecision(jobId: JobId, proceed: Boolean)

    /** Maps a job's phase to what the screen renders, with the platform's localized texts. */
    fun mapPhase(job: InstallJobState): InstallUiState

    /**
     * Asks for POST_NOTIFICATIONS the way classic InstallZip did: a plain permission launcher, no rationale dialog
     * (not the shared `NotificationPermission` port, which raises one), fire-and-forget.
     */
    fun requestNotificationPermission()

    /** Tells the user that no app can pick a file. */
    fun noFileManager()

    /**
     * Initialises the app when the host started uninitialised (on Backup, whose "restore documents" opens InstallZip),
     * before an entry touches anything. A no-op on an initialised host.
     */
    fun initialiseIfStartedUninitialised()
}

/**
 * Builds the InstallZip destination's deps and owns its host-held state: the opener's callback ([installZipReturn])
 * and the host-level collector that applies the destination's answer ([Overlays]). Construction is cheap and must
 * not touch the database (`InstallZipBeforeDbInitTest`): InstallZip starts uninitialised from Backup.
 */
class InstallZipHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val settings: SettingsPlatform,
    private val platform: InstallZipHostPlatform,
    private val starter: DocumentInstallStarter,
    private val documentPicker: DocumentPicker,
    private val orientation: OrientationLock,
) {
    /**
     * What the in-graph caller that opened InstallZip wants done with its answer -- see [open]. Handed from [open]
     * to the session [sessionFor] builds next, and back here only when that session's answer is delivered
     * (`InstallZipSession.onDelivering`), for the collector in [Overlays].
     */
    private var installZipReturn: ((InstallZipResult) -> Unit)? = null

    private var results: NavResultChannel<InstallZipResult>? = null
    private var controller: NavHostController? = null

    /**
     * The destination's deps. [installZipResults] stays declared in the activity: it exits the host with a real
     * result code when InstallZip is the start destination (the external redirect), which only the Activity can do.
     */
    fun navDeps(
        navController: NavHostController,
        installZipResults: NavResultChannel<InstallZipResult>,
    ): InstallZipNavDeps {
        controller = navController
        results = installZipResults
        return InstallZipNavDeps(
            setWindowTitle = { title -> shell.setWindowTitle(title) },
            // The manifest label classic's Activity had: install_zip_module, overridden to install_zip_module_discrete
            // by src/discrete/AndroidManifest.xml.
            windowTitle = windowTitle(),
            installZipResults = installZipResults,
            sessionFor = { action, uris, onFinished -> sessionFor(action, uris, onFinished) },
            lockOrientation = { orientation.lockPortrait() },
            hostBound = { starter.hostBound() },
            hostUnbound = { starter.hostUnbound() },
        )
    }

    /**
     * The host instance is going away: forget the graph and drop the opener's callback, so a later [open] is ignored
     * (no initialise, no stale [installZipReturn] to answer for an entry nobody opened).
     */
    fun onHostDestroy() {
        controller = null
        results = null
        installZipReturn = null
    }

    /** The destination's window title: the discrete build's own wording in the calculator disguise. */
    fun windowTitle(): String =
        if (settings.isDiscreteBuild) strings.installZipModuleDiscrete else strings.installZipModule

    /**
     * Open InstallZip in-graph; [onResult] runs once with its answer (slice 8 §3.2, M5). Never an `awaitIntent`: on a
     * `singleTop` host that is answered by a synthetic cancel before the install runs (`InstallZipAwaitGuardTest`).
     * [action]/[uris] are for an entry that already has files (the reading-plan import picks one first); the in-app
     * default shows the picker.
     *
     * Initialises the app first when the host started uninitialised (Backup's "restore documents").
     */
    fun open(
        action: String? = null,
        uris: List<DocumentRef> = emptyList(),
        onResult: (InstallZipResult) -> Unit = {},
    ) {
        if (controller == null) {
            Log.w(TAG, "InstallZip requested before the graph composed; ignored.")
            return
        }
        platform.initialiseIfStartedUninitialised()
        installZipReturn = onResult
        shell.navigate(NavRoutes.installZip(action, uris.map { it.uri }))
    }

    internal fun sessionFor(
        action: String?,
        uris: List<String>,
        onFinished: (InstallZipResult) -> Unit,
    ): InstallZipSession {
        // The arm-entry half of "initialise first": a destination restored onto an uninitialised host (process death on
        // Backup -> InstallZip) never passed through [open]. Before the session touches anything.
        platform.initialiseIfStartedUninitialised()
        // The opener's callback belongs to THIS session. Taken off the host field now, and put back only when the
        // destination DELIVERS this session's answer (onDelivering). A session that closes unanswered, or whose answer is
        // held while covered and then dropped because its entry left, therefore drops the callback. A later entry nobody
        // opened (a restored one, the redirect's start) cannot answer into it.
        val onReturn = installZipReturn
        installZipReturn = null
        val flow = InstallZipFlow(
            scope = shell.hostScope,
            action = action,
            uris = uris.map(::DocumentRef),
            seams = InstallZipFlow.Seams(
                jobs = platform.jobs,
                isStudyPadExport = platform::isStudyPadExport,
                displayName = platform::displayName,
                formatsText = platform::formatsText,
                pickFile = { documentPicker.openDocuments(INSTALL_ZIP_SAF_MIME_TYPES, multiple = false).firstOrNull() },
                enqueue = { list, act -> starter.start(list, act) },
                resolveDecision = platform::resolveDecision,
                mapPhase = platform::mapPhase,
                requestNotificationPermission = platform::requestNotificationPermission,
                noFileManager = platform::noFileManager,
            ),
            onFinished = onFinished,
        )
        return object : InstallZipSession by flow {
            override fun onDelivering() {
                installZipReturn = onReturn
            }
        }
    }

    /**
     * Host-level, outside the NavHost like the label-manager dialogs: InstallZip's answer goes back to whichever
     * in-graph caller opened it ([installZipReturn]), not to a parent arm -- its callers are host functions. Its own
     * composable so a result recomposes this scope and nothing else.
     */
    @Composable
    fun Overlays() {
        // Ordering dependency: [results] is set by [navDeps], so the graph's deps must be built before this composes
        // (the activity calls [AppHosts.graphs] in a `remember` ahead of AppRoot).
        val channel = results ?: return
        val pending by channel.pending.collectAsState()
        LaunchedEffect(pending) {
            if (pending == null) return@LaunchedEffect
            val result = channel.consume() ?: return@LaunchedEffect
            val onReturn = installZipReturn
            installZipReturn = null
            onReturn?.invoke(result)
        }
    }
}
