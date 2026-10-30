package net.bible.sharedui.desktop.update

import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.strings.keyed.StringTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopUpdatePromptsTest {
    @Test fun announcementAcknowledgesOnlyExplicitChoices() = runTest {
        val root = Files.createTempDirectory("update-ack")
        val prefs = DesktopDeviceSettings(root.resolve("settings")).apply { putString("desktop_update_check", "yes") }
        val secrets = object : SecretStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = Unit
            override fun remove(key: String) = Unit
        }
        val env = DesktopAppEnvironment(DesktopPaths({ null }, "Linux", root), secrets, prefs)
        val dialogs = AppDialogController()
        val table = StringTable.of("en", { it }, { _, _ -> "" }, { emptyList() })
        val opened = mutableListOf<String>()
        var now = 100000000L
        var build = 1240
        val links = object : ExternalLinks {
            override val askBeforeOpening = false
            override fun open(url: String) { opened += url }
        }
        fun start() = DesktopUpdatePrompts(prefs, backgroundScope, env, "6.0.1230", 1230, true, dialogs,
            HostStringTableHolder(table), links, { true }, { now }, {
                """[{"tag_name":"production-$build","draft":false,"html_url":"https://example.com/$build","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""
            })
        try {
            val first = start(); runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val options = (shown.request as AppDialogRequest.Localized).resolve() as AppDialogRequest.Options
            assertEquals(listOf("download", "later"), options.options.map { it.value })
            dialogs.cancelAll(); runCurrent()
            assertEquals(0, prefs.getInt("desktop_update_announced_build", 0))
            first.close()

            now += 86400000L
            val second = start(); runCurrent()
            dialogs.respond(assertNotNull(dialogs.pending.value).id, AppDialogResult.Selected("later")); runCurrent()
            assertEquals(1240, prefs.getInt("desktop_update_announced_build", 0))
            assertEquals(emptyList(), opened)
            second.close()

            now += 86400000L; build = 1241
            val third = start(); runCurrent()
            dialogs.respond(assertNotNull(dialogs.pending.value).id, AppDialogResult.Selected("download")); runCurrent()
            assertEquals(1241, prefs.getInt("desktop_update_announced_build", 0))
            assertEquals(listOf("https://example.com/1241"), opened)
            third.close()
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun readinessLocaleAndCancellationFollowLifetime() = runTest {
        val root = Files.createTempDirectory("update-prompt")
        val prefs = DesktopDeviceSettings(root.resolve("settings"))
        val secrets = object : SecretStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = Unit
            override fun remove(key: String) = Unit
        }
        val env = DesktopAppEnvironment(DesktopPaths({ null }, "Linux", root), secrets, prefs)
        val dialogs = AppDialogController()
        var language = "first"
        val table = StringTable.of("en", { "$language:$it" }, { _, _ -> "" }, { emptyList() })
        var ready = false
        val prompts = DesktopUpdatePrompts(prefs, backgroundScope, env, "6.0.1230", 1230, true, dialogs,
            HostStringTableHolder(table), object : ExternalLinks {
                override val askBeforeOpening = false
                override fun open(url: String) = Unit
            }, { ready })
        try {
            runCurrent(); assertNull(dialogs.pending.value)
            ready = true
            testScheduler.advanceTimeBy(500); runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val localized = shown.request as AppDialogRequest.Localized
            assertEquals("first:desktop_update_consent_title", localized.resolve().title)
            language = "second"
            assertEquals("second:desktop_update_consent_title", localized.resolve().title)
            dialogs.cancelAll(); runCurrent()
            assertNull(prefs.getString("desktop_update_check"))
            assertNull(dialogs.pending.value)
        } finally {
            prompts.close()
            root.toFile().deleteRecursively()
        }
    }
}
