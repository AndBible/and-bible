package net.bible.sharedui.desktop.update

import java.nio.file.Files
import kotlinx.coroutines.cancel
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.desktop.assembly.desktopMiscModule
import net.bible.sharedui.settings.DesktopUpdateCheckSetting
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopUpdateBindingTest {
    @Test fun eligiblePackageResolvesUpdaterAndDevDoesNotBindIt() {
        val keys = listOf("compose.application.resources.dir", "andbible.releaseKind", "andbible.version", "jpackage.app-path")
        val previous = keys.associateWith(System::getProperty)
        val root = Files.createTempDirectory("update-binding")
        val paths = DesktopPaths({ null }, "Linux", root)
        val prefs = DesktopDeviceSettings(root.resolve("settings"))
        val secrets = object : SecretStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) = Unit
            override fun remove(key: String) = Unit
        }
        val scope = AppCoroutineScope()
        val dependencies = module {
            single { prefs }
            single { scope }
            single<AppEnvironment> { DesktopAppEnvironment(paths, secrets, prefs, initialized = { false }) }
            single { AppDialogController() }
            single { HostStringTableHolder(hostStringTable("en")) }
            single<ExternalLinks> { object : ExternalLinks {
                override val askBeforeOpening = false
                override fun open(url: String) = Unit
            } }
        }
        try {
            System.setProperty("compose.application.resources.dir", root.toString())
            System.setProperty("andbible.version", "6.0.1230")
            System.clearProperty("jpackage.app-path")
            System.setProperty("andbible.releaseKind", "test")
            val eligible = koinApplication { modules(dependencies, desktopMiscModule()) }
            try { assertNotNull(eligible.koin.get<DesktopUpdateCheckSetting>()) } finally { eligible.close() }
            System.setProperty("andbible.releaseKind", "dev")
            val dev = koinApplication { modules(desktopMiscModule()) }
            try { assertNull(dev.koin.getOrNull<DesktopUpdateCheckSetting>()) } finally { dev.close() }
        } finally {
            scope.cancel()
            previous.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            root.toFile().deleteRecursively()
        }
    }
}
