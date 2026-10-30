package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedcore.platform.IosAppEnvironment
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform

class IosWelcomeHostPlatformTest {
    @Test fun downloadRefusalUsesDeviceSpaceAndTranslatedAndroidMessageWithoutInitialising() {
        val events = mutableListOf<String>()
        val table = hostStringTable("fi")
        val device = IosAppEnvironment(InMemorySecretStore(), isDiscrete = { false })
        val environment = object : AppEnvironment by device {
            override val megabytesFree = 49L
        }
        val app = koinApplication {
            modules(module {
                single<AppEnvironment> { environment }
                single<CoreStrings> { KeyedCoreStrings(table) }
                single<UserNotifier> { object : UserNotifier {
                    override fun showInfo(message: String, long: Boolean) { error("unexpected info") }
                    override fun showError(message: String, cause: Throwable?) { events += message }
                } }
            })
        }
        try {
            val platform = HostWelcomeHostPlatform(app.koin, CoroutineScope(Dispatchers.Unconfined), table,
                initialise = { events += "initialise" }, navigate = { events += it }, versionName = ::iosBundleVersionName)
            platform.onDownload()
            assertEquals(listOf(table.text("storage_space_warning")), events)
        } finally {
            app.close()
            device.close()
        }
    }

    @Test fun linksUseExternalLinksAndDoNotRemainPending() {
        val urls = mutableListOf<String>()
        val table = hostStringTable("en")
        val app = koinApplication { modules(module {
            single<ExternalLinks> { object : ExternalLinks {
                override val askBeforeOpening = false
                override fun open(url: String) { urls += url }
            } }
        }) }
        try {
            val platform = HostWelcomeHostPlatform(app.koin, CoroutineScope(Dispatchers.Unconfined), table, {}, {}, ::iosBundleVersionName)
            platform.onOpenHomepage()
            platform.onOpenGithub()
            assertEquals(listOf(table.text("homepage"), table.text("github_page")), urls)
        } finally { app.close() }
    }
}
