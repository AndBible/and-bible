package net.bible.sharedcore.docs

/**
 * In-app help links: a [DocsLink] used as a help topic. An `andbible-help:` URL is not opened in a browser; the app's
 * link routing opens Help & tips on the User guide tab, scrolled to the topic's page ([UserGuideToc.pageFor]) and
 * highlighted. It can travel anywhere a link can, including the HTML bodies of help dialogs.
 */
object HelpLinks {
    const val SCHEME: String = "andbible-help:"

    fun url(topic: DocsLink): String = SCHEME + topic.name

    fun isHelpUrl(url: String): Boolean = url.startsWith(SCHEME)

    /** The topic of an [url] made by [url]; null for any other URL or an unknown topic name. */
    fun parse(url: String): DocsLink? =
        if (!isHelpUrl(url)) null else DocsLink.entries.firstOrNull { it.name == url.removePrefix(SCHEME) }

    /**
     * Where a help link goes when the app cannot open Help & tips: its topic's guide page, or the guide's home for a
     * help link with an unknown topic (never the raw `andbible-help:` URL). Other URLs are returned unchanged.
     */
    fun externalUrl(url: String): String = if (!isHelpUrl(url)) url else parse(url)?.url ?: DocsLinks.BASE_URL
}

/** The User guide tab page that covers [topic] (the page of an anchored link); null when the guide has no such page. */
fun UserGuideToc.pageFor(topic: DocsLink): UserGuideToc.Page? =
    sections.firstNotNullOfOrNull { section -> section.pages.firstOrNull { it.stem == topic.page } }
