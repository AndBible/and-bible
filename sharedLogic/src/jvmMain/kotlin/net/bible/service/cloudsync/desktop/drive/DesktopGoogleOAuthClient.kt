package net.bible.service.cloudsync.desktop.drive

import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClient
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients

/**
 * The desktop OAuth client: the JVM properties `andbible.googleClientId` / `andbible.googleClientSecret` (blank is
 * unset) override [GoogleOAuthClients.desktop]. `null` means Google Drive is not offered.
 */
fun GoogleOAuthClients.desktopClient(property: (String) -> String? = System::getProperty): GoogleOAuthClient? {
    val id = property("andbible.googleClientId")?.trim().orEmpty()
    if (id.isEmpty()) return desktop
    return GoogleOAuthClient(id, property("andbible.googleClientSecret")?.trim()?.takeIf { it.isNotEmpty() })
}

/** The page the loopback server answers once the browser was redirected back; [text] is HTML-escaped. */
fun googleDriveSignInDonePage(text: String): String {
    val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>AndBible</title></head><body><p>$escaped</p></body></html>"
}
