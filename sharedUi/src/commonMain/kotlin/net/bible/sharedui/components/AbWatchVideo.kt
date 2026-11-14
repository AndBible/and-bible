package net.bible.sharedui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import net.bible.sharedui.strings.LocalStrings

/**
 * The one affordance for "watch the tutorial video of this screen": an overflow-menu item (the link opens through
 * the host's link opener, which asks first in discrete mode). [url] always comes from
 * `net.bible.sharedcore.docs.TutorialVideos`.
 */
@Composable
fun AbWatchVideoMenuItem(url: String, close: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val linkOpener = LocalAbLinkOpener.current
    AbMenuItem(
        text = LocalStrings.current.watchTutorialVideo,
        onClick = { close(); linkOpener?.open(url) ?: uriHandler.openUri(url) },
        icon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
    )
}

/** A top-bar overflow menu holding only [AbWatchVideoMenuItem], for a screen that has no overflow menu yet. */
@Composable
fun RowScope.AbWatchVideoOverflowMenu(url: String) {
    AbOverflowMenu { close -> AbWatchVideoMenuItem(url, close) }
}
