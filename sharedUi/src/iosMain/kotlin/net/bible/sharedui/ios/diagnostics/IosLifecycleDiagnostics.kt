@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package net.bible.sharedui.ios.diagnostics

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import net.bible.sharedui.host.diagnostics.DiagnosticSource
import net.bible.sharedui.host.diagnostics.DiagnosticState
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUUID
import platform.UIKit.*
import platform.darwin.NSObjectProtocol
import kotlin.native.ref.WeakReference

internal data class HostSnapshot(
    val window: Any?, val scene: Any?, val applicationState: DiagnosticState,
    val sceneState: DiagnosticState, val hidden: Boolean, val key: Boolean,
)

/** Reads only the actual controller's already-loaded view and owning window. */
internal interface HostOwnershipAccess {
    fun window(controller: UIViewController): Any?
    fun scene(window: Any): Any?
}

internal object UIKitHostOwnership : HostOwnershipAccess {
    override fun window(controller: UIViewController): Any? = controller.viewIfLoaded?.window
    override fun scene(window: Any): Any? = (window as UIWindow).windowScene
}

internal fun snapshotHost(controller: UIViewController, access: HostOwnershipAccess): HostSnapshot {
    val window = access.window(controller)
    val scene = window?.let(access::scene)
    val appState = when (UIApplication.sharedApplication.applicationState) {
        UIApplicationState.UIApplicationStateActive -> DiagnosticState.ACTIVE
        UIApplicationState.UIApplicationStateInactive -> DiagnosticState.INACTIVE
        UIApplicationState.UIApplicationStateBackground -> DiagnosticState.BACKGROUND
        else -> DiagnosticState.UNKNOWN
    }
    val sceneState = when ((scene as? UIWindowScene)?.activationState) {
        UISceneActivationStateForegroundActive -> DiagnosticState.ACTIVE
        UISceneActivationStateForegroundInactive -> DiagnosticState.INACTIVE
        UISceneActivationStateBackground -> DiagnosticState.BACKGROUND
        UISceneActivationStateUnattached -> DiagnosticState.STOPPED
        else -> DiagnosticState.UNKNOWN
    }
    val uiWindow = window as? UIWindow
    return HostSnapshot(window, scene, appState, sceneState, uiWindow?.hidden ?: true, uiWindow?.keyWindow ?: false)
}

/** Main-thread-only; neither hosts nor UIKit objects used as identities are retained. */
internal class IosLifecycleDiagnostics(
    private val emit: (RuntimeDiagnosticEvent) -> Unit,
    private val readHost: (UIViewController) -> HostSnapshot = { snapshotHost(it, UIKitHostOwnership) },
) {
    private data class Host(val controller: WeakReference<UIViewController>, var state: Lifecycle.State)
    private val center = NSNotificationCenter.defaultCenter
    private val observers = mutableListOf<NSObjectProtocol>()
    private val hosts = mutableMapOf<String, Host>()
    private val identities = mutableListOf<Pair<WeakReference<Any>, String>>()
    val observerCount get() = observers.size
    fun attachedHost(id: String) = hosts[id]?.controller?.get()

    fun install() {
        if (observers.isNotEmpty()) return
        val notifications = listOf(
            Triple(UIApplicationDidBecomeActiveNotification, DiagnosticSource.APPLICATION, DiagnosticState.ACTIVE),
            Triple(UIApplicationWillResignActiveNotification, DiagnosticSource.APPLICATION, DiagnosticState.INACTIVE),
            Triple(UIApplicationWillEnterForegroundNotification, DiagnosticSource.APPLICATION, DiagnosticState.FOREGROUND),
            Triple(UIApplicationDidEnterBackgroundNotification, DiagnosticSource.APPLICATION, DiagnosticState.BACKGROUND),
            Triple(UISceneDidActivateNotification, DiagnosticSource.SCENE, DiagnosticState.ACTIVE),
            Triple(UISceneWillDeactivateNotification, DiagnosticSource.SCENE, DiagnosticState.INACTIVE),
            Triple(UISceneWillEnterForegroundNotification, DiagnosticSource.SCENE, DiagnosticState.FOREGROUND),
            Triple(UISceneDidEnterBackgroundNotification, DiagnosticSource.SCENE, DiagnosticState.BACKGROUND),
            Triple(UISceneWillConnectNotification, DiagnosticSource.SCENE, DiagnosticState.STARTED),
            Triple(UISceneDidDisconnectNotification, DiagnosticSource.SCENE, DiagnosticState.STOPPED),
        )
        notifications.forEach { (name, source, transition) ->
            observers += center.addObserverForName(name, null, NSOperationQueue.mainQueue) { notification ->
                emit(RuntimeDiagnosticEvent.LifecycleObserved(source, transition, identity(notification?.`object`)))
                hosts.keys.toList().forEach(::snapshot)
            }
        }
    }

    fun attachHost(id: String, controller: UIViewController) {
        if (hosts[id]?.controller?.get() !== controller) hosts[id] = Host(WeakReference(controller), Lifecycle.State.INITIALIZED)
        snapshot(id)
    }
    fun detachHost(id: String) { hosts.remove(id) }
    fun observeLifecycle(id: String, owner: LifecycleOwner): () -> Unit {
        val observer = LifecycleEventObserver { _, _ -> composeState(id, owner.lifecycle.currentState) }
        owner.lifecycle.addObserver(observer)
        composeState(id, owner.lifecycle.currentState)
        return { owner.lifecycle.removeObserver(observer) }
    }
    fun composeState(id: String, value: Lifecycle.State) {
        hosts[id]?.state = value
        snapshot(id)
    }
    private fun identity(value: Any?): String {
        if (value == null) return "none"
        identities.removeAll { it.first.get() == null }
        return identities.firstOrNull { it.first.get() === value }?.second
            ?: NSUUID().UUIDString.also { identities += WeakReference(value) to it }
    }
    private fun snapshot(id: String) {
        val host = hosts[id] ?: return
        val controller = host.controller.get() ?: run { hosts.remove(id); return }
        val observed = readHost(controller)
        val compose = when (host.state) {
            Lifecycle.State.INITIALIZED -> DiagnosticState.INITIALIZED
            Lifecycle.State.CREATED -> DiagnosticState.CREATED
            Lifecycle.State.STARTED -> DiagnosticState.STARTED
            Lifecycle.State.RESUMED -> DiagnosticState.RESUMED
            Lifecycle.State.DESTROYED -> DiagnosticState.DESTROYED
        }
        emit(RuntimeDiagnosticEvent.OwnershipObserved(id, identity(observed.window), identity(observed.scene), observed.applicationState, observed.sceneState, compose, observed.hidden, observed.key, observed.window != null))
    }
    fun close() {
        observers.forEach { center.removeObserver(it) }
        observers.clear()
        hosts.clear()
        identities.clear()
    }
}

internal val iosLifecycleDiagnostics = IosLifecycleDiagnostics(IosRuntimeDiagnostics::emit)
