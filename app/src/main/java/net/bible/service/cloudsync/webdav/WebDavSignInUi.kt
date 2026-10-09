package net.bible.service.cloudsync.webdav

import net.bible.sharedcore.webdav.DavCertificateInfo

enum class WebDavMessage { WRONG_CREDENTIALS, NOT_A_FOLDER, CERTIFICATE_CHANGED, STORAGE_FULL }

/** What sign-in needs from the UI: the trust prompt and localized messages. */
interface WebDavSignInUi {
    suspend fun confirmCertificate(info: DavCertificateInfo): Boolean
    fun message(kind: WebDavMessage): String
}
