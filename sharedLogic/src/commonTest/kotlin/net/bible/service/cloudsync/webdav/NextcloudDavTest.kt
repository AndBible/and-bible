package net.bible.service.cloudsync.webdav

import kotlin.test.Test
import kotlin.test.assertEquals

class NextcloudDavTest {
    private val root = "https://cloud.example.com/remote.php/dav/files/anna/"

    @Test fun plainServer() = assertEquals(root, nextcloudDavUrl("https://cloud.example.com", "anna"))

    @Test fun trailingSlash() = assertEquals(root, nextcloudDavUrl("https://cloud.example.com/", "anna"))

    @Test fun subPathInstall() = assertEquals(
        "https://example.com/nextcloud/remote.php/dav/files/anna/",
        nextcloudDavUrl("https://example.com/nextcloud", "anna"),
    )

    @Test fun alreadyDavUrlIsNotDoubled() {
        assertEquals(root, nextcloudDavUrl("https://cloud.example.com/remote.php/dav/files/anna", "anna"))
        assertEquals(root, nextcloudDavUrl("https://cloud.example.com/remote.php/dav/files/anna/", "anna"))
    }

    @Test fun userNameEncodedOnce() {
        assertEquals(
            "https://c.example/remote.php/dav/files/anna%20maria%40x/",
            nextcloudDavUrl("https://c.example", "anna maria@x"),
        )
        assertEquals(
            "https://c.example/remote.php/dav/files/anna%20maria/",
            nextcloudDavUrl("https://c.example", "anna%20maria"),
        )
    }

    @Test fun nonAsciiUserIsUtf8Encoded() = assertEquals(
        "https://c.example/remote.php/dav/files/j%C3%A4rvi/",
        nextcloudDavUrl("https://c.example", "järvi"),
    )

    @Test fun alreadyDavUrlWithEncodedUserIsKept() = assertEquals(
        "https://c.example/remote.php/dav/files/anna%20maria/",
        nextcloudDavUrl("https://c.example/remote.php/dav/files/anna%20maria", "anna maria"),
    )
}
