package net.bible.sharedui.ios.ports

import org.jetbrains.skia.Surface
import org.jetbrains.skia.EncodedImageFormat
import okio.Path.Companion.toPath
import kotlin.test.*
import kotlinx.coroutines.CancellationException

class IosTextDisplayPlatformTest {
    @Test fun thumbnailDecodesAJpeg() {
        val surface = Surface.makeRasterN32Premul(1920, 1080)
        surface.canvas.clear(0xff608030.toInt())
        val bytes = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG)!!.bytes
        val port = IosTextDisplayPlatform({ error("not used") },
            { mapOf("Photo" to "/module/photo.jpg".toPath()) }, { bytes })
        val image = assertNotNull(port.thumbnail("Photo"))
        assertTrue(image.width in 1..240 && image.height in 1..240)
        assertNull(port.thumbnail("/module/photo.jpg"), "paths are not module tokens")
        surface.close()
    }

    private class MapSettings : net.bible.sharedcore.platform.AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = (m[key] as String?) ?: default
        override fun getLong(key: String, default: Long) = (m[key] as Long?) ?: default
        override fun getInt(key: String, default: Int) = (m[key] as Int?) ?: default
        override fun getBoolean(key: String, default: Boolean) = (m[key] as Boolean?) ?: default
        override fun getDouble(key: String, default: Double) = (m[key] as Double?) ?: default
        override fun getFloat(key: String, default: Float) = (m[key] as Float?) ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value }
    }

    @Test fun detachedRouteReturnsRealEditsAndResetWithoutSharingBundleState() {
        val table = net.bible.sharedui.assembly.hostStringTable("en")
        val source = object : net.bible.service.sword.ContentSource {
            override fun openSource(uri: String): okio.Source? = null
            override fun displayName(uri: String): String? = null
            override fun mimeType(uri: String): String? = null
        }
        org.koin.core.context.startKoin { modules(org.koin.dsl.module {
            single<net.bible.sharedlogic.settings.TextDisplayPrefItems> {
                net.bible.sharedui.strings.keyed.SharedTextDisplayPrefItems(
                    strings = { table }, contentSource = { source },
                    windowControl = { error("a detached bundle has no window") },
                    documentControl = { error("a detached bundle needs no document") },
                    settings = { MapSettings() },
                )
            }
        }) }
        try {
            val platform = IosTextDisplayPlatform({ net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl() })
            val args = net.bible.sharedcore.nav.TextDisplaySettingsArgs(
                settingsBundleJson = net.bible.android.database.SettingsBundle(workspaceName = "Detached").toJson())
            val first = platform.entryFor(args)
            val second = platform.entryFor(args)
            assertNull(first.resultOnLeave())
            first.service.setValue(first.scope, net.bible.sharedcore.settings.TextSettingType.JUSTIFY,
                net.bible.sharedcore.settings.TextSettingValue.BoolValue(false))
            val result = assertNotNull(first.resultOnLeave())
            assertEquals(false, net.bible.android.database.SettingsBundle.fromJson(result.settingsBundleJson).workspaceSettings.justifyText)
            assertNull(second.resultOnLeave())
            first.service.reset(first.scope)
            assertTrue(assertNotNull(first.resultOnLeave()).reset)
        } finally { org.koin.core.context.stopKoin() }
    }

    @Test fun invalidImageIsNullButCancellationEscapes() {
        val inventory = { mapOf("Photo" to "/photo".toPath()) }
        assertNull(IosTextDisplayPlatform({ error("not used") }, inventory, { byteArrayOf(1, 2) }).thumbnail("Photo"))
        assertFailsWith<CancellationException> {
            IosTextDisplayPlatform({ error("not used") }, inventory, { throw CancellationException("stop") }).thumbnail("Photo")
        }
    }

    private fun jpeg(): ByteArray {
        val surface = Surface.makeRasterN32Premul(64, 64)
        surface.canvas.clear(0xff203040.toInt())
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG)!!.bytes.also { surface.close() }
    }

    @Test fun thumbnailCacheIsClearedWhenModulesChangeAndOnMemoryWarning() {
        val bytes = jpeg()
        var inventory = mapOf<String, okio.Path>()
        var reads = 0
        var modulesChanged: (() -> Unit)? = null
        var memoryWarning: (() -> Unit)? = null
        val port = IosTextDisplayPlatform({ error("not used") }, { inventory }, { reads++; bytes },
            { modulesChanged = it }, { memoryWarning = it })
        // Negative entry: the module is not installed yet.
        assertNull(port.thumbnail("Photo"))
        inventory = mapOf("Photo" to "/module/photo.jpg".toPath())
        assertNull(port.thumbnail("Photo"), "the miss is cached until the catalog reports a change")
        assertEquals(0, reads)
        // Install (or any module change) clears the negative entry.
        assertNotNull(modulesChanged).invoke()
        assertNotNull(port.thumbnail("Photo"))
        assertEquals(1, reads)
        port.thumbnail("Photo")
        assertEquals(1, reads, "a decoded entry is served from the cache")
        // Delete: the stale decoded entry must go and the lookup must see the missing module.
        inventory = mapOf()
        assertNotNull(port.thumbnail("Photo"), "still cached before the change notification")
        assertNotNull(modulesChanged).invoke()
        assertNull(port.thumbnail("Photo"))
        // Low-memory warning drops decoded entries too.
        inventory = mapOf("Photo" to "/module/photo.jpg".toPath())
        assertNotNull(memoryWarning).invoke()
        assertNotNull(port.thumbnail("Photo"))
        assertEquals(2, reads)
        assertNotNull(memoryWarning).invoke()
        assertNotNull(port.thumbnail("Photo"))
        assertEquals(3, reads)
    }
}
