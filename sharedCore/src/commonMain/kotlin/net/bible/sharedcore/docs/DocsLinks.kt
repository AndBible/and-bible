package net.bible.sharedcore.docs

/** Builds URLs for the app's [DocsLink] documentation catalog. */
object DocsLinks {
    const val BASE_URL = "https://andbible.org/docs/"

    fun page(link: DocsLink): String =
        BASE_URL + link.page + "/" + (link.anchor?.let { "#$it" } ?: "")

    /** Only published release pages are offered; a future/unknown version must not produce a broken link. */
    fun releaseNotes(version: String?): String {
        val majorMinor = version?.let { releaseVersion.matchEntire(it)?.groupValues?.get(1) }
        val releasePage = majorMinor?.replace('.', '_')?.let { "releases/release_$it" }
        val link = DocsLink.entries.firstOrNull { it.page == releasePage } ?: DocsLink.DESKTOP_UPDATE_CHECKS
        return link.url
    }

    private val releaseVersion = Regex("""(\d+\.\d+)\.\d+(?:-(?:beta|test))?""")
}
