package net.bible.sharedui.desktop.assembly

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.control.activity.NativeNotificationPolicy
import net.bible.android.control.activity.SystemNotificationsPreference
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppForeground
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.NativeNotifier
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.dbus.linuxNativeNotifier
import net.bible.sharedlogic.util.currentTimeMillis
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private const val TAG = "DesktopNotifications"

/**
 * Native system notifications of the desktop graph (#4012): the [NativeNotifier] (D-Bus on Linux; Windows and macOS
 * follow in #4013), the [SystemNotificationsPreference] (default on), and the [NativeNotificationPolicy] that
 * `SharedAppStartup.startActivityCenter` starts. Needs [ActivityCenter], [ActivityListRequests],
 * [DesktopLifecycleEvents], `AppSettings` and [CoreStrings] from the rest of the graph.
 *
 * The policy and the notifier run on one IO scope (their D-Bus calls must never reach the AWT thread). Closing the
 * graph closes the policy first (it removes its progress notifications through the notifier) and then the notifier
 * (flushes its queue, at most about 2 s), then cancels the scope.
 *
 * Nothing is bound when [available] is false (Windows and macOS until #4013): there the notifier would be a no-op, and
 * an unbound [SystemNotificationsPreference] keeps the settings row hidden instead of showing a toggle that does nothing.
 *
 * @param available whether a real notifier exists on this OS; defaults to the D-Bus platforms
 * @param clock the policy's clock, only compared with itself (not with ActivityCenter's); a test seam
 * @param runOnUi posts to the UI thread; a notification click raises the window there
 * @param notifierFactory test seam, given the IO scope
 */
fun desktopNotificationModule(
    window: DesktopWindowHandle,
    available: Boolean = DesktopDbus().supportedOs,
    runOnUi: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
    clock: () -> Long = ::currentTimeMillis,
    notifierFactory: (CoroutineScope) -> NativeNotifier = { scope -> linuxNativeNotifier(scope = scope) },
): Module = if (!available) module { } else module {
    // Real exit never closes Koin: the exit path runs these tasks (on IO, bounded) after the lifecycle's Background event.
    // onClose covers graph teardown in tests and startup failure; shutdown() is idempotent.
    single {
        NotificationRuntime().also { runtime ->
            getOrNull<DesktopExitTasks>()?.register("native notifications", runFirst = true) { withContext(Dispatchers.IO) { runtime.shutdown() } }
        }
    } onClose { it?.shutdown() }
    single<NativeNotifier> {
        val runtime = get<NotificationRuntime>()
        notifierFactory(runtime.scope).also { notifier ->
            installNotificationActivation(notifier, window, get(), runOnUi)
            runtime.onShutdown { (notifier as? AutoCloseable)?.close() }
        }
    }
    single { SystemNotificationsPreference(get(), defaultEnabled = true) }
    single {
        val runtime = get<NotificationRuntime>()
        val policy = NativeNotificationPolicy(
            items = get<ActivityCenter>().items,
            notifier = get<NativeNotifier>(),
            foreground = AppForeground { window.isFocusedAndVisible },
            enabled = get<SystemNotificationsPreference>().enabled,
            clock = clock,
            scope = runtime.scope,
            finishedText = { get<CoreStrings>().notificationFinished },
        )
        get<DesktopLifecycleEvents>().observe { policy.onForegroundChanged(it == DesktopLifecycleEvent.Active) }
        runtime.onShutdown { policy.close() }
        policy
    }
}

/**
 * The IO scope shared by the notifier and the policy, and their shutdown steps. [shutdown] runs the steps in reverse
 * registration order (the policy, registered after the notifier it uses, closes first), then cancels the scope.
 */
internal class NotificationRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val steps = CopyOnWriteArrayList<() -> Unit>()

    fun onShutdown(step: () -> Unit) { steps += step }

    private val done = AtomicBoolean(false)

    fun shutdown() {
        if (!done.compareAndSet(false, true)) return
        steps.reversed().forEach { step -> runCatching(step).onFailure { Log.e(TAG, "Notification shutdown step failed", it) } }
        scope.cancel()
    }
}

/**
 * A click on a native notification brings the app forward: raises [window] and asks the host for the activity list.
 * The handler is called on a D-Bus thread, so the work is posted with [runOnUi].
 */
internal fun installNotificationActivation(
    notifier: NativeNotifier,
    window: DesktopWindowHandle,
    requests: ActivityListRequests,
    runOnUi: (() -> Unit) -> Unit,
) {
    notifier.setOnActivated { id ->
        Log.i(TAG, "Notification activated: $id")
        runOnUi {
            window.toFront()
            requests.request()
        }
    }
}
