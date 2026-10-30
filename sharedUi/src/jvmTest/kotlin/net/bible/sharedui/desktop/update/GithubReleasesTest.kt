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
          {"tag_name":"production-1243","draft":true,"html_url":"https://example.com/draft","assets":[{"name":"andbible-desktop-linux.deb"}]},
          {"tag_name":"production-invalid","draft":false,"html_url":"https://example.com/bad","assets":[{"name":"andbible-desktop-linux.deb"}]}
        ]
    """.trimIndent()

    @Test fun filtersAssetsDraftsAndMalformedTags() {
        val releases = parseDesktopReleases(fixture)
        assertEquals(listOf(1240, 1241), releases.map { it.build })
        assertEquals(listOf(true, false), releases.map { it.production })
    }

    @Test fun productionBuildOffersProductionEvenIfPrerelease() {
        assertEquals(1240, newestOffered(parseDesktopReleases(fixture), 1239, true, 0)?.build)
    }

    @Test fun testBuildOffersNewerTestOrProduction() {
        val releases = parseDesktopReleases(fixture)
        assertEquals(1241, newestOffered(releases, 1239, false, 0)?.build)
        assertEquals(1240, newestOffered(releases.filter { it.production }, 1239, false, 0)?.build)
        assertNull(newestOffered(releases, 1241, false, 0))
        assertNull(newestOffered(releases, 1239, false, 1241))
    }
}
