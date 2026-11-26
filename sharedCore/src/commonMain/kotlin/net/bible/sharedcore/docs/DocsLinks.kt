package net.bible.sharedcore.docs

/** Builds URLs for the app's [DocsLink] documentation catalog. */
object DocsLinks {
    const val BASE_URL = "https://andbible.org/docs/"

    fun page(link: DocsLink): String =
        BASE_URL + link.page + "/" + (link.anchor?.let { "#$it" } ?: "")

    /** Only published release pages are offered; a future/unknown version must not produce a broken link. */
    fun releaseNotes(version: String?): String {
        val match = version?.let { releaseVersion.matchEntire(it) }
        val link = when (match?.groupValues?.get(1)) {
            "4.0" -> DocsLink.RELEASE_4_0
            "4.1" -> DocsLink.RELEASE_4_1
            "5.0" -> DocsLink.RELEASE_5_0
            "6.0" -> DocsLink.RELEASE_6_0
            else -> DocsLink.DESKTOP_UPDATE_CHECKS
        }
        return link.url
    }

    private val releaseVersion = Regex("""(\d+\.\d+)\.\d+(?:-(?:beta|test))?""")
}
