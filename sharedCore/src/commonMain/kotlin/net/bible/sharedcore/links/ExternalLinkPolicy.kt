package net.bible.sharedcore.links

import net.bible.sharedcore.platform.AppSettings

/** What to do with a tapped external link: open it now, or ask first. */
sealed interface ExternalLinkDecision {
    data object Open : ExternalLinkDecision

    /**
     * Ask "open external link?" first. [trustableHost] non-null offers "Don't ask again for <host>"; it is null in
     * discrete mode (which always asks, #4155 decision 1) and for links without a web host (mailto:, malformed).
     */
    data class Ask(val trustableHost: String?) : ExternalLinkDecision
}

/**
 * The single external-link decision shared by every place that may ask before leaving the app
 * (`ExternalLinks.confirmAndOpen`, `AbLinkRouting`, Android `CommonUtils.openLink`), #4155.
 */
object ExternalLinkPolicy {
    private val scheme = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val hostName = Regex("^[a-z0-9._-]+$") // no percent-escapes or sub-delims: anything unusual just asks
    private val ipv6Literal = Regex("^\\[[0-9a-f:.]+]$")

    /**
     * The lower-cased host of an `http`/`https` [url]; null for any other link. Matching is by exact host.
     *
     * Parsed so it never names a host a browser would not open: a URL with a backslash, whitespace or a control
     * character answers null (browsers read a backslash as a slash). Multiple `@` separators in the authority also
     * answer null because native URL parsers disagree with browsers. An IPv6 literal is the whole bracketed
     * address. Null means "ask, without offering to trust".
     */
    fun hostOf(url: String): String? {
        if (url.any { it == '\\' || it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) return null
        val prefix = scheme.find(url) ?: return null
        val rest = url.substring(prefix.range.last + 1)
        val authority = rest.substring(0, rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.takeIf { it >= 0 } ?: rest.length)
        if (authority.count { it == '@' } > 1) return null
        val hostPort = authority.substringAfterLast('@').lowercase()
        val host = if (hostPort.startsWith("[")) {
            hostPort.substring(0, hostPort.indexOf(']').takeIf { it > 0 }?.plus(1) ?: return null)
                .takeIf { ipv6Literal.matches(it) }
        } else hostPort.substringBefore(':').takeIf { hostName.matches(it) }
        return host?.takeIf { it.isNotEmpty() }
    }

    /**
     * [askRequested]: whether this caller asks at all (unchanged per call site). [discrete]: calculator build or
     * discrete mode, which always asks and never offers the option. Otherwise a host in [trusted] opens at once.
     */
    fun decide(url: String, askRequested: Boolean, discrete: Boolean, trusted: Set<String>): ExternalLinkDecision {
        if (discrete) return ExternalLinkDecision.Ask(trustableHost = null)
        if (!askRequested) return ExternalLinkDecision.Open
        val host = hostOf(url) ?: return ExternalLinkDecision.Ask(trustableHost = null)
        return if (host in trusted) ExternalLinkDecision.Open else ExternalLinkDecision.Ask(trustableHost = host)
    }
}

/**
 * The user's trusted link hosts, kept in the shared [AppSettings] store under [KEY] (empty by default, #4155
 * decision 3). Application preferences list and edit the same key.
 */
class TrustedLinkDomains(private val settings: AppSettings) {
    fun domains(): Set<String> = settings.getStringSet(KEY)

    /** Adds [host] (as returned by [ExternalLinkPolicy.hostOf]). */
    fun trust(host: String) {
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return
        setDomains(domains() + normalized)
    }

    fun setDomains(domains: Set<String>) = settings.setStringSet(KEY, domains)

    fun decide(url: String, askRequested: Boolean, discrete: Boolean): ExternalLinkDecision =
        ExternalLinkPolicy.decide(url, askRequested, discrete, if (discrete || !askRequested) emptySet() else domains())

    companion object {
        const val KEY = "trusted_link_domains"
    }
}
