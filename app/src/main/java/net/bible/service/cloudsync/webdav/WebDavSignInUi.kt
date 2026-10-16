package net.bible.service.cloudsync.webdav

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.webdav.DavCertificateInfo
import org.koin.java.KoinJavaComponent

enum class WebDavMessage { WRONG_CREDENTIALS, NOT_A_FOLDER, CERTIFICATE_CHANGED, STORAGE_FULL }

/** What sign-in needs from the UI: the trust prompt and localized messages. */
interface WebDavSignInUi {
    suspend fun confirmCertificate(info: DavCertificateInfo): Boolean
    fun message(kind: WebDavMessage): String
}

internal fun formatFingerprint(sha256: String): String = sha256.chunked(2).joinToString(":")

/** Production UI: the trust prompt goes through the app-wide [AppDialogController] queue. */
class AndroidWebDavSignInUi : WebDavSignInUi {
    private val app get() = BibleApplication.application
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean {
        val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        val result = dialogs.await(AppDialogRequest.Confirm(
            title = app.getString(R.string.webdav_untrusted_certificate_title),
            message = app.getString(R.string.webdav_untrusted_certificate_message,
                info.host, info.subject, info.issuer,
                df.format(java.util.Date(info.notBefore)), df.format(java.util.Date(info.notAfter)),
                formatFingerprint(info.sha256)),
            confirmText = app.getString(R.string.webdav_trust_certificate),
            dismissText = app.getString(R.string.cancel),
            cancellable = true,
        ))
        return result == AppDialogResult.Ok
    }

    override fun message(kind: WebDavMessage): String = app.getString(when (kind) {
        WebDavMessage.WRONG_CREDENTIALS -> R.string.webdav_wrong_credentials
        WebDavMessage.NOT_A_FOLDER -> R.string.webdav_not_a_folder
        WebDavMessage.CERTIFICATE_CHANGED -> R.string.webdav_certificate_changed
        WebDavMessage.STORAGE_FULL -> R.string.webdav_storage_full
    })
}
