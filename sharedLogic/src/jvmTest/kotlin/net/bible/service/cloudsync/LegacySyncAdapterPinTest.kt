package net.bible.service.cloudsync

import net.bible.sharedcore.platform.AppSettings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * #4102 follow-up: an install that already synced under the implicit old default (iOS: iCloud, desktop: Nextcloud)
 * must keep that backend after Google Drive became the fresh-install default; only fresh installs get Drive.
 */
private fun stubSettings(): AppSettings =
    Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf<Class<*>>(AppSettings::class.java)) { _, _, _ -> null } as AppSettings

class LegacySyncAdapterPinTest {
    private class Store(initial: Map<String, String> = emptyMap(), bools: Map<String, Boolean> = emptyMap()) {
        val strings = initial.toMutableMap()
        val booleans = bools.toMutableMap()
        val settings: AppSettings = object : AppSettings by stubSettings() {
            override fun getString(key: String, default: String?) = strings[key] ?: default
            override fun setString(key: String, value: String?) { if (value == null) strings.remove(key) else strings[key] = value }
            override fun getBoolean(key: String, default: Boolean) = booleans[key] ?: default
            override fun setBoolean(key: String, value: Boolean?) { if (value == null) booleans.remove(key) else booleans[key] = value }
        }
    }

    private fun factory(available: List<String>, legacy: String? = null) = object : CloudAdapterFactory {
        override val available = available
        override val legacyDefaultId = legacy ?: available.firstOrNull()
        override fun displayName(id: String) = id
        override fun create(id: String): CloudAdapter = error("unused")
    }

    private val ios = factory(listOf("GOOGLE_DRIVE", "ICLOUD_DRIVE", "NEXT_CLOUD", "WEBDAV"), legacy = "ICLOUD_DRIVE")
    private val desktop = factory(listOf("GOOGLE_DRIVE", "NEXT_CLOUD", "WEBDAV"), legacy = "NEXT_CLOUD")

    @Test fun iosInstallWithSyncOnKeepsICloud() {
        val s = Store(bools = mapOf("sync_enable_bookmarks" to true))
        assertEquals("ICLOUD_DRIVE", ios.effectiveAdapterId(s.settings))
        ios.pinLegacyAdapterIfInUse(s.settings)
        assertEquals("ICLOUD_DRIVE", s.strings["sync_adapter"])
    }

    @Test fun desktopInstallWithSyncOnKeepsNextcloud() {
        val s = Store(bools = mapOf("sync_enable_workspaces" to true))
        desktop.pinLegacyAdapterIfInUse(s.settings)
        assertEquals("NEXT_CLOUD", s.strings["sync_adapter"])
        assertEquals("NEXT_CLOUD", desktop.effectiveAdapterId(s.settings))
    }

    @Test fun freshInstallDefaultsToDriveAndIsNotPinnedByTheMigration() {
        val s = Store()
        desktop.pinLegacyAdapterIfInUse(s.settings)
        assertNull(s.strings["sync_adapter"])
        assertEquals("GOOGLE_DRIVE", desktop.effectiveAdapterId(s.settings))
    }

    @Test fun enablingOnAFreshInstallPinsTheCurrentDefault() {
        val s = Store()
        desktop.pinAdapterOnEnable(s.settings)
        assertEquals("GOOGLE_DRIVE", s.strings["sync_adapter"])
    }

    @Test fun storedChoiceIsNeverTouched() {
        val s = Store(mapOf("sync_adapter" to "WEBDAV"), mapOf("sync_enable_bookmarks" to true))
        ios.pinLegacyAdapterIfInUse(s.settings)
        ios.pinAdapterOnEnable(s.settings)
        assertEquals("WEBDAV", s.strings["sync_adapter"])
    }

    @Test fun secondRunIsANoOp() {
        val s = Store(bools = mapOf("sync_enable_bookmarks" to true))
        ios.pinLegacyAdapterIfInUse(s.settings)
        val after = s.strings.toMap()
        ios.pinLegacyAdapterIfInUse(s.settings)
        assertEquals(after, s.strings)
    }
}
