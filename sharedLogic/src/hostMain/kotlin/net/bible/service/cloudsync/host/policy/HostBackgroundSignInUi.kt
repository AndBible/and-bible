package net.bible.service.cloudsync.host

import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.sharedcore.webdav.DavCertificateInfo

/** Existing credentials/pins only; never asks to trust a new certificate during OS refresh. */
internal class HostBackgroundSignInUi(private val foreground: WebDavSignInUi) : WebDavSignInUi {
    override suspend fun confirmCertificate(info: DavCertificateInfo) = false
    override fun message(kind: WebDavMessage) = foreground.message(kind)
}
