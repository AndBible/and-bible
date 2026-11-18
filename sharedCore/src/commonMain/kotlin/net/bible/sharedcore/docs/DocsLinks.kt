package net.bible.sharedcore.docs

/** Builds URLs for the app's [DocsLink] documentation catalog. */
object DocsLinks {
    const val BASE_URL = "https://andbible.org/docs/"

    fun page(link: DocsLink): String =
        BASE_URL + link.page + "/" + (link.anchor?.let { "#$it" } ?: "")
}
