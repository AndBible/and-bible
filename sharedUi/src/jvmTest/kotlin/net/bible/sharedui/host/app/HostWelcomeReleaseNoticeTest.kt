package net.bible.sharedui.host.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.MemoryAppSettings
import net.bible.sharedui.app.notice.ReleaseNotice
import net.bible.sharedui.app.notice.ReleaseNoticePlatform
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform
import org.koin.dsl.koinApplication
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostWelcomeReleaseNoticeTest {
    private fun environment(beta: Boolean, discrete: Boolean) = Proxy.newProxyInstance(
        AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "isBeta" -> beta
            "isDiscrete" -> discrete
            else -> error("Unexpected environment access: ${method.name}")
        }
    } as AppEnvironment

    @Test
    fun abandonedWelcomeDoesNotDismissStableNotice() = runTest {
        for (platform in listOf(ReleaseNoticePlatform.IOS, ReleaseNoticePlatform.DESKTOP)) {
            val settings = MemoryAppSettings()
            val app = koinApplication {
                modules(org.koin.dsl.module {
                    single<AppSettings> { settings }
                    single<AppEnvironment> { environment(false, false) }
                })
            }
            val job = Job().apply { cancel() }
            try {
                val welcome = HostWelcomeHostPlatform(
                    app.koin, CoroutineScope(job), initialise = {}, navigate = {}, versionName = { "6.0.7" },
                )
                welcome.controller
                assertTrue(settings.values.isEmpty(), "$platform: showing Welcome must not mark the notice")
                var asked = 0
                ReleaseNotice.showIfNeeded(platform, false, false, "6.0", settings,
                    ask = { asked++; AppDialogResult.Cancel }, onAction = {})
                assertEquals(1, asked, "$platform: a later launch must show the notice")
                assertTrue(settings.values.isEmpty())
            } finally {
                app.close()
            }
        }
    }

    @Test
    fun failedWelcomeReturnDoesNotMarkNotice() = runTest {
        for (usable in listOf(false, true)) {
            val settings = MemoryAppSettings()
            val app = koinApplication {
                modules(org.koin.dsl.module {
                    single<AppSettings> { settings }
                    single<AppEnvironment> { environment(false, false) }
                })
            }
            val job = Job().apply { cancel() }
            try {
                val welcome = HostWelcomeHostPlatform(
                    app.koin, CoroutineScope(job), initialise = {}, navigate = {}, versionName = { "6.0.7" },
                )
                welcome.controller
                var opened = false
                var refreshed = false
                var failure: String? = null
                val gate = HostWelcomeReturnGate(
                    scope = this,
                    usableBible = { usable },
                    ensureReading = { error("reading initialization failed") },
                    currentRoute = { NavRoutes.WELCOME },
                    openReading = {
                        opened = true
                        welcome.onCompleted()
                    },
                    refreshWelcome = { refreshed = true },
                    log = { failure = it },
                )
                welcome.afterRestore = gate::afterExternalFlow
                welcome.afterRestore()
                runCurrent()
                kotlin.test.assertFalse(opened)
                assertEquals(!usable, refreshed)
                assertEquals(usable, failure != null)
                assertTrue(settings.values.isEmpty(), "failed return must not dismiss the notice")
            } finally {
                app.close()
            }
        }
    }

    @Test
    fun completingWelcomeMarksBeforeReadingChecksNotice() = runTest {
        for (platform in listOf(ReleaseNoticePlatform.IOS, ReleaseNoticePlatform.DESKTOP)) {
            for ((beta, discrete) in listOf(false to false, true to false, false to true)) {
                val settings = MemoryAppSettings()
                val app = koinApplication {
                    modules(org.koin.dsl.module {
                        single<AppSettings> { settings }
                        single<AppEnvironment> { environment(beta, discrete) }
                    })
                }
                val job = Job().apply { cancel() }
                try {
                    val welcome = HostWelcomeHostPlatform(
                        app.koin, CoroutineScope(job), initialise = {}, navigate = {}, versionName = { "6.0.7" },
                    )
                    welcome.controller
                    var opened = false
                    var asked = 0
                    var failure: String? = null
                    val gate = HostWelcomeReturnGate(
                        scope = this,
                        usableBible = { true },
                        ensureReading = {},
                        currentRoute = { NavRoutes.WELCOME },
                        openReading = {
                            welcome.onCompleted()
                            assertEquals(if (beta || discrete) "" else "6.0", settings.getString(ReleaseNotice.DISPLAYED_KEY, ""))
                            launch {
                                ReleaseNotice.showIfNeeded(platform, beta, discrete, "6.0", settings,
                                    ask = { asked++; AppDialogResult.Cancel }, onAction = {})
                                opened = true
                            }
                        },
                        refreshWelcome = { error("Unexpected refresh") },
                        log = { failure = it },
                    )
                    welcome.afterRestore = gate::afterExternalFlow
                    welcome.afterRestore()
                    runCurrent()
                    assertTrue(opened, "$platform beta=$beta discrete=$discrete: $failure")
                    assertEquals(0, asked, "$platform beta=$beta discrete=$discrete")
                    if (beta || discrete) assertTrue(settings.values.isEmpty())
                } finally {
                    app.close()
                }
            }
        }
    }
}
