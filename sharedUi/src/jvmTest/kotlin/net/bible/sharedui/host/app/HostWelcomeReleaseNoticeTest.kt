package net.bible.sharedui.host.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.MemoryAppSettings
import net.bible.sharedui.app.notice.ReleaseNotice
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform
import org.koin.dsl.koinApplication
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostWelcomeReleaseNoticeTest {
    @Test
    fun welcomeControllerSilentlyDismissesStableNoticeOnIosAndDesktop() {
        for ((beta, discrete) in listOf(false to false, true to false, false to true)) {
            val settings = MemoryAppSettings()
            val environment = Proxy.newProxyInstance(
                AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "isBeta" -> beta
                    "isDiscrete" -> discrete
                    else -> error("Unexpected environment access: ${method.name}")
                }
            } as AppEnvironment
            val app = koinApplication {
                modules(org.koin.dsl.module {
                    single<AppSettings> { settings }
                    single<AppEnvironment> { environment }
                })
            }
            // Controller construction is synchronous; prevent its unrelated DB refresh from starting.
            val job = Job().apply { cancel() }
            try {
                val welcome = HostWelcomeHostPlatform(
                    app.koin, CoroutineScope(job), initialise = {}, navigate = {}, versionName = { "6.0.7" },
                )
                assertTrue(settings.values.isEmpty(), "constructing the host must not dismiss anything")
                welcome.controller
                assertEquals(if (beta || discrete) "" else "6.0", settings.getString(ReleaseNotice.DISPLAYED_KEY, ""))
                assertEquals("", settings.getString("stable-notice-beta-ran", ""))
            } finally {
                app.close()
            }
        }
    }
}
