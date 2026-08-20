package net.bible.android.view.compose.golden

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.bookmark.LabelIdentitySheetContent
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The host resolves a label's icon from an Android drawable, so goldens across this package stand
 *  one in. Not cosmetic: with an empty slot the MARKER preview is indistinguishable from HIDDEN,
 *  which is the pair [net.bible.sharedui.bookmark.BookmarkStylePreview] exists to separate.
 *  Package-visible (not `private`) so other golden tests in this package share one stand-in icon
 *  instead of each inlining its own -- see [BookmarkStylePreviewGoldenTest]'s use.
 *
 *  Paints with the caller-supplied [Color], mirroring the real host's `AndroidLabelIcon` (which
 *  also takes an explicit tint since round-9a's I1 fix) -- ignoring it here is exactly what let
 *  the identity row's avatar golden show a crisp glyph on a same-coloured disc, a picture the app
 *  never actually produced (M3). */
val bookmarkIcon: @Composable (String?, Color) -> Unit = { _, tint ->
    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = tint)
}

/** Varies with the key on purpose: with a constant glyph the grid golden cannot distinguish
 *  "each cell got its own key" from "every cell got the same one", which is the mechanism the
 *  identity sheet's icon grid depends on. The real host resolves Android drawables per key. */
private fun standInIconFor(key: String?): ImageVector = when (key) {
    null -> Icons.Filled.Block
    "book" -> Icons.Filled.MenuBook
    "cross" -> Icons.Filled.Add
    "star" -> Icons.Filled.Star
    "question" -> Icons.Filled.QuestionMark
    else -> Icons.Filled.SmartToy
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelEditGoldenTest {

    /** [special] hides name-edit/favourite/auto-assign (isSpecialLabel) -- NOT custom-icon, which
     *  is gated on isSpeakLabel instead and stays visible here. [ws] gives a workspace context,
     *  revealing the "this workspace" group + override picker, pinned to MARKER so the picker's
     *  non-default option renders. [inherit] sets wholeVerseStyle to null (the "Same as selection"
     *  tile) instead of the pinned MARKER used by every other case. */
    private fun sample(special: Boolean = false, ws: Boolean = false, inherit: Boolean = false) = LabelEditState(
        labelId = "L1", name = "Study", color = AbColor.palette.first(),
        customIcon = null,
        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT,
        wholeVerseStyle = if (inherit) null else BookmarkDisplayStyle.MARKER,
        favourite = true, isAssigning = false,
        thisBookmarkSelected = false, thisBookmarkPrimary = false, hasWorkspaceContext = ws,
        autoAssign = false, autoAssignPrimary = false,
        overrideMode = if (ws) OverrideMode.MARKER else OverrideMode.NONE,
        isSpecialLabel = special, isSpeakLabel = false,
    )

    private fun screen(
        state: LabelEditState,
        thisBookmarkExpanded: Boolean = false,
        workspaceExpanded: Boolean = false,
    ) = @androidx.compose.runtime.Composable {
        LabelEditScreen(
            state = state,
            onName = {},
            onColor = {},
            onCustomIcon = {},
            onSelectionStyle = {},
            onWholeVerseStyle = {},
            onToggleFavourite = {},
            onToggleSelected = {},
            onTogglePrimary = {},
            onToggleAutoAssign = {},
            onToggleAutoAssignPrimary = {},
            onOverrideMode = {},
            onUp = {},
            iconKeys = listOf("book", "cross", "star", "question", "robot", null),
            iconSlot = bookmarkIcon,
            actions = {},
            initialThisBookmarkExpanded = thisBookmarkExpanded,
            initialWorkspaceExpanded = workspaceExpanded,
        )
    }

    // heightDp=1000: the identity row (colour circle + name + favourite heart) + 2 style groups
    // (each a preview + 4 radio rows) -- much taller than the six switches this originally
    // replaced. The primary state has no "this bookmark"/"this workspace" groups
    // (isAssigning=false, hasWorkspaceContext=false).
    @Test fun labelEdit_primary() =
        captureMatrix("LabelEdit", "primary", heightDp = 1000, content = screen(sample()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun labelEdit_primary_rtl() =
        captureRtl("LabelEdit", "primary", heightDp = 1000, content = screen(sample()))

    /** isSpecialLabel=true: the identity row's heart is hidden (favouriteVisible=false); the sheet's
     *  name field would be disabled too, but the sheet itself is not open in this capture. Neither
     *  is gated by isSpeakLabel, which this sample leaves false. */
    @Test fun labelEdit_special() =
        captureGolden("LabelEdit", "special", EDGE_MODE, heightDp = 1000, content = screen(sample(special = true)))

    /** wholeVerseStyle = null: the "use a different style" switch is UNCHECKED and the whole-verse
     *  choice group is absent entirely -- this is what a brand-new label shows (a new label
     *  inherits, `BookmarkEntities.kt:720`), and it is visibly shorter than the old two-group
     *  layout this round replaces. */
    @Test fun labelEdit_inherit() =
        captureGolden("LabelEdit", "inherit", EDGE_MODE, heightDp = 1000, content = screen(sample(inherit = true)))

    /** hasWorkspaceContext=true with the section COLLAPSED (the default): the "this workspace"
     *  header carries the auto-assign (bolt) and override (tune) marks -- pinned to
     *  OverrideMode.MARKER so the tune mark shows -- but the switches and override picker
     *  underneath are not rendered at all. The expanded counterpart is labelEdit_override_expanded. */
    @Test fun labelEdit_override() =
        captureGolden("LabelEdit", "override", EDGE_MODE, heightDp = 1400, content = screen(sample(ws = true)))

    /** hasWorkspaceContext=true with the section EXPANDED: the auto-assign switch (with its ⚡, the
     *  same mark the list row's toggle uses), the main-label switch, and the override picker. The
     *  collapsed counterpart is labelEdit_override. */
    @Test fun labelEdit_override_expanded() =
        captureGolden(
            "LabelEdit", "override_expanded", EDGE_MODE, heightDp = 1400,
            content = screen(sample(ws = true), workspaceExpanded = true),
        )

    /** The identity sheet open over the editor: name, colour presets, icon grid. Rendered directly
     *  rather than through a sheet-state toggle, because a ModalBottomSheet's own animation makes a
     *  capture flaky. */
    @Test fun labelEdit_identitySheet() =
        captureGolden(
            "LabelEdit", "identitySheet", EDGE_MODE, heightDp = 900,
            content = {
                LabelIdentitySheetContent(
                    name = "Study",
                    nameEditable = true,
                    colorArgb = AbColor.palette.first(),
                    customIcon = null,
                    iconKeys = listOf("book", "cross", "star", "question", "robot", null),
                    iconVisible = true,
                    onName = {}, onColor = {}, onCustomIcon = {},
                    iconSlot = { key, tint -> Icon(standInIconFor(key), contentDescription = null, tint = tint) },
                )
            },
        )
}
