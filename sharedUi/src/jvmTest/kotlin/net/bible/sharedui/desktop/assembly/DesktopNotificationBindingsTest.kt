package net.bible.sharedui.desktop.assembly

import androidx.compose.ui.unit.IntSize
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.control.activity.NativeNotificationPolicy
import net.bible.android.control.activity.SystemNotificationsPreference
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier
import net.bible.sharedcore.platform.desktop.dbus.linuxNativeNotifier
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.Test
import net.bible.sharedui.MemoryAppSettings
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The notification layer of the desktop graph (#4012): bindings, foreground tracking, click activation, shutdown. */
class DesktopNotificationBindingsTest {
    private class FakeWindow(var focused: Boolean = false) : DesktopWindowHandle {
        val log = CopyOnWriteArrayList<String>()
        override val sizeDp = IntSize(1200, 800)
        override val density = 1f
        override val isFocusedAndVisible get() = focused
        override fun toFront() { log += "toFront" }
    }

    private class RecordingNotifier : NativeNotifier, AutoCloseable {
        override val supportsProgress = true
        val shows = CopyOnWriteArrayList<NativeNotification>()

        /** Every call in order: `show:<id>`, `remove:<id>`, `close`. */
        val events = CopyOnWriteArrayList<String>()
        @Volatile var handler: ((String) -> Unit)? = null
        @Volatile var closed = false
        override fun show(n: NativeNotification) { shows += n; events += "show:${n.id}" }
        override fun remove(id: String) { events += "remove:$id" }
        override fun setOnActivated(handler: (id: String) -> Unit) { this.handler = handler }
        override fun close() { closed = true; events += "close" }
    }

    private val strings: CoreStrings =
        Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, m, _ ->
            if (m.returnType == String::class.java) m.name else error("unexpected ${m.name}")
        } as CoreStrings

    private class Rig(val koin: Koin, val window: FakeWindow, val events: DesktopLifecycleEvents, val center: ActivityCenter, val notifier: RecordingNotifier, val exitTasks: DesktopExitTasks)

    private var now = 0L

    /** The policy's own clock (decoupled from ActivityCenter's `now`) and how often the policy has read it. */
    @Volatile private var policyNow = 0L
    private val policyClockReads = java.util.concurrent.atomic.AtomicInteger()

    private fun <T> withRig(
        notifier: RecordingNotifier = RecordingNotifier(),
        dbus: DesktopDbus? = null,
        block: (Rig) -> T,
    ): T {
        val window = FakeWindow()
        val events = DesktopLifecycleEvents()
        val exitTasks = DesktopExitTasks()
        val app = koinApplication {
            modules(
                module {
                    single<AppSettings> { MemoryAppSettings() }
                    single<CoreStrings> { strings }
                    single { events }
                    single { exitTasks }
                    single { ActivityListRequests() }
                    single { ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { now } }
                },
                desktopNotificationModule(
                    window, source = DesktopNotifierSource(true) { scope -> if (dbus != null) linuxNativeNotifier(dbus, scope = scope) else notifier },
                    runOnUi = { it() }, clock = { policyClockReads.incrementAndGet(); policyNow },
                ),
            )
        }
        try {
            return block(Rig(app.koin, window, events, app.koin.get(), notifier, exitTasks))
        } finally {
            app.close()
        }
    }

    private fun await(what: String, cond: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!cond()) {
            check(System.nanoTime() < deadline) { "timed out: $what" }
            Thread.sleep(10)
        }
    }

    @Test fun desktopBindsPolicyNotifierAndPreference() {
        val unsupported = object : DesktopDbus("Windows 11") {}
        withRig(dbus = unsupported) { rig ->
            assertIs<NoOpNativeNotifier>(rig.koin.get<NativeNotifier>())
            assertTrue(rig.koin.get<SystemNotificationsPreference>().enabled.value, "desktop default is on")
            assertIs<NativeNotificationPolicy>(rig.koin.get<NativeNotificationPolicy>())
            assertEquals(null, (rig.koin.get<AppSettings>() as MemoryAppSettings).values["Boolean:system_notifications"], "default never persisted")
        }
    }

    @Test fun sourceAvailableUsesItsNotifier() = withRig { rig ->
        kotlin.test.assertSame(rig.notifier, rig.koin.get<NativeNotifier>())
        assertTrue(rig.koin.get<SystemNotificationsPreference>().enabled.value)
    }

    @Test fun sourceUnavailableBindsNothing() {
        val app = koinApplication {
            modules(module { single<AppSettings> { MemoryAppSettings() } }, desktopNotificationModule(FakeWindow(), source = DesktopNotifierSource(false) { error("unavailable source called") }))
        }
        try {
            assertEquals(null, app.koin.getOrNull<SystemNotificationsPreference>(), "settings row stays hidden")
            assertEquals(null, app.koin.getOrNull<NativeNotificationPolicy>())
            assertEquals(null, app.koin.getOrNull<NativeNotifier>())
        } finally {
            app.close()
        }
    }

    @Test fun activationRaisesWindowAndRequestsList() {
        val window = FakeWindow()
        val requests = ActivityListRequests()
        val notifier = RecordingNotifier()
        val ui = mutableListOf<() -> Unit>()
        installNotificationActivation(notifier, window, requests) { ui += it }

        val got = CoroutineScope(Dispatchers.Unconfined).launch { requests.requests.first() }
        notifier.handler!!("andbible-dl")
        assertEquals(emptyList(), window.log, "nothing happens on the notifier's thread")
        assertTrue(got.isActive)
        ui.single()()
        assertEquals(listOf("toFront"), window.log)
        assertTrue(got.isCompleted, "the list was requested after the window was raised")
    }

    @Test fun foregroundEventsReachThePolicy() = withRig { rig ->
        rig.koin.get<NativeNotificationPolicy>().start()
        rig.events.emit(DesktopLifecycleEvent.ResignActive)
        rig.center.start("a", ActivityKind.DOWNLOAD, "A")
        now = 20_000
        rig.center.succeed("a")
        await("background finish notifies") { "show:andbible-a" in rig.notifier.events }

        rig.events.emit(DesktopLifecycleEvent.Active)
        rig.center.start("b", ActivityKind.DOWNLOAD, "B")
        now = 50_000
        rig.center.succeed("b")
        // A later failure always notifies, so once it shows, the foreground success has been evaluated (and was silent).
        rig.center.start("c", ActivityKind.DOWNLOAD, "C")
        rig.center.fail("c", "boom")
        await("barrier failure shows") { "show:andbible-c" in rig.notifier.events }
        assertTrue("show:andbible-b" !in rig.notifier.events, "foreground finish is silent")
    }

    @Test fun initialForegroundStateComesFromTheWindow() = withRig { rig ->
        rig.window.focused = true // focused at start(), no lifecycle event has arrived
        rig.koin.get<NativeNotificationPolicy>().start()
        rig.center.start("b", ActivityKind.DOWNLOAD, "B")
        now = 30_000
        rig.center.succeed("b")
        rig.center.start("c", ActivityKind.DOWNLOAD, "C")
        rig.center.fail("c", "boom")
        await("barrier failure shows") { "show:andbible-c" in rig.notifier.events }
        assertTrue("show:andbible-b" !in rig.notifier.events, "a long finish while focused is silent")
    }

    @Test fun exitTasksRemoveProgressThenCloseNotifierAndNothingShowsAfter() {
        val notifier = RecordingNotifier()
        withRig(notifier) { rig ->
            rig.koin.get<NativeNotificationPolicy>().start()
            rig.events.emit(DesktopLifecycleEvent.ResignActive)
            rig.center.start("a", ActivityKind.DOWNLOAD, "A")
            now = 2_000 // ActivityCenter hides a running item for its first second
            policyNow = 2_000
            val readsBefore = policyClockReads.get()
            rig.center.update("a", 40) // first seen running by the policy at policyNow = 2_000
            // Evaluating a RUNNING item that is not yet due reads the clock twice (now, then the wake-up delay).
            await("policy saw the running item") { policyClockReads.get() >= readsBefore + 2 }
            now = 13_000
            policyNow = 13_000
            rig.center.update("a", 41)
            await("progress shown") { "show:andbible-a" in notifier.events }

            // What persistBeforeExit does: Koin stays open, only the registered exit tasks run.
            rig.events.emit(DesktopLifecycleEvent.Background)
            assertTrue(runBlocking { rig.exitTasks.runAll(5_000) })
            assertEquals(listOf("show:andbible-a", "remove:andbible-a", "close"), notifier.events.toList())

            now = 16_000
            rig.center.update("a", 70)
            rig.center.start("z", ActivityKind.DOWNLOAD, "Z")
            rig.center.fail("z", "late")
            Thread.sleep(200) // nothing to wait for: the policy is closed, so only absence can be asserted
            assertEquals(listOf("show:andbible-a", "remove:andbible-a", "close"), notifier.events.toList(), "no call after shutdown")
        }
        assertEquals(3, notifier.events.size, "closing the graph afterwards does not shut down twice")
    }
}
