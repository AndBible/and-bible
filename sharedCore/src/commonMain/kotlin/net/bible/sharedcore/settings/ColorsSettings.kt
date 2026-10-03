package net.bible.sharedcore.settings

/**
 * Which editable colour swatch a call targets. The first four go through
 * [TextDisplaySettingsService.setColor]; [WORKSPACE] is carried by the same enum (rather than a
 * separate sentinel) so [SettingsEditorPage.ColorPick] can address the workspace swatch too, but it
 * is routed by [ColorSettingsController.onColorChange] to [TextDisplaySettingsService.setWorkspaceColor]
 * instead — see that function's kdoc.
 */
enum class ColorField { DAY_TEXT, DAY_BACKGROUND, NIGHT_TEXT, NIGHT_BACKGROUND, WORKSPACE }

/** One installed background-image module the chooser can offer. [thumbnailToken] is an opaque
 *  host key (the module initials) the chooser resolves to an ImageBitmap via a host lambda —
 *  no android.graphics here. */
data class BackgroundImageOption(
    val initials: String,
    val name: String,
    val thumbnailToken: String,
)

/** Resolved (merged/effective) day+night colour scheme + background-image state for a scope —
 *  everything ColorSettingsScreen renders. Colours are ARGB Ints; noise/opacity are 0..100.
 *  Background-image *names* are pre-resolved host-side ("None" when initials == null). */
data class ColorsSnapshot(
    val title: String,
    val dayTextColor: Int,
    val dayBackground: Int,
    val dayNoise: Int,
    val nightTextColor: Int,
    val nightBackground: Int,
    val nightNoise: Int,
    val workspaceColor: Int,
    val workspaceColorVisible: Boolean,        // false at WINDOW scope
    val dayBackgroundImageInitials: String?,   // null = None
    val dayBackgroundImageName: String,
    val dayBackgroundImageOpacity: Int,        // 0..100
    val nightBackgroundImageInitials: String?,
    val nightBackgroundImageName: String,
    val nightBackgroundImageOpacity: Int,
    val inheritedFrom: InheritedFrom,          // COLORS type inheritance (informational)
)

/** Picks the ARGB value out of [ColorsSnapshot] that [field] addresses — the single place
 *  [ColorSettingsScreen]'s two picker dialogs and [ColorSettingsEditorSheet]'s `ColorPick` page both
 *  resolve an initial colour from. Public (moved out of `ColorSettingsScreen.kt`, where it started
 *  private) so the sheet page can reuse it instead of duplicating the `when`. */
fun ColorsSnapshot.colorFor(field: ColorField): Int = when (field) {
    ColorField.DAY_TEXT -> dayTextColor
    ColorField.DAY_BACKGROUND -> dayBackground
    ColorField.NIGHT_TEXT -> nightTextColor
    ColorField.NIGHT_BACKGROUND -> nightBackground
    ColorField.WORKSPACE -> workspaceColor
}

/** [colorFor] through the controller's [ColorSettingsUiState] wrapper — what
 *  [ColorSettingsEditorSheet]'s `ColorPick` page reads its initial colour from, given only the page
 *  stack's [ColorField] and the live state. */
fun colorForPage(state: ColorSettingsUiState, field: ColorField): Int = state.colors.colorFor(field)
