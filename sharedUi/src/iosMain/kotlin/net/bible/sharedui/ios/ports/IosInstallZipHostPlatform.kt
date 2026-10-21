@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.installzip.*
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.installzip.InstallZipHostPlatform
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.iosStringTable
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.strings.keyed.StringTable
import org.koin.mp.KoinPlatform

/** iOS UI adapter over the same process-wide queue used by [IosDocumentInstallStarter]. */
@kotlin.native.HiddenFromObjC
class IosInstallZipHostPlatform(
    private val controller: () -> InstallServiceController = { IosInstallPipeline.controller },
    private val scope: CoroutineScope = IosInstallPipeline.scope,
    private val table: () -> StringTable = { iosStringTable("en") },
    private val notificationPermission: NotificationPermission = IosNotificationPermission(),
    // "If started": before startup completes (or after a reset) there is nothing to initialise, and the table is not there to read.
    private val initialise: suspend () -> Unit = { if (IosAppStartup.isStarted) IosInstallPipeline.ensureInitialized() },
) : InstallZipHostPlatform {
    override val jobs get() = controller().jobs

    override suspend fun isStudyPadExport(uri: DocumentRef): Boolean = withContext(Dispatchers.Default) {
        try { IosInstallPipeline.isStudyPad(uri.uri) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    override fun displayName(uri: DocumentRef): String? = IosInstallPipeline.url(uri.uri).lastPathComponent

    override fun formatsText(): String {
        val table = table()
        val zip = table.fmt("format_zip", table.text("app_name_andbible"))
        val formats = listOf(zip, table.text("format_mybible"), table.text("format_mysword"),
            table.text("format_esword"), table.text("format_epub"), table.text("format_ttf"),
            table.text("format_csv_prompts"), table.text("format_studypads")).joinToString(", ")
        return table.fmt("choose_file", table.text("app_name_andbible")) + " \n\n" + table.fmt("supported_formats", formats)
    }

    override fun resolveDecision(jobId: JobId, proceed: Boolean) = controller().resolveDecision(jobId, proceed)

    override fun mapPhase(job: InstallJobState): InstallUiState {
        val table = table()
        return when (val phase = job.phase) {
        InstallPhase.Queued, InstallPhase.Inspecting ->
            InstallUiState.Progress(job.displayName, table.text("checking_zip_file"), null, true)
        is InstallPhase.Acquiring ->
            InstallUiState.Progress(job.displayName, table.text("checking_zip_file"), phase.percent, false)
        is InstallPhase.Committing ->
            InstallUiState.Progress(job.displayName, table.text("extracting_zip_file"), phase.percent, false)
        InstallPhase.Done -> InstallUiState.Progress(job.displayName, table.text("extracting_zip_file"), 100, false)
        is InstallPhase.AwaitingDecision -> when (val request = phase.request) {
            is DecisionRequest.Overwrite -> InstallUiState.Overwrite(request.files)
            is DecisionRequest.StudyPadImport -> InstallUiState.StudyPadImport(request.statsText)
            DecisionRequest.EpubUpgrade -> InstallUiState.EpubUpgrade
        }
        is InstallPhase.Error -> {
            val key = when (phase.kind) {
                InstallError.GENERIC -> "error_occurred"
                InstallError.INVALID_FILE -> "sqlite_invalid_file"
            }
            InstallUiState.Error(if (phase.arg == null) table.text(key) else table.fmt(key, phase.arg))
        }
        InstallPhase.Cancelled -> InstallUiState.Error(table.text("install_zip_canceled"))
    }

    }

    override fun requestNotificationPermission() {
        scope.launch { notificationPermission.request() }
    }

    override fun noFileManager() {
        scope.launch {
            val table = table()
            KoinPlatform.getKoin().get<AppDialogController>().await(AppDialogRequest.Message(
                title = null, message = table.text("no_file_manager"), confirmText = table.text("okay"),
            ))
        }
    }

    override fun initialiseIfStartedUninitialised() {
        scope.launch(Dispatchers.Default) { initialise() }
    }
}
