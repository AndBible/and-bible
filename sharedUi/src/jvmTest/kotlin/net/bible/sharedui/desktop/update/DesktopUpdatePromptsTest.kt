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
import net.bible.sharedui.settings.DesktopUpdateInterval

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopUpdatePromptsTest {
    @Test fun announcementAcknowledgesOnlyExplicitChoices() = runTest {
        val root = Files.createTempDirectory("update-ack")
        val prefs = DesktopDeviceSettings(root.resolve("settings")).apply { putString("desktop_update_check", "daily") }
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
        fun start() = DesktopUpdatePrompts(prefs, backgroundScope, env, "6.0.1230", 1230, DesktopChannel.PRODUCTION, dialogs,
            HostStringTableHolder(table), links, { true }, { now }, {
                """[{"tag_name":"production-$build","draft":false,"html_url":"https://example.com/$build","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""
            })
        try {
            val first = start(); runCurrent()
            val shown = assertNotNull(dialogs.pending.value)
            val options = (shown.request as AppDialogRequest.Localized).resolve() as AppDialogRequest.Options
            assertEquals(listOf("download", "whats_new", "snooze_day", "snooze_week", "snooze_month", "skip"), options.options.map { it.value })
            assertEquals("desktop_update_dont_notify", options.checkboxLabel)
            kotlin.test.assertTrue(options.message.orEmpty().contains("desktop_update_reenable"))
            dialogs.cancelAll(); runCurrent()
            assertEquals(0, prefs.getInt("desktop_update_announced_build", 0))
            first.close()

            now += 86400000L
            val second = start(); runCurrent()
            dialogs.respond(assertNotNull(dialogs.pending.value).id, AppDialogResult.SelectedWithCheckbox("snooze_day", false)); runCurrent()
            assertEquals(0, prefs.getInt("desktop_update_announced_build", 0))
            assertEquals(emptyList(), opened)
            assertEquals("daily", prefs.getString(CONSENT_KEY))

            now += 86399999L
            testScheduler.advanceTimeBy(86399999L); runCurrent()
            assertNull(dialogs.pending.value)
            now += 1L
            testScheduler.advanceTimeBy(1L); runCurrent()
            assertNotNull(dialogs.pending.value)
            val third = second
            dialogs.respond(assertNotNull(dialogs.pending.value).id, AppDialogResult.SelectedWithCheckbox("download", false)); runCurrent()
            assertEquals(1240, prefs.getInt("desktop_update_announced_build", 0))
            assertEquals(listOf("https://example.com/1240"), opened)
            third.close()

            now += 86400000L; build = 1241
            val fourth = start(); runCurrent()
            dialogs.respond(assertNotNull(dialogs.pending.value).id, AppDialogResult.SelectedWithCheckbox("download", false)); runCurrent()
            assertEquals(1241, prefs.getInt("desktop_update_announced_build", 0))
            assertEquals(listOf("https://example.com/1240", "https://example.com/1241"), opened)
            fourth.close()
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun whatsNewOpensOfferedVersionsNotesWithoutAcknowledgingOrSnoozing() = runTest {
        // The offered version, not the installed one or the build-only tag, selects the page.
        for ((name, expectedUrl) in listOf(
            "Release 6.0.1240" to "https://andbible.org/docs/releases/release_6_0/",
            "Beta release 6.0.1240-beta" to "https://andbible.org/docs/releases/release_6_0/",
            "Test release 6.0.1240-test" to "https://andbible.org/docs/releases/release_6_0/",
            "Test release 7.0.1240-test" to "https://andbible.org/docs/desktop/#update-checks",
            "Release 6.0.invalid" to "https://andbible.org/docs/desktop/#update-checks",
            "" to "https://andbible.org/docs/desktop/#update-checks",
        )) {
            for (followUp in listOf("download", "snooze_day", "snooze_week", "snooze_month", "skip", "cancel")) {
                val root = Files.createTempDirectory("update-notes")
                val prefs = DesktopDeviceSettings(root.resolve("settings")).apply { putString(CONSENT_KEY, "daily") }
                val env = DesktopAppEnvironment(DesktopPaths({ null }, "Linux", root), object : SecretStore {
                    override fun get(key: String): String? = null
                    override fun put(key: String, value: String) = Unit
                    override fun remove(key: String) = Unit
                }, prefs)
                val dialogs = AppDialogController()
                val opened = mutableListOf<String>()
                val prompts = DesktopUpdatePrompts(prefs, backgroundScope, env, "5.0.1230", 1230,
                    DesktopChannel.PRODUCTION, dialogs,
                    HostStringTableHolder(StringTable.of("en", { it }, { _, _ -> "" }, { emptyList() })),
                    object : ExternalLinks {
                        override val askBeforeOpening = false
                        override fun open(url: String) { opened += url }
                    }, { true }, { 100000000L }, {
                        """[{"tag_name":"production-1240","name":"$name","draft":false,"html_url":"https://example.com/1240","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""
                    })
                try {
                    runCurrent()
                    val pending = assertNotNull(dialogs.pending.value)
                    dialogs.respond(pending.id, AppDialogResult.SelectedWithCheckbox("whats_new", false))
                    runCurrent()
                    assertEquals(listOf(expectedUrl), opened, name)
                    assertEquals(0, prefs.getInt("desktop_update_announced_build", 0), name)
                    assertEquals(0L, prefs.getLong("desktop_update_snooze_until", 0L), name)
                    assertEquals("daily", prefs.getString(CONSENT_KEY), name)
                    val reopened = assertNotNull(dialogs.pending.value, "Offer must remain after reading notes")
                    assertEquals((pending.request as AppDialogRequest.Localized).resolve(),
                        (reopened.request as AppDialogRequest.Localized).resolve())
                    dialogs.respond(reopened.id, AppDialogResult.Selected("whats_new"))
                    runCurrent()
                    assertEquals(listOf(expectedUrl, expectedUrl), opened)
                    val offer = assertNotNull(dialogs.pending.value, "Repeated notes must keep the offer")
                    if (followUp == "cancel") dialogs.cancelAll()
                    else dialogs.respond(offer.id, AppDialogResult.SelectedWithCheckbox(followUp, false))
                    runCurrent()
                    assertNull(dialogs.pending.value)
                    assertEquals(if (followUp in listOf("download", "skip")) 1240 else 0,
                        prefs.getInt("desktop_update_announced_build", 0), followUp)
                    val days = when (followUp) { "snooze_day" -> 1; "snooze_week" -> 7; "snooze_month" -> 30; else -> 0 }
                    assertEquals(if (days > 0) 100000000L + days * 86400000L else 0L,
                        prefs.getLong("desktop_update_snooze_until", 0L), followUp)
                    assertEquals(listOf(expectedUrl, expectedUrl) +
                        (if (followUp == "download") listOf("https://example.com/1240") else emptyList()), opened)
                } finally {
                    prompts.close()
                    root.toFile().deleteRecursively()
                }
            }
        }
    }

    @Test fun offerDisplaysMarketingVersionOrFallsBackToTag() = runTest {
        for ((name, expected) in listOf("Release 6.0.1240" to "6.0.1240", "" to "production-1240")) {
            val root = Files.createTempDirectory("update-version")
            val prefs = DesktopDeviceSettings(root.resolve("settings")).apply { putString(CONSENT_KEY, "daily") }
            val env = DesktopAppEnvironment(DesktopPaths({ null }, "Linux", root), object : SecretStore {
                override fun get(key: String): String? = null
                override fun put(key: String, value: String) = Unit
                override fun remove(key: String) = Unit
            }, prefs)
            val dialogs = AppDialogController()
            val table = StringTable.of("en", { if (it == "desktop_update_available_message") "%1\$s|%2\$s" else it },
                { _, _ -> "" }, { emptyList() })
            val prompts = DesktopUpdatePrompts(prefs, backgroundScope, env, "5.0.1230", 1230,
                DesktopChannel.PRODUCTION, dialogs, HostStringTableHolder(table), object : ExternalLinks {
                    override val askBeforeOpening = false
                    override fun open(url: String) = Unit
                }, { true }, { 100000000L }, {
                    """[{"tag_name":"production-1240","name":"$name","draft":false,"html_url":"https://example.com/1240","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""
                })
            try {
                runCurrent()
                val request = assertNotNull(dialogs.pending.value).request as AppDialogRequest.Localized
                assertEquals("$expected|5.0.1230<br><br>desktop_update_reenable",
                    (request.resolve() as AppDialogRequest.Options).message)
            } finally {
                prompts.close()
                root.toFile().deleteRecursively()
            }
        }
    }

    @Test fun snoozeChoicesDelayTheNextAnnouncement() = runTest {
        for ((choice, days) in listOf("snooze_day" to 1, "snooze_week" to 7, "snooze_month" to 30)) {
            val root = Files.createTempDirectory("update-snooze")
            val prefs = DesktopDeviceSettings(root.resolve("settings")).apply { putString("desktop_update_check", "daily") }
            val env = DesktopAppEnvironment(DesktopPaths({ null }, "Linux", root), object : SecretStore {
                override fun get(key: String): String? = null
                override fun put(key: String, value: String) = Unit
                override fun remove(key: String) = Unit
            }, prefs)
            val dialogs = AppDialogController()
            var now = 100000000L
            var checks = 0
            val prompts = DesktopUpdatePrompts(prefs, backgroundScope, env, "6.0.1230", 1230, DesktopChannel.PRODUCTION, dialogs,
                HostStringTableHolder(StringTable.of("en", { it }, { _, _ -> "" }, { emptyList() })), object : ExternalLinks {
                    override val askBeforeOpening = false
                    override fun open(url: String) = Unit
                }, { true }, { now }, {
                    checks++
                    """[{"tag_name":"production-1240","draft":false,"html_url":"https://example.com/1240","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""
                })
            try {
                runCurrent()
                dialogs.respond(assertNotNull(dialogs.pending.value).id, AppDialogResult.SelectedWithCheckbox(choice, false)); runCurrent()
                assertEquals(0, prefs.getInt("desktop_update_announced_build", 0), choice)
                assertEquals("daily", prefs.getString("desktop_update_check"), choice)
                val delay = days * 86400000L
                now += delay - 1; testScheduler.advanceTimeBy(delay - 1); runCurrent()
                assertNull(dialogs.pending.value, choice); assertEquals(1, checks, choice)
                now += 1; testScheduler.advanceTimeBy(1); runCurrent()
                assertNotNull(dialogs.pending.value, choice)
            } finally {
                prompts.close(); root.toFile().deleteRecursively()
            }
        }
    }

    @Test fun skipThisVersionOffersOnlyANewerBuild() = runTest {
        val root = Files.createTempDirectory("update-skip")
        val prefs = DesktopDeviceSettings(root.resolve("settings")).apply { putString("desktop_update_check", "daily") }
        val env = DesktopAppEnvironment(DesktopPaths({ null }, "Linux", root), object : SecretStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = Unit
            override fun remove(key: String) = Unit
        }, prefs)
        val dialogs = AppDialogController()
        var now = 100000000L
        var build = 1240
        val prompts = DesktopUpdatePrompts(prefs, backgroundScope, env, "6.0.1230", 1230, DesktopChannel.PRODUCTION, dialogs,
            HostStringTableHolder(StringTable.of("en", { it }, { _, _ -> "" }, { emptyList() })), object : ExternalLinks {
                override val askBeforeOpening = false
                override fun open(url: String) = Unit
            }, { true }, { now }, {
                """[{"tag_name":"production-$build","draft":false,"html_url":"https://example.com/$build","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""
            })
        try {
            runCurrent()
            dialogs.respond(assertNotNull(dialogs.pending.value).id, AppDialogResult.SelectedWithCheckbox("skip", false)); runCurrent()
            assertEquals(1240, prefs.getInt("desktop_update_announced_build", 0))
            now += 86400000L; testScheduler.advanceTimeBy(86400000L); runCurrent()
            assertNull(dialogs.pending.value)
            build = 1241; now += 86400000L; testScheduler.advanceTimeBy(86400000L); runCurrent()
            assertNotNull(dialogs.pending.value)
        } finally {
            prompts.close(); root.toFile().deleteRecursively()
        }
    }

    @Test fun optingOutWithSnoozeDisablesFutureChecks() = verifyOptOut("snooze_day")
    @Test fun optingOutWithDownloadStillOpensReleaseAndDisablesFutureChecks() = verifyOptOut("download")
    @Test fun optingOutWithWhatsNewStillOpensNotesAndDisablesFutureChecks() = verifyOptOut("whats_new")

    private fun verifyOptOut(choice: String) = runTest {
        val root = Files.createTempDirectory("update-opt-out")
        val prefs = DesktopDeviceSettings(root.resolve("settings")).apply { putString("desktop_update_check", "daily") }
        val secrets = object : SecretStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = Unit
            override fun remove(key: String) = Unit
        }
        val env = DesktopAppEnvironment(DesktopPaths({ null }, "Linux", root), secrets, prefs)
        val dialogs = AppDialogController()
        var checks = 0
        val opened = mutableListOf<String>()
        var now = 100000000L
        val prompts = DesktopUpdatePrompts(prefs, backgroundScope, env, "6.0.1230", 1230, DesktopChannel.PRODUCTION, dialogs,
            HostStringTableHolder(StringTable.of("en", { it }, { _, _ -> "" }, { emptyList() })), object : ExternalLinks {
                override val askBeforeOpening = false
                override fun open(url: String) { opened += url }
            }, { true }, { now }, {
                checks++
                """[{"tag_name":"production-1240","draft":false,"html_url":"https://example.com/1240","assets":[{"name":"andbible-desktop-linux.deb"}]}]"""
            })
        try {
            runCurrent()
            val pending = assertNotNull(dialogs.pending.value)
            dialogs.respond(pending.id, AppDialogResult.SelectedWithCheckbox(choice, true)); runCurrent()
            assertEquals("never", prefs.getString("desktop_update_check"))
            assertEquals(DesktopUpdateInterval.NEVER, prompts.interval)
            assertEquals(when (choice) {
                "download" -> listOf("https://example.com/1240")
                "whats_new" -> listOf("https://andbible.org/docs/desktop/#update-checks")
                else -> emptyList()
            }, opened)
            assertEquals(if (choice == "download") 1240 else 0, prefs.getInt("desktop_update_announced_build", 0))
            if (choice == "whats_new") {
                val reopened = assertNotNull(dialogs.pending.value, "Opt-out must not remove the current offer")
                val request = (reopened.request as AppDialogRequest.Localized).resolve() as AppDialogRequest.Options
                kotlin.test.assertTrue(request.checkboxChecked, "Reopened offer must show the saved opt-out")
                dialogs.respond(reopened.id, AppDialogResult.SelectedWithCheckbox("whats_new", false)); runCurrent()
                assertEquals("daily", prefs.getString(CONSENT_KEY), "Unchecking opt-out restores the original interval")
                val restored = assertNotNull(dialogs.pending.value)
                val restoredRequest = (restored.request as AppDialogRequest.Localized).resolve() as AppDialogRequest.Options
                kotlin.test.assertFalse(restoredRequest.checkboxChecked)
                dialogs.respond(restored.id, AppDialogResult.SelectedWithCheckbox("whats_new", true)); runCurrent()
                assertEquals("never", prefs.getString(CONSENT_KEY))
                dialogs.cancelAll(); runCurrent()
            }
            now += 3 * 86400000L
            testScheduler.advanceTimeBy(3 * 86400000L); runCurrent()
            assertNull(dialogs.pending.value)
            assertEquals(1, checks)
            prompts.setInterval(DesktopUpdateInterval.DAILY); runCurrent()
            assertEquals(DesktopUpdateInterval.DAILY, prompts.interval)
            assertEquals(2, checks)
            if (choice == "snooze_day") assertNotNull(dialogs.pending.value)
        } finally {
            prompts.close()
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
        val prompts = DesktopUpdatePrompts(prefs, backgroundScope, env, "6.0.1230", 1230, DesktopChannel.PRODUCTION, dialogs,
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
