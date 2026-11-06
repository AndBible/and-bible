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
        val removes = CopyOnWriteArrayList<String>()
        @Volatile var handler: ((String) -> Unit)? = null
        @Volatile var closed = false
        override fun show(n: NativeNotification) { shows += n }
        override fun remove(id: String) { removes += id }
        override fun setOnActivated(handler: (id: String) -> Unit) { this.handler = handler }
        override fun close() { closed = true }
    }

    private class MemorySettings : AppSettings {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = values["String:$key"] as? String ?: default
        override fun getLong(key: String, default: Long) = values["Long:$key"] as? Long ?: default
        override fun getInt(key: String, default: Int) = values["Int:$key"] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = values["Boolean:$key"] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = values["Double:$key"] as? Double ?: default
        override fun getFloat(key: String, default: Float) = values["Float:$key"] as? Float ?: default
        override fun setString(key: String, value: String?) { values["String:$key"] = value }
        override fun setLong(key: String, value: Long?) { values["Long:$key"] = value }
        override fun setInt(key: String, value: Int?) { values["Int:$key"] = value }
        override fun setBoolean(key: String, value: Boolean?) { values["Boolean:$key"] = value }
        override fun setDouble(key: String, value: Double?) { values["Double:$key"] = value }
        override fun setFloat(key: String, value: Float?) { values["Float:$key"] = value }
    }

    private val strings: CoreStrings =
        Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, m, _ ->
            if (m.returnType == String::class.java) m.name else error("unexpected ${m.name}")
        } as CoreStrings

    private class Rig(val koin: Koin, val window: FakeWindow, val events: DesktopLifecycleEvents, val center: ActivityCenter, val notifier: RecordingNotifier)

    private var now = 0L

    private fun <T> withRig(
        notifier: RecordingNotifier = RecordingNotifier(),
        dbus: DesktopDbus? = null,
        block: (Rig) -> T,
    ): T {
        val window = FakeWindow()
        val events = DesktopLifecycleEvents()
        val app = koinApplication {
            modules(
                module {
                    single<AppSettings> { MemorySettings() }
                    single<CoreStrings> { strings }
                    single { events }
                    single { ActivityListRequests() }
                    single { ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { now } }
                },
                desktopNotificationModule(
                    window, runOnUi = { it() },
                    notifierFactory = { scope -> if (dbus != null) linuxNativeNotifier(dbus, scope = scope) else notifier },
                ),
            )
        }
        try {
            return block(Rig(app.koin, window, events, app.koin.get(), notifier))
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
            assertEquals(null, (rig.koin.get<AppSettings>() as MemorySettings).values["Boolean:system_notifications"], "default never persisted")
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

    @Test fun foregroundEventsReachThePolicyAndStateStartsFromTheWindow() = withRig { rig ->
        rig.window.focused = true
        rig.koin.get<NativeNotificationPolicy>().start()
        rig.events.emit(DesktopLifecycleEvent.ResignActive)
        rig.center.start("a", ActivityKind.DOWNLOAD, "A")
        now = 20_000
        rig.center.succeed("a")
        await("background finish notifies") { rig.notifier.shows.size == 1 }

        rig.events.emit(DesktopLifecycleEvent.Active)
        now = 20_000
        rig.center.start("b", ActivityKind.DOWNLOAD, "B")
        now = 50_000
        rig.center.succeed("b")
        Thread.sleep(300)
        assertEquals(1, rig.notifier.shows.size, "foreground finish is silent")
    }

    @Test fun closingTheGraphClosesPolicyThenNotifier() {
        val notifier = RecordingNotifier()
        withRig(notifier) { rig ->
            rig.events.emit(DesktopLifecycleEvent.ResignActive)
            rig.koin.get<NativeNotificationPolicy>().start()
            rig.center.start("a", ActivityKind.DOWNLOAD, "A")
            now = 15_000
            rig.center.update("a", 40)
            await("progress shown") { notifier.shows.isNotEmpty() }
        }
        assertEquals(listOf("andbible-a"), notifier.removes, "policy removed its progress notification on close")
        assertTrue(notifier.closed)
    }
}
