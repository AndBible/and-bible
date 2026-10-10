package net.bible.android.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.control.backup.*
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/** Android picker/share implementation owned by the current UI host. */
class AndroidBackupHost(private val activityProvider: () -> ActivityBase) : BackupHost {
    override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
        val activity = activityProvider()
        val uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", File(file))
        val intent = when (mode) {
            SaveOrShare.SAVE -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = saveMimeType
            }
            SaveOrShare.SHARE -> Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (message != null) putExtra(Intent.EXTRA_TEXT, message)
                type = shareMimeType
            }
        }
        val result = activity.awaitIntent(Intent.createChooser(intent, chooserTitle))
        val destinationUri = result.data?.data
        val outcome = androidDeliveryResult(mode, result.resultCode, destinationUri != null)
        if (mode == SaveOrShare.SHARE || destinationUri == null) return outcome
        return withContext(Dispatchers.IO) {
            val dialogs = KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java)
            val progressId = dialogs.show(AppDialogRequest.Progress(title = null, message = BibleApplication.application.getString(R.string.please_wait)))
            try {
                val out = BibleApplication.application.contentResolver.openOutputStream(destinationUri)!!
                val inputStream = FileInputStream(file)
                var ok = true
                try {
                    out.use { inputStream.copyTo(out) }
                } catch (ex: IOException) {
                    Log.e("BackupControl", ex.message ?: "Error occurred in backuping db")
                    ok = false
                }
                if (ok) DeliveryResult.DELIVERED else DeliveryResult.FAILED
            } finally {
                dialogs.dismiss(progressId)
            }
        }
    }

    override suspend fun pickBackupFile(mime: String): PickedSource? {
        val result = activityProvider().awaitIntent(Intent(Intent.ACTION_GET_CONTENT).apply { type = mime })
        return if (result.resultCode == Activity.RESULT_OK) fromUri(result.data?.data!!) else null
    }
    override suspend fun openInput(source: PickedSource): InputStream =
        BibleApplication.application.contentResolver.openInputStream(Uri.parse(source.token)) ?: throw IOException("Failed to open input stream")

    override suspend fun sniffFileType(source: PickedSource): BackupFileType = CommonUtils.determineFileType(Uri.parse(source.token))

    override suspend fun showErrorReport(message: String, cause: Throwable?) {
        ErrorReportControl.showErrorDialog(activityProvider(), message, exception = cause)
    }

    override suspend fun restoreModules() = withContext(Dispatchers.Main) {
        net.bible.android.view.activity.backup.BackupNavigation.restoreModulesViaIntent(activityProvider())
    }

    companion object {
        fun fromUri(uri: Uri): PickedSource = PickedSource(uri.toString(), null, null)
    }
}

/** A save URI wins over the result code; share sheets commonly return CANCELED after sending. */
internal fun androidDeliveryResult(mode: SaveOrShare, resultCode: Int, hasUri: Boolean): DeliveryResult =
    if (mode == SaveOrShare.SAVE) {
        when {
            hasUri -> DeliveryResult.DELIVERED
            resultCode == Activity.RESULT_CANCELED -> DeliveryResult.CANCELLED
            else -> DeliveryResult.FAILED
        }
    } else if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED) DeliveryResult.DELIVERED
    else DeliveryResult.FAILED
