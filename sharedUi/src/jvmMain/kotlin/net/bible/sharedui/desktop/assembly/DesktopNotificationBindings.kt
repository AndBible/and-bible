package net.bible.sharedui.desktop.assembly

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.control.activity.NativeNotificationPolicy
import net.bible.android.control.activity.SystemNotificationsPreference
import net.bible.android.control.activity.monotonicActivityClock
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppForeground
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.NativeNotifier
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.platform.desktop.dbus.DesktopDbus
import net.bible.sharedcore.platform.desktop.dbus.linuxNativeNotifier
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private const val TAG = "DesktopNotifications"

class DesktopNotifierSource(val available: Boolean, val create: (CoroutineScope) -> NativeNotifier)

fun linuxNotifierSource(): DesktopNotifierSource =
    DesktopNotifierSource(DesktopDbus().supportedOs) { linuxNativeNotifier(scope = it) }

/**
 * Native system notifications of the desktop graph (#4012): the [NativeNotifier] (D-Bus on Linux; Windows and macOS
 * follow in #4013), the [SystemNotificationsPreference] (default on), and the [NativeNotificationPolicy] that
 * `SharedAppStartup.startActivityCenter` starts. Needs [ActivityCenter], [ActivityListRequests],
 * [DesktopLifecycleEvents], `AppSettings` and [CoreStrings] from the rest of the graph.
 *
 * The policy and the notifier run on one IO scope (their D-Bus calls must never reach the AWT thread). Closing the
 * graph closes the policy first (it removes its progress notifications through the notifier) and then the notifier
 * (flushes its queue for at most 250 ms; transport close is separate), then cancels the scope.
 *
 * Nothing is bound when [source] is unavailable: an unbound [SystemNotificationsPreference] keeps the settings row
 * hidden instead of showing a toggle that does nothing.
 *
 * @param source availability and factory for the current OS
 * @param clock the policy's clock, only compared with itself (not with ActivityCenter's); a test seam
 * @param runOnUi posts to the UI thread; a notification click raises the window there
 */
fun desktopNotificationModule(
    window: DesktopWindowHandle,
    source: DesktopNotifierSource = linuxNotifierSource(),
    runOnUi: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
    clock: () -> Long = monotonicActivityClock(),
): Module = if (!source.available) module { } else module {
    // Real exit never closes Koin: the exit path runs these bounded steps after the lifecycle's Background event.
    // onClose covers graph teardown in tests and startup failure; shutdown() is idempotent.
    single {
        NotificationRuntime().also { runtime ->
            get<DesktopPreExitSteps>().register("native notifications", 1_000) { runtime.shutdown() }
        }
    } onClose { it?.shutdown() }
    single<NativeNotifier> {
        val runtime = get<NotificationRuntime>()
        source.create(runtime.scope).also { notifier ->
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
            attentionText = { get<net.bible.service.llm.LlmStrings>().agentActivityPermissionNeeded },
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
