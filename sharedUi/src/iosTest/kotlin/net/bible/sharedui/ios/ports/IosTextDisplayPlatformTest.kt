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

    @Test fun detachedRouteReturnsRealEditsAndResetWithoutSharingBundleState() {
        val table = net.bible.sharedui.assembly.iosStringTable("en")
        val source = object : net.bible.service.sword.ContentSource {
            override fun openSource(uri: String): okio.Source? = null
            override fun displayName(uri: String): String? = null
            override fun mimeType(uri: String): String? = null
        }
        org.koin.core.context.startKoin { modules(org.koin.dsl.module {
            single<net.bible.sharedlogic.settings.TextDisplayPrefItems> {
                net.bible.sharedui.strings.keyed.PendingTextDisplayPrefItems({ table }, { source })
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
}
