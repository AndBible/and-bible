package net.bible.service.legacymigration.extras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.service.legacymigration.DeviceLocalPref
import net.bible.service.legacymigration.PrefType
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class LegacyExtrasApplierTest {
    private class CountingPrefs : DeviceLocalPrefs {
        val map = mutableMapOf<String, Any>()
        var writes = 0
        override fun getString(key: String, default: String?) = map[key] as? String ?: default
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun setString(key: String, value: String) { writes++; map[key] = value }
        override fun setBoolean(key: String, value: Boolean) { writes++; map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private class MapSettings : AppSettings {
        val map = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = map[key] as? String ?: default
        override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
        override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = map[key] as? Double ?: default
        override fun getFloat(key: String, default: Float) = map[key] as? Float ?: default
        override fun setString(key: String, value: String?) { map[key] = value }
        override fun setLong(key: String, value: Long?) { map[key] = value }
        override fun setInt(key: String, value: Int?) { map[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { map[key] = value }
        override fun setDouble(key: String, value: Double?) { map[key] = value }
        override fun setFloat(key: String, value: Float?) { map[key] = value }
    }

    private val fs = FakeFileSystem()
    private val dir = "/mig".toPath()
    private val prefs = CountingPrefs()

    private fun stored(
        sync: StoredSync? = null,
        icloud: Boolean = false,
        unsent: Boolean = false,
        local: List<DeviceLocalPref> = emptyList(),
        skipped: Int = 0,
    ) = StoredExtras(sync, icloud, unsent, local, skipped)

    private val nextcloud = StoredSync("NEXT_CLOUD", "https://cloud.example/dav", "user", "AndBible", "900", setOf("bookmarks"), hadPassword = true)

    private fun write(extras: StoredExtras? = stored()) {
        fs.createDirectories(dir)
        if (extras != null) fs.write(dir / LegacyExtrasFile.NAME) { writeUtf8(encode(extras)) }
    }

    private fun encode(e: StoredExtras): String {
        val lp = e.deviceLocalPrefs.joinToString(",") { """{"key":"${it.key}","type":"${it.type.name}","value":${kotlinx.serialization.json.JsonPrimitive(it.value)}}""" }
        val sync = e.sync?.let { s ->
            """{"adapter":"${s.adapter}","serverUrl":"${s.serverUrl}","username":"${s.username}","folderPath":"${s.folderPath}","interval":null,"enabledCategories":[],"hadPassword":${s.hadPassword}}"""
        } ?: "null"
        return """{"version":1,"sync":$sync,"icloudSyncWasEnabled":${e.icloudSyncWasEnabled},"unsentSyncPatches":${e.unsentSyncPatches},"deviceLocalPrefs":[$lp],"skippedUnverifiedBookmarks":${e.skippedUnverifiedBookmarks}}"""
    }

    private fun applier() = LegacyExtrasApplier(fs, dir, prefs)

    @Test
    fun nightModeDefaultsToSystemWhenAbsent() {
        write()
        applier().applyBeforeDatabases()
        assertEquals("system", prefs.map["night_mode_pref3"])
    }

    @Test
    fun nightModeAndSpeakCarriedVerbatim() {
        val speak = """{"playbackSettings":{"speed":120}}"""
        write(stored(local = listOf(
            DeviceLocalPref("night_mode_pref3", PrefType.STRING, "manual"),
            DeviceLocalPref("night_mode_pref", PrefType.BOOLEAN, "true"),
            DeviceLocalPref("SpeakSettings", PrefType.STRING, speak),
        )))
        applier().applyBeforeDatabases()
        assertEquals("manual", prefs.map["night_mode_pref3"])
        assertEquals(true, prefs.map["night_mode_pref"])
        assertEquals(speak, prefs.map["SpeakSettings"])
    }

    @Test
    fun nextcloudCredentialsWrittenUnderAndroidKeys() {
        write(stored(sync = nextcloud))
        applier().applyBeforeDatabases()
        assertEquals("https://cloud.example/dav", prefs.map["cloud_sync_server_url"])
        assertEquals("user", prefs.map["cloud_sync_username"])
        assertEquals("AndBible", prefs.map["cloud_sync_folder_path"])
        assertFalse(prefs.map.keys.any { it.contains("password") })
    }

    @Test
    fun afterReadySetsAdapterAndLeavesSyncOff() {
        write(stored(sync = nextcloud))
        val settings = MapSettings()
        applier().applyAfterReady(settings)
        assertEquals("NEXT_CLOUD", settings.map["sync_adapter"])
        assertTrue(settings.map.keys.none { it.startsWith("sync_enable_") })
    }

    @Test
    fun noSyncMeansNoCredentialsAndNoAdapter() {
        write(stored())
        val settings = MapSettings()
        val a = applier()
        a.applyBeforeDatabases()
        a.applyAfterReady(settings)
        assertNull(prefs.map["cloud_sync_server_url"])
        assertNull(prefs.map["cloud_sync_username"])
        assertNull(prefs.map["cloud_sync_folder_path"])
        assertTrue(settings.map.isEmpty())
    }

    @Test
    fun noticesFollowExtras() {
        write(stored(sync = nextcloud, icloud = true, unsent = true, skipped = 2))
        val a = applier()
        assertEquals(
            listOf(LegacyNotice.ICLOUD_SYNC_MOVED, LegacyNotice.NEXTCLOUD_SYNC_OFF, LegacyNotice.NEXTCLOUD_UNSENT, LegacyNotice.BOOKMARKS_SKIPPED),
            a.pendingNotices(),
        )
        assertEquals(2, a.skippedBookmarks)
    }

    @Test
    fun noticesAbsentWhenNothingToTell() {
        write(stored())
        assertEquals(emptyList(), applier().pendingNotices())
        assertEquals(0, applier().skippedBookmarks)
    }

    @Test
    fun applyIsIdempotentAcrossRestarts() {
        write(stored(sync = nextcloud))
        applier().applyBeforeDatabases()
        val writes = prefs.writes
        prefs.map["night_mode_pref3"] = "dark" // the user changed it between launches
        applier().applyBeforeDatabases()
        assertEquals(writes, prefs.writes)
        assertEquals("dark", prefs.map["night_mode_pref3"])
    }

    @Test
    fun afterReadyIsIdempotent() {
        write(stored(sync = nextcloud))
        val settings = MapSettings()
        applier().applyAfterReady(settings)
        settings.map["sync_adapter"] = "WEBDAV"
        applier().applyAfterReady(settings)
        assertEquals("WEBDAV", settings.map["sync_adapter"])
    }

    @Test
    fun markShownPersists() {
        write(stored(icloud = true, unsent = true))
        applier().markShown(LegacyNotice.ICLOUD_SYNC_MOVED)
        assertEquals(listOf(LegacyNotice.NEXTCLOUD_UNSENT), applier().pendingNotices())
        // applying afterwards must not lose the shown record
        applier().applyBeforeDatabases()
        assertEquals(listOf(LegacyNotice.NEXTCLOUD_UNSENT), applier().pendingNotices())
    }

    @Test
    fun missingExtrasFileMeansNothingToApply() {
        write(null)
        val settings = MapSettings()
        val a = applier()
        a.applyBeforeDatabases()
        a.applyAfterReady(settings)
        assertEquals(0, prefs.writes)
        assertTrue(settings.map.isEmpty())
        assertEquals(emptyList(), a.pendingNotices())
        assertEquals(0, a.skippedBookmarks)
    }
}
