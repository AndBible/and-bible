package net.bible.sharedui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedui.strings.tutorialVideoLabel
import net.bible.sharedui.strings.tutorialVideoUrl

/**
 * The one affordance for "watch the tutorial video of this screen": an overflow-menu item (the link opens through
 * the host's link opener, which asks first in discrete mode). The link follows the app language
 * ([tutorialVideoUrl]).
 */
@Composable
fun AbWatchVideoMenuItem(episode: TutorialVideos.Episode, close: () -> Unit) {
    val url = tutorialVideoUrl(episode)
    val uriHandler = LocalUriHandler.current
    val linkOpener = LocalAbLinkOpener.current
    AbMenuItem(
        text = tutorialVideoLabel(episode),
        onClick = { close(); linkOpener?.open(url) ?: uriHandler.openUri(url) },
        icon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
    )
}

/** A top-bar overflow menu holding only [AbWatchVideoMenuItem], for a screen that has no overflow menu yet. */
@Composable
fun RowScope.AbWatchVideoOverflowMenu(episode: TutorialVideos.Episode) {
    AbOverflowMenu { close -> AbWatchVideoMenuItem(episode, close) }
}
