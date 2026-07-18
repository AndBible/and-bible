package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelEditGoldenTest {

    /** [special] hides name-edit/favourite/auto-assign/custom-icon (isSpecialLabel). [ws] gives a
     *  workspace context, revealing the "this workspace" group + override dropdown, pinned to
     *  MARKER so the dropdown's non-default label renders. */
    private fun sample(special: Boolean = false, ws: Boolean = false) = LabelEditState(
        labelId = "L1", name = "Study", color = AbColor.palette.first(),
        customIcon = null, underline = false, underlineWholeVerse = false, marker = true, markerWholeVerse = false,
        hide = false, hideWholeVerse = false, favourite = true, isAssigning = false,
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
            onToggleUnderline = {},
            onToggleUnderlineWholeVerse = {},
            onToggleMarker = {},
            onToggleMarkerWholeVerse = {},
            onToggleHide = {},
            onToggleHideWholeVerse = {},
            onToggleFavourite = {},
            onToggleSelected = {},
            onTogglePrimary = {},
            onToggleAutoAssign = {},
            onToggleAutoAssignPrimary = {},
            onOverrideMode = {},
            onUp = {},
            iconSlot = {},
            actions = {},
        )
    }

    // heightDp=700: colour swatch + name field + 6 style switches + favourite -- the primary state
    // has no "this bookmark"/"this workspace" groups (isAssigning=false, hasWorkspaceContext=false),
    // but the default viewport still clips the tail of the switch list.
    @Test fun labelEdit_primary() =
        captureMatrix("LabelEdit", "primary", heightDp = 700, content = screen(sample()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun labelEdit_primary_rtl() =
        captureRtl("LabelEdit", "primary", heightDp = 700, content = screen(sample()))

    /** isSpecialLabel=true: name field disabled, favourite/custom-icon rows hidden. */
    @Test fun labelEdit_special() =
        captureGolden("LabelEdit", "special", EDGE_MODE, heightDp = 700, content = screen(sample(special = true)))

    /** hasWorkspaceContext=true reveals the "this workspace" group + override dropdown, pinned to
     *  OverrideMode.MARKER so the dropdown shows a non-default selection -- needs more vertical
     *  space than the other states (extra section heading + 2 switches + dropdown). */
    @Test fun labelEdit_override() =
        captureGolden("LabelEdit", "override", EDGE_MODE, heightDp = 950, content = screen(sample(ws = true)))
}
