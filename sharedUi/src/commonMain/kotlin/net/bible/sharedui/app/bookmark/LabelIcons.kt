@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.bookmark

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The top-bar and menu glyphs of the bookmark, label manager and label editor screens. */
@kotlin.native.HiddenFromObjC
enum class BookmarkActionIcon { SEARCH, NEW, HELP, REORDER, STYLE_EXAMPLES, UNDO, EXPORT, IMPORT, CHECK, DELETE }

/**
 * The glyphs the bookmark cluster draws from platform resources (Android: `R.drawable` and `LabelIconMap`, which
 * stays in `:app`). A host-scoped feature port held in `HostPorts`.
 */
@kotlin.native.HiddenFromObjC
interface LabelIcons {
    /** The custom-icon keys the label editor offers, with a trailing null for "no custom icon". */
    val iconKeys: List<String?>

    /** The label's glyph in the editor: the custom icon named [name] or the default bookmark glyph, tinted [tint]. */
    @Composable
    fun LabelIcon(name: String?, tint: Color)

    /** The label's glyph for a manager row; [studyPadMode] picks the mode's default glyph. */
    @Composable
    fun ManageLabelIcon(name: String?, tint: Color, studyPadMode: Boolean)

    @Composable
    fun ShareIcon()

    @Composable
    fun SaveIcon()

    /** A top-bar or overflow glyph; the caller sizes it through [modifier]. */
    @Composable
    fun ActionIcon(icon: BookmarkActionIcon, contentDescription: String?, modifier: Modifier)
}
