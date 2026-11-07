package net.bible.sharedui.desktop.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Release channel, ordered by stability: [TEST] < [BETA] < [PRODUCTION]. An installation is offered
 * releases of its own channel or a more stable one, never a less stable one.
 */
enum class DesktopChannel(val tagPrefix: String) {
    TEST("test"), BETA("beta"), PRODUCTION("production");

    /** Whether an installation on this channel may be offered [release]. */
    fun accepts(release: DesktopRelease): Boolean = release.channel >= this

    /** Default of the "offer public beta updates" setting: ON for a beta install, OFF for a production install. */
    val defaultOfferBeta: Boolean get() = this == BETA

    /**
     * Least stable channel offered to an install on this channel. Test builds always see test/beta/production;
     * beta and production installs see betas only with [offerBeta] (null = [defaultOfferBeta]).
     */
    fun offerFloor(offerBeta: Boolean?): DesktopChannel = when {
        this == TEST -> TEST
        offerBeta ?: defaultOfferBeta -> BETA
        else -> PRODUCTION
    }

    companion object {
        /** Maps the build's `andbible.releaseKind`; `dev` and unknown values have no channel (no update check). */
        fun fromReleaseKind(kind: String?): DesktopChannel? = entries.firstOrNull { it.tagPrefix == kind }
        fun fromTagPrefix(prefix: String): DesktopChannel? = fromReleaseKind(prefix)

        /** System property that makes any build (e.g. `gradle run`, release kind `dev`) a beta build, to check the beta notice. */
        const val FORCE_BETA_PROPERTY = "andbible.forceBeta"
        /** Environment variable alternative to [FORCE_BETA_PROPERTY] (`ANDBIBLE_FORCE_BETA=1`). */
        const val FORCE_BETA_ENV = "ANDBIBLE_FORCE_BETA"

        /**
         * Whether this desktop build is a beta build (the beta notice, beta features): packaged from a `beta-*` or
         * `test-*` tag (release kind [BETA] or [TEST]). Production packages and dev runs are not, unless forced with
         * [FORCE_BETA_PROPERTY] = `true` or [FORCE_BETA_ENV] = `1`.
         */
        fun isBetaBuild(properties: (String) -> String?, environment: (String) -> String?): Boolean {
            if (properties(FORCE_BETA_PROPERTY) == "true" || environment(FORCE_BETA_ENV) == "1") return true
            return when (fromReleaseKind(properties("andbible.releaseKind"))) {
                BETA, TEST -> true
                PRODUCTION, null -> false
            }
        }
    }
}

data class DesktopRelease(val tag: String, val build: Int, val channel: DesktopChannel, val htmlUrl: String)

fun parseDesktopReleases(json: String): List<DesktopRelease> {
    val releases = Json.parseToJsonElement(json) as? JsonArray ?: return emptyList()
    val tagPattern = Regex("^(test|beta|production)-(\\d+)$")
    return releases.mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        if (release["draft"]?.jsonPrimitive?.booleanOrNull != false) return@mapNotNull null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val match = tagPattern.matchEntire(tag) ?: return@mapNotNull null
        val build = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val assets = release["assets"] as? JsonArray ?: return@mapNotNull null
        if (assets.none { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.startsWith("andbible-desktop-") == true }) return@mapNotNull null
        val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        DesktopRelease(tag, build, DesktopChannel.fromTagPrefix(match.groupValues[1]) ?: return@mapNotNull null, url)
    }
}

fun newestOffered(releases: List<DesktopRelease>, ownBuild: Int, ownChannel: DesktopChannel, announcedBuild: Int, offerBeta: Boolean? = null): DesktopRelease? =
    releases.filter { it.build > ownBuild && it.build > announcedBuild && it.channel >= ownChannel.offerFloor(offerBeta) }
        .maxByOrNull { it.build }
