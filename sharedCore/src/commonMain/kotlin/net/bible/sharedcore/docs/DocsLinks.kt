package net.bible.sharedcore.docs

/** Builds URLs for the app's [DocsLink] documentation catalog. */
object DocsLinks {
    const val BASE_URL = "https://andbible.org/docs/"

    fun page(link: DocsLink): String = page(link.page, link.anchor)

    // Kept for the generated UserGuideToc until its generator is migrated to DocsLink.
    // The website gate requires all of its targets to exist in the catalog too.
    internal fun page(page: String, anchor: String? = null): String =
        BASE_URL + page + "/" + (anchor?.let { "#$it" } ?: "")
}
