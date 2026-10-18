package net.bible.test

import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import net.bible.sharedcore.reading.ReadingHostPresence
import org.robolectric.android.controller.ActivityController
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewVisibility
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertTrue

/**
 * Await the actual destination, including asynchronous settings loading and composition.
 *
 * Under a Compose test rule pass [advanceComposition] (`{ compose.waitForIdle() }`) whenever the host may still be
 * composing: the rule runs the host's effects (the settings gate in `setContent` among them) on its own dispatcher,
 * which pumping the main looper does not advance. A gate that completes after the test's last `idle()` (a slow or
 * contended settings load, as under the full forked suite) then never opens, and the route stays null.
 */
fun awaitHostRoute(activity: NavHostComposeActivity, route: String, advanceComposition: () -> Unit = {}) {
    awaitHostCondition("host must reach ${route.substringBefore('?')}", advanceComposition) {
        activity.currentRouteForTest()?.substringBefore('?') == route.substringBefore('?')
    }
}

/** Wait (bounded, looper pumped, real time) for an arbitrary host condition that settles asynchronously. */
fun awaitHostUntil(message: String, ready: () -> Boolean) = awaitHostCondition(message, {}, ready)

/** Wait for real startup effects, not just Activity.resume(): settings loading suspends graph creation. */
fun awaitReadingGraph(activity: NavHostComposeActivity, advanceComposition: () -> Unit = {}) {
    awaitHostCondition("fixture must publish the real visible Reading destination", advanceComposition) {
        activity.currentRouteForTest()?.substringBefore('?') == NavRoutes.READING &&
            activity.composeReadingViewHost != null && ReadingViewVisibility.isVisible
    }
    ReadingFixtureOwners.retain(activity)
}

/** Exact test-owned jobs survive disposal and failed Main pumping until completion is proved. */
internal object ReadingFixtureOwners {
    private class Owner(val jobs: List<Job>, val dispose: (() -> Unit)?) {
        var disposed = dispose == null
    }
    private val owners = mutableListOf<Owner>()
    private val restorations = mutableListOf<() -> Unit>()
    private val closures = linkedMapOf<ActivityController<NavHostComposeActivity>, () -> Unit>()

    @Synchronized fun retainController(controller: ActivityController<NavHostComposeActivity>, closed: () -> Unit) {
        closures.putIfAbsent(controller, closed)
    }

    @Synchronized fun closeController(controller: ActivityController<NavHostComposeActivity>) {
        controller.close()
        // A completed Job is not evidence that onDestroy removed subscriptions/callbacks.
        closures.remove(controller)?.invoke()
    }

    @Synchronized fun retainRestoration(restore: () -> Unit) { restorations += restore }

    @Synchronized fun retain(scopes: List<kotlinx.coroutines.CoroutineScope>, dispose: (() -> Unit)? = null) {
        owners += Owner(scopes.mapNotNull { it.coroutineContext[Job] }.distinct(), dispose)
    }

    fun retain(activity: NavHostComposeActivity, view: net.bible.android.view.activity.page.screen.ComposeReadingViewHost? = activity.composeReadingViewHost) {
        val scopes = listOfNotNull(activity.lifecycleScope,
            try { activity.hostWindowRepository.scope } catch (_: UninitializedPropertyAccessException) { null },
            view?.fixtureScope())
        retain(scopes) { view?.dispose() }
    }

    @Synchronized fun disposeAndSnapshot(): List<Job> {
        var failure: Throwable? = null
        owners.forEach { owner ->
            try {
                if (!owner.disposed) { owner.dispose?.invoke(); owner.disposed = true }
            } catch (e: Throwable) {
                if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e)
            } finally { owner.jobs.forEach { it.cancel() } }
        }
        failure?.let { throw it }
        return owners.flatMap { it.jobs }.distinct()
    }

    @Synchronized fun retireCompleted() { owners.removeAll { it.disposed && it.jobs.all(Job::isCompleted) } }
    @Synchronized fun requireEmpty() {
        retireCompleted()
        check(owners.isEmpty() && restorations.isEmpty() && closures.isEmpty()) { "Retained fixture owners require cleanup retry before restart" }
    }

    suspend fun drain(retryClosures: Boolean = true) {
        val jobs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { disposeAndSnapshot() }
        jobs.forEach { it.join() }
        retireCompleted()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            synchronized(this@ReadingFixtureOwners) {
                var failure: Throwable? = null
                if (retryClosures) closures.keys.toList().forEach { controller ->
                    try { closeController(controller) } catch (e: Throwable) {
                        if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e)
                    }
                }
                failure?.let { throw it }
                check(closures.isEmpty()) { "Controller destruction incomplete; retry cleanup before restoration/reset" }
                restorations.toList().forEach { restore -> restore(); restorations.remove(restore) }
            }
        }
    }
}

