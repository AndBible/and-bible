package net.bible.android.view.activity.nav.ports

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.bible.android.activity.R
import net.bible.android.view.activity.bookmark.customIconMap
import net.bible.sharedui.app.bookmark.BookmarkActionIcon
import net.bible.sharedui.app.bookmark.LabelIcons

/**
 * [LabelIcons] over the Android drawables, moved here unchanged from `NavHostComposeActivity`'s label baggage
 * (the glyph bodies are verbatim from classic `LabelEditComposeActivity` and `ManageLabelsComposeActivity`).
 */
class AndroidLabelIcons : LabelIcons {
    /** Classic's `remember { customIconMap.keys.toList() + null }`; the trailing null is the "no custom icon" cell. */
    override val iconKeys: List<String?> = customIconMap.keys.toList() + null

    /** Verbatim from classic `LabelEditComposeActivity.kt:285-294`. */
    @Composable
    override fun LabelIcon(name: String?, tint: Color) {
        val drawableId = customIconMap[name] ?: R.drawable.ic_baseline_bookmark_24
        Icon(
            painter = painterResource(drawableId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }

    /** The label's glyph for a list row, verbatim from classic (`:691-699`). */
    @Composable
    override fun ManageLabelIcon(name: String?, tint: Color, studyPadMode: Boolean) {
        val defaultId = if (studyPadMode) R.drawable.ic_baseline_studypads_24 else R.drawable.ic_label_24dp
        Icon(
            painter = painterResource(customIconMap[name] ?: defaultId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }

    @Composable
    override fun ShareIcon() {
        Icon(painterResource(R.drawable.ic_baseline_share_24), contentDescription = null)
    }

    @Composable
    override fun SaveIcon() {
        Icon(painterResource(R.drawable.ic_save_24dp), contentDescription = null)
    }

    @Composable
    override fun ActionIcon(icon: BookmarkActionIcon, contentDescription: String?, modifier: Modifier) {
        val drawableId = when (icon) {
            BookmarkActionIcon.SEARCH -> R.drawable.ic_search_24dp
            BookmarkActionIcon.NEW -> R.drawable.ic_add_circle_outline_white_24dp
            BookmarkActionIcon.HELP -> R.drawable.ic_help_white_24dp
            BookmarkActionIcon.REORDER -> R.drawable.ic_baseline_refresh_24
            BookmarkActionIcon.STYLE_EXAMPLES -> R.drawable.ic_text_format_white_24dp
            BookmarkActionIcon.UNDO -> R.drawable.ic_baseline_undo_24
            BookmarkActionIcon.EXPORT -> R.drawable.file_export
            BookmarkActionIcon.IMPORT -> R.drawable.ic_file_download_24dp
            BookmarkActionIcon.CHECK -> R.drawable.ic_check_24dp
            BookmarkActionIcon.DELETE -> R.drawable.ic_delete_24dp
        }
        Icon(painter = painterResource(drawableId), contentDescription = contentDescription, modifier = modifier)
    }
}
