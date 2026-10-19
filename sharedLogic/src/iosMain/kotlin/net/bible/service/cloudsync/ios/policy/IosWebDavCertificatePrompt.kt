package net.bible.service.cloudsync.ios

import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.formatFingerprint
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.webdav.DavCertificateInfo

/** Certificate approval is explicit and host/leaf scoped by the existing adapter pin store. */
internal class IosWebDavCertificatePrompt(
    private val dialogs: () -> AppDialogController,
    private val strings: CoreStrings,
    private val date: (Long) -> String,
) : WebDavSignInUi {
    override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean = dialogs().await(
        AppDialogRequest.Confirm(
            title = strings.webDavUntrustedCertificateTitle,
            message = net.bible.sharedcore.ui.dialog.plainTextToHtml(
                strings.webDavUntrustedCertificateMessage(info.host, info.subject, info.issuer,
                    date(info.notBefore), date(info.notAfter), formatFingerprint(info.sha256)),
            ),
            confirmText = strings.webDavTrustCertificate,
            dismissText = strings.cancel,
            cancellable = true,
        ),
    ) == AppDialogResult.Ok

    override fun message(kind: WebDavMessage): String = when (kind) {
        WebDavMessage.WRONG_CREDENTIALS -> strings.webDavWrongCredentials
        WebDavMessage.NOT_A_FOLDER -> strings.webDavNotAFolder
        WebDavMessage.CERTIFICATE_CHANGED -> strings.webDavCertificateChanged
        WebDavMessage.STORAGE_FULL -> strings.webDavStorageFull
    }
}
