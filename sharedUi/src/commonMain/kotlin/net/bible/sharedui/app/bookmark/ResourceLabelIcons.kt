@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.app.bookmark

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.bible.sharedui.icons.AbIcons
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Common resource-backed counterpart of AndroidLabelIcons; Android keeps its native drawables. */
@kotlin.native.HiddenFromObjC
open class ResourceLabelIcons : LabelIcons {
    override val iconKeys: List<String?> = AbIcons.labelIconResources.keys.toList() + null
    internal fun labelResource(name: String?): DrawableResource = AbIcons.labelIconResources[name]
        ?: AbIcons.bookmarkIconResources.getValue("ic_baseline_bookmark_24")
    internal fun manageResource(name: String?, studyPadMode: Boolean): DrawableResource = AbIcons.labelIconResources[name]
        ?: AbIcons.bookmarkIconResources.getValue(if (studyPadMode) "ic_baseline_studypads_24" else "ic_label_24dp")
    internal fun actionResource(icon: BookmarkActionIcon): DrawableResource = AbIcons.bookmarkActionIconResources.getValue(when (icon) {
        BookmarkActionIcon.SEARCH -> "ic_search_24dp"
        BookmarkActionIcon.NEW -> "ic_add_circle_outline_white_24dp"
        BookmarkActionIcon.HELP -> "ic_help_white_24dp"
        BookmarkActionIcon.REORDER -> "ic_baseline_refresh_24"
        BookmarkActionIcon.STYLE_EXAMPLES -> "ic_text_format_white_24dp"
        BookmarkActionIcon.UNDO -> "ic_baseline_undo_24"
        BookmarkActionIcon.EXPORT -> "file_export"
        BookmarkActionIcon.IMPORT -> "ic_file_download_24dp"
        BookmarkActionIcon.CHECK -> "ic_check_24dp"
        BookmarkActionIcon.DELETE -> "ic_delete_24dp"
    })
    @Composable override fun LabelIcon(name: String?, tint: Color) {
        Icon(painterResource(labelResource(name)), null, Modifier.size(24.dp), tint)
    }
    @Composable override fun ManageLabelIcon(name: String?, tint: Color, studyPadMode: Boolean) {
        Icon(painterResource(manageResource(name, studyPadMode)), null, Modifier.size(24.dp), tint)
    }
    @Composable override fun ShareIcon() {
        Icon(painterResource(AbIcons.bookmarkIconResources.getValue("ic_baseline_share_24")), null)
    }
    @Composable override fun SaveIcon() {
        Icon(painterResource(AbIcons.bookmarkIconResources.getValue("ic_save_24dp")), null)
    }
    @Composable override fun ActionIcon(icon: BookmarkActionIcon, contentDescription: String?, modifier: Modifier) {
        Icon(painterResource(actionResource(icon)), contentDescription, modifier)
    }
}
