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
    private val webUrl = Regex("^(https?)://(?:[^/?#@]*@)?([^/?#:]+)", RegexOption.IGNORE_CASE)

    /** The lower-cased host of an `http`/`https` [url]; null for any other link. Matching is by exact host. */
    fun hostOf(url: String): String? =
        webUrl.find(url.trim())?.groupValues?.get(2)?.lowercase()?.takeIf { it.isNotBlank() }

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
