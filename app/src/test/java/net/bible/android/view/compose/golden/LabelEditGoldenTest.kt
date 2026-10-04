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
     *  non-default option renders, and pins `autoAssign = true` too so the section's ⚡ header
     *  mark and its "set as main label" switch (`autoAssignPrimaryEnabled`) are both live rather
     *  than a permanently-off/disabled row. `autoAssignPrimary` is also tied to [ws] (round-12a)
     *  so the header's 🔖 mark and the "add automatically as primary" row's filled icon are both
     *  captured in the same cases the ⚡/⚙ marks already were. [inherit] sets wholeVerseStyle to
     *  null (the "Same as selection" tile) instead of the pinned MARKER used by every other case.
     *  [assigning] gives the bookmark context that reveals the "this bookmark" group, with both
     *  its switches ON (`thisBookmarkSelected`/`thisBookmarkPrimary`) so the group's own 🔖 header
     *  mark renders too, the same way [ws] lights up the workspace group's marks. */
    private fun sample(special: Boolean = false, ws: Boolean = false, inherit: Boolean = false, assigning: Boolean = false) = LabelEditState(
        labelId = "L1", name = "Study", color = AbColor.palette.first(),
        customIcon = null,
        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT,
        wholeVerseStyle = if (inherit) null else BookmarkDisplayStyle.MARKER,
        favourite = true, isAssigning = assigning,
        thisBookmarkSelected = assigning, thisBookmarkPrimary = assigning, hasWorkspaceContext = ws,
        autoAssign = ws, autoAssignPrimary = ws,
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
     *  header carries the auto-assign (⚡) mark, the auto-assign-primary (🔖) mark -- both pinned
     *  on by `sample(ws = true)` -- and a miniature style tag for the override (pinned to
     *  OverrideMode.MARKER), but the switches and override picker underneath are not rendered at
     *  all. Proves ⚡, 🔖 and the tag. There is no ⚙ mark on this screen any more (round-12a): the
     *  override is now shown as the AXIS word ("Workspace", the same LabelStyleTag the list row
     *  draws) rather than as an abstract "something is set" glyph -- and NOT as the name of the
     *  style it imposes (round-15a §4.2 corrected that overclaim). The expanded counterpart is
     *  labelEdit_override_expanded. */
    @Test fun labelEdit_override() =
        captureGolden("LabelEdit", "override", EDGE_MODE, heightDp = 1400, content = screen(sample(ws = true)))

    /** hasWorkspaceContext=true with the section EXPANDED: the auto-assign switch is now CHECKED
     *  (autoAssign = true), which also makes `autoAssignPrimaryEnabled` true so the "set as main
     *  label" switch underneath is live rather than permanently greyed; plus the auto-assign
     *  switch's own ⚡ leading icon (the same mark the list row's toggle uses, and the mark the
     *  collapsed header in labelEdit_override shows), and the override picker. The collapsed
     *  counterpart is labelEdit_override. */
    @Test fun labelEdit_override_expanded() =
        captureGolden(
            "LabelEdit", "override_expanded", EDGE_MODE, heightDp = 1400,
            content = screen(sample(ws = true), workspaceExpanded = true),
        )

    /** hasWorkspaceContext=true, section EXPANDED, OverrideMode.NONE: the override group's preview
     *  is present anyway and shows the label's OWN style, so nothing appears or disappears as the
     *  radio moves. Also the only capture of a HOLLOW bolt in this screen (autoAssign = false), the
     *  editor half of round 12a's false-state fix. */
    @Test fun labelEdit_workspace_noOverride_expanded() =
        captureGolden(
            "LabelEdit", "workspace_noOverride_expanded", EDGE_MODE, heightDp = 1400,
            content = screen(
                sample(ws = true).copy(overrideMode = OverrideMode.NONE, autoAssign = false, autoAssignPrimary = false),
                workspaceExpanded = true,
            ),
        )

    /** isAssigning=true with the "this bookmark" section EXPANDED: both switches are ON
     *  (thisBookmarkSelected/thisBookmarkPrimary), so the section's own 🔖 header mark is visible
     *  at the top AND the two switch rows underneath render -- this is the section's first-ever
     *  golden coverage; nothing previously captured isAssigning=true at all. One EDGE_MODE capture
     *  is enough: this is a state check, not a theme check. Proves 🔖. */
    @Test fun labelEdit_assigning_expanded() =
        captureGolden(
            "LabelEdit", "assigning_expanded", EDGE_MODE, heightDp = 1200,
            content = screen(sample(assigning = true), thisBookmarkExpanded = true),
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
