package net.bible.sharedcore.platform.speak

internal data class IosNowPlaying(val title: String, val album: String)

internal class IosSystemMediaMetadata(
    private val isDiscrete: () -> Boolean,
    private val neutralTitle: () -> String,
) {
    /** U7 hides content before the PIN; it does not fake the installed app name (ADR 0007). */
    fun forContent(title: String, album: String): IosNowPlaying =
        if (isDiscrete()) IosNowPlaying(neutralTitle(), "") else IosNowPlaying(title, album)
}