internal fun net.bible.android.view.activity.page.screen.ComposeReadingViewHost.fixtureScope(): kotlinx.coroutines.CoroutineScope =
    javaClass.getDeclaredField("hostScope").apply { isAccessible = true }.get(this) as kotlinx.coroutines.CoroutineScope

/** Register every independently constructed host, including ones never assigned to the activity. */
fun fixtureReadingHost(activity: NavHostComposeActivity): net.bible.android.view.activity.page.screen.ComposeReadingViewHost =
    net.bible.android.view.activity.page.screen.ComposeReadingViewHost(activity).also { ReadingFixtureOwners.retain(activity, it) }

/** Close hosts, then join their actual Window/lifecycle owners before any database reset. */
fun closeReadingHostsBeforeReset(
    controllers: MutableList<ActivityController<NavHostComposeActivity>>,
    afterClose: () -> Unit = {},
    reset: () -> Unit = { DatabaseResetter.resetDatabase() },
    restoreBeforeReset: () -> Unit = {},
) {
    controllers.forEach { controller ->
        ReadingFixtureOwners.retain(controller.get())
        ReadingFixtureOwners.retainController(controller) { controllers.remove(controller) }
    }
    var failure: Throwable? = null
    fun attempt(action: () -> Unit) {
        try { action() } catch (e: Throwable) {
            if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e)
        }
    }
    controllers.toList().forEach { controller -> attempt { ReadingFixtureOwners.closeController(controller) } }
    attempt(afterClose)
    // Register BEFORE any drain/attempt. Immediate and deferred failures have identical lifetime.
    ReadingFixtureOwners.retainRestoration(restoreBeforeReset)
    var drained = false
    attempt {
        // Do not silently retry a failed lifecycle stage in this same invocation. Exact Jobs
        // still drain; the centrally retained controller blocks restoration until explicit retry.
        DatabaseResetter.runResetBlocking { ReadingFixtureOwners.drain(retryClosures = false) }
        drained = true
    }
    // Retracting UI presence admits no new DB work and preserves the existing failure contract.
    // The mutable graph/settings restoration above remains deferred until owner completion.
    attempt { ReadingHostPresence.setForeground(null); ReadingViewVisibility.setVisible(false) }
    if (drained && failure == null) attempt(reset)
    failure?.let { throw it }
}

/** Attempt every disposal, retaining incomplete independent jobs across bounded cleanup failure. */
internal fun finishFixtureScopes(scopes: List<kotlinx.coroutines.CoroutineScope>, primary: Throwable? = null, timeoutMs: Long = 5000) {
    ReadingFixtureOwners.retain(scopes)
    val jobs = scopes.mapNotNull { it.coroutineContext[Job] }.distinct()
    var failure = primary
    jobs.forEach { it.cancel() }
    jobs.forEach { job ->
        try { DatabaseResetter.runResetBlocking(timeoutMs) { job.join() } }
        catch (e: Throwable) { if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e) }
    }
    ReadingFixtureOwners.retireCompleted()
    if (primary == null) failure?.let { throw it }
}

/**
 * Pump the main looper (one task at a time) until [ready], failing with [message] after 10 s. For host effects
 * that finish asynchronously, e.g. a `lifecycleScope.launch` that suspends on IO before acting.
 */
fun awaitHostCondition(message: String, advanceComposition: () -> Unit = {}, ready: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (!ready() && System.nanoTime() < deadline) {
        // One task rather than idle(): a Compose popup can keep the looper busy forever.
        shadowOf(Looper.getMainLooper()).runOneTask()
        advanceComposition()
        Thread.sleep(10)
    }
    assertTrue(ready(), message)
}
