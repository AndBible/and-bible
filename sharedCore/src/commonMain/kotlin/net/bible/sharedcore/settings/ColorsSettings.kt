package net.bible.sharedcore.settings

/** Which of the four editable colour swatches a [TextDisplaySettingsService.setColor] call targets. */
enum class ColorField { DAY_TEXT, DAY_BACKGROUND, NIGHT_TEXT, NIGHT_BACKGROUND }

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
