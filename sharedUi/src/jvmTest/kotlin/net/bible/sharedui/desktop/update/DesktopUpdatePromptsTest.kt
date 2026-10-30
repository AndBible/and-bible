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
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.strings.keyed.StringTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopUpdatePromptsTest {
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
