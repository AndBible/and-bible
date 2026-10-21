package net.bible.service.legacymigration.extras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.android.database.IdType
import net.bible.service.legacymigration.DeviceLocalPref
import net.bible.service.legacymigration.LegacyExtras
import net.bible.service.legacymigration.LegacySyncSettings
import net.bible.service.legacymigration.PrefType
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport

class LegacyExtrasFileTest {
    private val aiKey = "test-ai-key-not-a-secret"
    private val password = "test-gdrive-password-not-a-secret"

    private fun extras(sync: LegacySyncSettings?) = LegacyExtras(
        aiKeys = mapOf(IdType.fromString("11111111-2222-3333-4444-555555555555") to aiKey),
        sync = sync,
        icloudSyncWasEnabled = true,
        unsentSyncPatches = true,
        userPlanFiles = emptyList(),
        deviceLocalPrefs = listOf(DeviceLocalPref("night_mode_pref3", PrefType.STRING, "manual"), DeviceLocalPref("night_mode_pref", PrefType.BOOLEAN, "true")),
    )

    private val sync = LegacySyncSettings("NEXT_CLOUD", "https://cloud.example/dav", "user", "AndBible", "900", linkedSetOf("bookmarks", "progress"), password)

    private fun report() = MigrationReport().apply {
        skipped(LegacyCategory.BOOKMARKS, "ordinal trust: 2 verses unverified", 2)
        skipped(LegacyCategory.BOOKMARKS, "ordinal trust: another", 3, table = "Bookmark")
        skipped(LegacyCategory.BOOKMARKS, "something else", 7)
        skipped(LegacyCategory.SETTINGS, "ordinal trust (wrong category)", 11)
    }

    @Test
    fun roundTripHasNoSecrets() {
        val text = LegacyExtrasFile.encode(extras(sync), report())
        assertFalse(text.contains(aiKey), "the AI key must not be in extras.json")
        assertFalse(text.contains(password), "the password must not be in extras.json")
        val expected = StoredExtras(
            sync = StoredSync("NEXT_CLOUD", "https://cloud.example/dav", "user", "AndBible", "900", setOf("bookmarks", "progress"), hadPassword = true),
            icloudSyncWasEnabled = true,
            unsentSyncPatches = true,
            deviceLocalPrefs = listOf(DeviceLocalPref("night_mode_pref3", PrefType.STRING, "manual"), DeviceLocalPref("night_mode_pref", PrefType.BOOLEAN, "true")),
            skippedUnverifiedBookmarks = 5,
        )
        assertEquals(expected, LegacyExtrasFile.decode(text))
        assertTrue(LegacyExtrasFile.decode(text).sync!!.hadPassword)
    }

    @Test
    fun noSyncAndNoPasswordRoundTrip() {
        val none = LegacyExtrasFile.decode(LegacyExtrasFile.encode(extras(null), MigrationReport()))
        assertNull(none.sync)
        assertEquals(0, none.skippedUnverifiedBookmarks)
        val noPassword = LegacyExtrasFile.decode(LegacyExtrasFile.encode(extras(sync.copy(password = null, interval = null)), MigrationReport()))
        assertFalse(noPassword.sync!!.hadPassword)
        assertNull(noPassword.sync!!.interval)
    }
}
