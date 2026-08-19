package net.bible.android.view.compose.golden

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedui.bookmark.LabelEditScreen
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
 *  instead of each inlining its own -- see [BookmarkStylePreviewGoldenTest]'s use. */
val bookmarkIcon: @Composable (String?) -> Unit = {
    Icon(Icons.Filled.Bookmark, contentDescription = null)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelEditGoldenTest {

    /** [special] hides name-edit/favourite/auto-assign (isSpecialLabel) -- NOT custom-icon, which
     *  is gated on isSpeakLabel instead and stays visible here. [ws] gives a workspace context,
     *  revealing the "this workspace" group + override picker, pinned to MARKER so the picker's
     *  non-default option renders. */
    private fun sample(special: Boolean = false, ws: Boolean = false) = LabelEditState(
        labelId = "L1", name = "Study", color = AbColor.palette.first(),
        customIcon = null,
        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT, wholeVerseStyle = BookmarkDisplayStyle.MARKER,
        favourite = true, isAssigning = false,
        thisBookmarkSelected = false, thisBookmarkPrimary = false, hasWorkspaceContext = ws,
        autoAssign = false, autoAssignPrimary = false,
        overrideMode = if (ws) OverrideMode.MARKER else OverrideMode.NONE,
        isSpecialLabel = special, isSpeakLabel = false,
    )

    private fun screen(state: LabelEditState) = @androidx.compose.runtime.Composable {
        LabelEditScreen(
            state = state,
            onName = {},
            onColor = {},
            onEditIcon = {},
            onSelectionStyle = {},
            onWholeVerseStyle = {},
            onToggleFavourite = {},
            onToggleSelected = {},
            onTogglePrimary = {},
            onToggleAutoAssign = {},
            onToggleAutoAssignPrimary = {},
            onOverrideMode = {},
            onUp = {},
            iconSlot = bookmarkIcon,
            actions = {},
        )
    }

    // heightDp=1000: colour swatch + name field + 2 style groups (each a preview + 4 radio rows)
    // + favourite -- much taller than the six switches this replaced. The primary state has no
    // "this bookmark"/"this workspace" groups (isAssigning=false, hasWorkspaceContext=false).
    @Test fun labelEdit_primary() =
        captureMatrix("LabelEdit", "primary", heightDp = 1000, content = screen(sample()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun labelEdit_primary_rtl() =
        captureRtl("LabelEdit", "primary", heightDp = 1000, content = screen(sample()))

    /** isSpecialLabel=true: name field disabled, favourite row hidden. The custom-icon row is
     *  NOT hidden by isSpecialLabel -- it is gated on isSpeakLabel, which this sample leaves
     *  false, so it stays visible and editable here (visible in the recorded PNG). */
    @Test fun labelEdit_special() =
        captureGolden("LabelEdit", "special", EDGE_MODE, heightDp = 1000, content = screen(sample(special = true)))

    /** hasWorkspaceContext=true reveals the "this workspace" group + override picker, pinned to
     *  OverrideMode.MARKER so the picker shows a non-default selection -- needs more vertical
     *  space than the other states (extra section heading + 2 switches + 5-option picker). */
    @Test fun labelEdit_override() =
        captureGolden("LabelEdit", "override", EDGE_MODE, heightDp = 1400, content = screen(sample(ws = true)))
}
