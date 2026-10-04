package net.bible.sharedcore.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Slice 8 C1: the three routes that can be the nav host's START destination. */
class SliceEightRoutesTest {

    @Test
    fun theArgumentFreeRoutesAreDistinctAndStable() {
        assertEquals("startup/welcome", NavRoutes.WELCOME)
        assertEquals("backup/restore", NavRoutes.BACKUP)
        assertTrue(NavRoutes.WELCOME != NavRoutes.READING && NavRoutes.BACKUP != NavRoutes.READING)
    }

    @Test
    fun theBareInstallZipRouteMatchesItsPatternBase() {
        assertEquals(NavRoutes.INSTALL_ZIP_PATTERN.substringBefore('?'), NavRoutes.installZip())
    }

    /**
     * Review Focus 5. The navigation library decodes a query argument ONCE before an arm reads it;
     * `decodeArg` is that decode here. Commas, `%` and non-ASCII inside one URI must not split or corrupt
     * the list.
     */
    @Test
    fun installZipUrisRoundTripThroughTheLibraryDecode() {
        val uris = listOf(
            "content://com.example.files/doc/a,b%2Cc.zip",
            "content://com.example.files/doc/%E2%82%AC-euro.epub",
            "content://media/external/file/12?x=1&y=2",
            "content://files/äöå sword.zip",
        )
        val route = NavRoutes.installZip(action = "android.intent.action.SEND_MULTIPLE", uris = uris)
        val query = route.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }

        assertEquals("android.intent.action.SEND_MULTIPLE", NavRoutes.decodeArg(query.getValue(NavRoutes.ARG_INSTALL_ACTION)))
        val libraryDecoded = NavRoutes.decodeArg(query.getValue(NavRoutes.ARG_INSTALL_URIS))
        assertEquals(uris, NavRoutes.decodeInstallZipUris(libraryDecoded))
    }

    @Test
    fun noUrisMeansNoUrisArgumentAndAnEmptyDecode() {
        assertTrue(!NavRoutes.installZip().contains(NavRoutes.ARG_INSTALL_URIS))
        assertEquals(emptyList(), NavRoutes.decodeInstallZipUris(null))
        assertEquals(emptyList(), NavRoutes.decodeInstallZipUris(""))
    }

    @Test
    fun theInstallZipResultIsOkOrCanceledOnly() {
        assertEquals(listOf(InstallZipResult.OK, InstallZipResult.CANCELED), InstallZipResult.entries.toList())
    }
}
