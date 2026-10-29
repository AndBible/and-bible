package net.bible.service.cloudsync.host

import net.bible.service.cloudsync.webdav.WebDavMessage

/** Localized texts the iOS sync classes need; supplied by the host (`:sharedLogic` cannot see `Strings`). */
data class IosSyncStrings(
    val adapterDisplayName: (String) -> String,
    val webDavServerUrlHint: () -> String,
    val syncError: () -> String,
    val webDavMessage: (WebDavMessage) -> String,
    val ok: () -> String,
)
