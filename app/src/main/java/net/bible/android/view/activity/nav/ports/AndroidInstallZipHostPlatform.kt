package net.bible.android.view.activity.nav.ports

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.view.activity.installzip.installZipDisplayName
import net.bible.android.view.activity.installzip.installZipFormatsText
import net.bible.android.view.activity.installzip.mapPhaseToUiState
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.control.event.UserMessages
import net.bible.android.activity.R
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.common.fromUri
import net.bible.service.installzip.DocumentInstallService
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.JobId
import net.bible.sharedui.app.installzip.InstallZipHostPlatform
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.installzip.InstallUiState

/** [InstallZipHostPlatform] over the install service and the Activity's resources. */
class AndroidInstallZipHostPlatform(private val activity: NavHostComposeActivity) : InstallZipHostPlatform {
    private val context: Context get() = activity

    override val jobs: StateFlow<List<InstallJobState>> get() = DocumentInstallService.controller.jobs

    override suspend fun isStudyPadExport(uri: DocumentRef): Boolean =
        AndBibleBackupManifest.fromUri(Uri.parse(uri.uri))?.backupType == BackupType.STUDYPAD_EXPORT

    override fun displayName(uri: DocumentRef): String? = installZipDisplayName(context, Uri.parse(uri.uri))

    override fun formatsText(): String = installZipFormatsText(context)

    override fun resolveDecision(jobId: JobId, proceed: Boolean) = DocumentInstallService.resolveDecision(jobId, proceed)

    override fun mapPhase(job: InstallJobState): InstallUiState = mapPhaseToUiState(context, job.phase, job.displayName)

    override fun noFileManager() = UserMessages.toast(context.getString(R.string.no_file_manager))

    override fun initialiseIfStartedUninitialised() = activity.initialiseIfStartedUninitialised()
}
