package net.bible.sharedcore.docs

/**
 * Links into the user documentation at https://andbible.org/docs/.
 *
 * Every app link into the docs goes through [page] so that
 * `website/tests/test_app_deep_links.py` can find each (page, anchor) pair
 * statically and check it against the built site. Keep calls literal:
 * `DocsLinks.page("ai", "setting-permissions")`, never computed strings.
 */
object DocsLinks {
    const val BASE_URL = "https://andbible.org/docs/"

    /** URL of docs page [page] (file stem, e.g. "study_pads"), optionally at heading [anchor]. */
    fun page(page: String, anchor: String? = null): String =
        BASE_URL + page + "/" + (anchor?.let { "#$it" } ?: "")
}
