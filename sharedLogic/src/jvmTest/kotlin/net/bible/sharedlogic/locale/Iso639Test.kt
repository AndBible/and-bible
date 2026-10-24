package net.bible.sharedlogic.locale

import java.util.Locale
import java.util.MissingResourceException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The literal [Iso639] table must equal what the JVM knows, exactly (same derivation as `AndroidPlatformLocales`). */
class Iso639Test {
    private fun liveMap(): Map<String, String> = Locale.getISOLanguages().mapNotNull { iso2 ->
        try {
            iso2 to Locale(iso2).isO3Language
        } catch (e: MissingResourceException) {
            null
        }
    }.toMap()

    @Test fun tableEqualsLiveJvmMap() = assertEquals(liveMap(), Iso639.iso3ByIso2)

    @Test fun spotChecks() {
        assertEquals("eng", Iso639.iso3ByIso2["en"])
        assertEquals("fin", Iso639.iso3ByIso2["fi"])
        assertEquals("heb", Iso639.iso3ByIso2["he"])
    }
}
