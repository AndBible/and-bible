package net.bible.sharedui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.HelpLinks
import net.bible.sharedui.strings.LocalStrings

/**
 * The one affordance for "help on this screen" (#4159): an overflow-menu item that opens Help & tips on the User
 * guide tab, scrolled to [topic]'s page and highlighted; the user picks the guide page or the tutorial video there.
 */
@Composable
fun AbHelpMenuItem(topic: DocsLink, close: () -> Unit) {
    val open = rememberLinkOpener()
    AbMenuItem(
        text = LocalStrings.current.helpInUserGuide,
        onClick = { close(); open(HelpLinks.url(topic)) },
        icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null) },
    )
}

/** A top-bar overflow menu holding only [AbHelpMenuItem], for a screen that has no overflow menu yet. */
@Composable
fun RowScope.AbHelpOverflowMenu(topic: DocsLink) {
    AbOverflowMenu { close -> AbHelpMenuItem(topic, close) }
}
