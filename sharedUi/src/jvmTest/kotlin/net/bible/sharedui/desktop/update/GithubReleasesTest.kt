package net.bible.sharedui.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GithubReleasesTest {
    private val fixture = """
        [
          {"tag_name":"production-1240","draft":false,"prerelease":true,"html_url":"https://example.com/1240","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"production-1242","draft":false,"html_url":"https://example.com/android","assets":[{"name":"andbible-android.apk"}]},
          {"tag_name":"test-1241","draft":false,"html_url":"https://example.com/1241","assets":[{"name":"andbible-desktop-linux.AppImage"}]},
          {"tag_name":"beta-1245","draft":false,"prerelease":true,"html_url":"https://example.com/1245","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"production-1243","draft":true,"html_url":"https://example.com/draft","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"production-invalid","draft":false,"html_url":"https://example.com/bad","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"beta-","draft":false,"html_url":"https://example.com/bad2","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"beta-12x","draft":false,"html_url":"https://example.com/bad3","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"Beta-1250","draft":false,"html_url":"https://example.com/bad4","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"gamma-1260","draft":false,"html_url":"https://example.com/bad5","assets":[{"name":"andbible-desktop-linux.deb"}]}
        ]
    """.trimIndent()

    private val all = parseDesktopReleases(fixture)

    @Test fun filtersAssetsDraftsAndMalformedTags() {
        assertEquals(listOf(1240, 1241, 1245), all.map { it.build })
        assertEquals(
            listOf(DesktopChannel.PRODUCTION, DesktopChannel.TEST, DesktopChannel.BETA),
            all.map { it.channel },
        )
    }

    private fun release(channel: DesktopChannel, build: Int) = DesktopRelease("${channel.name.lowercase()}-$build", build, channel, "u")

    /** (installed channel, available releases) -> offered tag; the rule is test < beta < production. */
    @Test fun offeredReleaseByInstalledChannel() {
        val t = DesktopChannel.TEST; val b = DesktopChannel.BETA; val p = DesktopChannel.PRODUCTION
        val cases: List<Triple<DesktopChannel, List<DesktopRelease>, String?>> = listOf(
            Triple(p, listOf(release(t, 1301), release(b, 1302)), null),
            Triple(p, listOf(release(t, 1301), release(b, 1302), release(p, 1300)), "production-1300"),
            Triple(p, listOf(release(b, 1302), release(p, 1300), release(p, 1299)), "production-1300"),
            Triple(b, listOf(release(t, 1301), release(b, 1302)), "beta-1302"),
            Triple(b, listOf(release(t, 1310), release(b, 1302)), "beta-1302"),
            Triple(b, listOf(release(t, 1310)), null),
            Triple(b, listOf(release(b, 1302), release(p, 1303)), "production-1303"),
            Triple(b, listOf(release(b, 1302), release(p, 1300)), "beta-1302"),
            Triple(t, listOf(release(t, 1301), release(b, 1302), release(p, 1303)), "production-1303"),
            Triple(t, listOf(release(t, 1301), release(b, 1302)), "beta-1302"),
            Triple(t, listOf(release(t, 1301)), "test-1301"),
            Triple(t, emptyList(), null),
        )
        cases.forEach { (own, releases, expected) ->
            assertEquals(expected, newestOffered(releases, 1250, own, 0)?.tag, "own=$own releases=${releases.map { it.tag }}")
        }
    }

    /** (installed channel, offerBeta setting, releases) -> offered tag; null setting = default (ON for beta installs only). */
    @Test fun offeredReleaseBySettingAndChannel() {
        val t = DesktopChannel.TEST; val b = DesktopChannel.BETA; val p = DesktopChannel.PRODUCTION
        val all3 = listOf(release(t, 1301), release(b, 1302), release(p, 1300))
        val cases: List<Quadruple> = listOf(
            Quadruple(p, true, listOf(release(b, 1302), release(p, 1300)), "beta-1302"),
            Quadruple(p, true, listOf(release(b, 1302), release(p, 1303)), "production-1303"),
            Quadruple(p, true, listOf(release(t, 1310)), null),
            Quadruple(p, false, listOf(release(b, 1302), release(p, 1300)), "production-1300"),
            Quadruple(p, false, listOf(release(b, 1302)), null),
            Quadruple(p, null, listOf(release(b, 1302)), null),
            Quadruple(b, true, listOf(release(b, 1302), release(p, 1300)), "beta-1302"),
            Quadruple(b, null, listOf(release(b, 1302), release(p, 1300)), "beta-1302"),
            Quadruple(b, false, listOf(release(b, 1302), release(p, 1300)), "production-1300"),
            Quadruple(b, false, listOf(release(b, 1302)), null),
            Quadruple(b, false, listOf(release(b, 1302), release(p, 1303)), "production-1303"),
            Quadruple(b, false, listOf(release(p, 1249)), null),
            Quadruple(t, false, all3, "beta-1302"),
            Quadruple(t, true, all3, "beta-1302"),
            Quadruple(t, null, listOf(release(t, 1301)), "test-1301"),
        )
        cases.forEach { c ->
            assertEquals(c.expected, newestOffered(c.releases, 1250, c.own, 0, c.offerBeta)?.tag,
                "own=${c.own} offerBeta=${c.offerBeta} releases=${c.releases.map { it.tag }}")
        }
    }

    private data class Quadruple(val own: DesktopChannel, val offerBeta: Boolean?, val releases: List<DesktopRelease>, val expected: String?)

    @Test fun defaultOfferBetaIsOnOnlyForBetaInstalls() {
        assertEquals(true, DesktopChannel.BETA.defaultOfferBeta)
        assertEquals(false, DesktopChannel.PRODUCTION.defaultOfferBeta)
    }

    @Test fun fixtureOffersPerChannel() {
        assertEquals("production-1240", newestOffered(all, 1239, DesktopChannel.PRODUCTION, 0)?.tag)
        assertEquals("beta-1245", newestOffered(all, 1239, DesktopChannel.BETA, 0)?.tag)
        assertEquals("beta-1245", newestOffered(all, 1239, DesktopChannel.TEST, 0)?.tag)
    }

    @Test fun neverOffersOlderOrAnnouncedBuild() {
        assertNull(newestOffered(all, 1245, DesktopChannel.TEST, 0))
        assertNull(newestOffered(all, 1239, DesktopChannel.TEST, 1245))
    }

    @Test fun releaseKindMapsToChannelAndRejectsDev() {
        assertEquals(DesktopChannel.TEST, DesktopChannel.fromReleaseKind("test"))
        assertEquals(DesktopChannel.BETA, DesktopChannel.fromReleaseKind("beta"))
        assertEquals(DesktopChannel.PRODUCTION, DesktopChannel.fromReleaseKind("production"))
        assertNull(DesktopChannel.fromReleaseKind("dev"))
        assertNull(DesktopChannel.fromReleaseKind(null))
    }
}
