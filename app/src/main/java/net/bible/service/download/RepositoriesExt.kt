package net.bible.service.download

import org.crosswire.jsword.book.install.Installer
import org.crosswire.jsword.book.install.sword.AbstractSwordInstaller

/**
 * The [RepoBase] data structure does not contain the URL of the repository.
 * Instead, the URL is computed by JSword's [Installer] implementations,
 * but unfortunately it does not provide a direct accessor to obtain it.
 * This extension property uses available information to re-compute the URL.
 */
val Installer.urlPrefix: String? get()
    {
    // base implementation class, necessary to access properties not declared
    // by the interface
    if (this !is AbstractSwordInstaller)
        { return null }

    val protocol = when (type)
        {
        "sword-http" -> "http"
        "sword-https" -> "https"
        else -> null
        }

    return protocol?.let { "${protocol}://${host}/" }
    }
