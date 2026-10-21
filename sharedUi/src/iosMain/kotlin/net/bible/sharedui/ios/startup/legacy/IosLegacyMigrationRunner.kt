package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.service.legacymigration.IosLegacyDatabaseFactory
import net.bible.service.legacymigration.LegacyMigrationOutcome
import net.bible.service.legacymigration.LegacyMigrator
import net.bible.service.legacymigration.LegacyPlatform
import net.bible.service.legacymigration.IosLegacyPlatform
import net.bible.service.legacymigration.Step
import net.bible.service.legacymigration.control.LegacyGateDecision
import net.bible.service.legacymigration.control.LegacyMigrationControl
import net.bible.service.legacymigration.control.decideGate
import net.bible.service.legacymigration.extras.LegacyExtrasApplier
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.IosAppDirectories
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.platform.secrets.IosKeychainSecretStore
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.ios.startup.LEGACY_SETTLED_KEY
import platform.Foundation.NSNotificationCenter
import platform.darwin.NSObjectProtocol
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationProtectedDataDidBecomeAvailable

/**
 * Everything the runner takes from its environment; production builds one lazily, tests replace it through
 * [IosLegacyMigrationRunner.testHooks]. Only the environment is replaceable: the attempt/ordering logic is always the
 * production code.
 *
 * @property protectedDataAvailable whether file protection allows reading the old stores now.
 * @property awaitProtectedDataChange suspends until the protected-data state may have changed; it must register
 *   interest first and re-check [protectedDataAvailable] so a change in between is not lost (the caller loops).
 * @property runMigrator runs the built migrator (production: `migrator.run()`).
 * @property markSettled sets [LEGACY_SETTLED_KEY] (production: `IosDeviceSettings`).
 */
internal class RunnerHooks(
    val platform: LegacyPlatform,
    val control: LegacyMigrationControl,
    val devicePrefs: DeviceLocalPrefs,
    val protectedDataAvailable: suspend () -> Boolean,
    val awaitProtectedDataChange: suspend () -> Unit,
    val runMigrator: suspend (LegacyMigrator) -> LegacyMigrationOutcome = { it.run() },
    val markSettled: () -> Unit,
)

/**
 * Owns the legacy migration for the whole process, independent of any screen or BGTask waiter: concurrent callers
 * of [run] share one attempt and a finished attempt is not repeated. The work runs on `Dispatchers.Default`; nothing
 * here waits on disk from Main.
 */
internal object IosLegacyMigrationRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var attempt: Deferred<LegacyGateDecision>? = null

    /** Test seam for the environment only: tests only, set before [run]. Setting it forgets any cached attempt. */
    internal var testHooks: RunnerHooks? = null
        set(value) {
            field = value
            attempt = null
        }

    /**
     * The decision of the process's one attempt; a waiter's cancellation does not cancel the attempt, and a finished
     * attempt (including a Recover decision) is never repeated in this process.
     *
     * May throw: a failure while applying the extras (for example the `applied.json` write) or any unexpected error
     * of the attempt. The caller must then never proceed to open the databases; the settled flag stays unset and the
     * import request is kept. A failed attempt is not cached, so the next call repeats it, which is safe because the
     * migrator answers `AlreadyDone` and the extras applier is idempotent.
     */
    suspend fun run(): LegacyGateDecision {
        val shared = lock.withLock {
            // A failed attempt is replaced; a completed decision (Proceed or Recover) stays.
            attempt?.takeUnless { it.isCompleted && it.isCancelled }
                ?: scope.async { execute(testHooks ?: productionHooks()) }.also { attempt = it }
        }
        return shared.await()
    }

    private suspend fun execute(hooks: RunnerHooks): LegacyGateDecision {
        awaitProtectedData(hooks.protectedDataAvailable, hooks.awaitProtectedDataChange)
        val control = hooks.control
        val platform = hooks.platform
        val migrator = newMigrator(platform, replaceExisting = control.importRequested())
        val decision = decideGate(hooks.runMigrator(migrator))
        if (decision is LegacyGateDecision.Proceed) {
            // Order matters: a crash between the steps must leave the flag unset so the next launch re-enters the gate.
            LegacyExtrasApplier(platform.fileSystem, control.dir, hooks.devicePrefs).applyBeforeDatabases()
            control.clearImportRequest()
            hooks.markSettled()
        }
        return decision
    }

    /** The one construction of the migrator, for the startup attempt and the post-commit retry. */
    private fun newMigrator(platform: LegacyPlatform, replaceExisting: Boolean): LegacyMigrator {
        val directories = IosAppDirectories(platform.applicationSupport / "AndBible", platform.documents)
        return LegacyMigrator(
            platform = platform,
            databases = IosLegacyDatabaseFactory(),
            replaceExisting = replaceExisting,
            userPlanDir = directories.manualReadingPlanDir,
            stepHook = { step ->
                if (step == Step.MAP) IosDebugLaunchArgs.legacyStepDelayMs()?.let { delay(it) }
            },
            modulesDir = directories.modulesDir,
        )
    }

    /**
     * Retries the post-commit leftovers (the old search index deletion and the EPUB adoption) once the migration is
     * settled: the gate is skipped from then on, so nothing else would run the migrator's `AlreadyDone` path. Does nothing
     * unless `done.json` still lacks one of the two flags. Best-effort: never throws (cancellation is rethrown) and runs
     * off Main. Waits for the startup attempt if one is in flight, so two migrators never work on the directories at once.
     */
    internal suspend fun retryPostCommit() {
        try {
            withContext(Dispatchers.Default) {
                lock.withLock { attempt }?.let { runCatching { it.await() } }
                val hooks = testHooks ?: productionHooks()
                if (!hooks.control.postCommitPending()) return@withContext
                // replaceExisting = false: the AlreadyDone path never reads it, and an import request must stay untouched.
                hooks.runMigrator(newMigrator(hooks.platform, replaceExisting = false))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            nsLog("legacy migration: post-commit retry failed: ${e.message}")
        }
    }

    private fun productionHooks(): RunnerHooks {
        val platform = IosLegacyPlatform(IosKeychainSecretStore())
        val settings = IosDeviceSettings()
        return RunnerHooks(
            platform = platform,
            control = legacyMigrationControl(platform),
            devicePrefs = settings,
            protectedDataAvailable = ::protectedDataAvailableNow,
            awaitProtectedDataChange = ::awaitProtectedDataNotification,
            markSettled = { settings.putBoolean(LEGACY_SETTLED_KEY, true) },
        )
    }
}

/** The one construction of the migration control (directory `Application Support/LegacyMigration`), shared with [IosLegacyNotices]. */
internal fun legacyMigrationControl(platform: LegacyPlatform): LegacyMigrationControl =
    LegacyMigrationControl(platform.fileSystem, platform.applicationSupport, sync = platform::syncFile)

/** H3: the old stores sit under file protection; before the first unlock they cannot be read, so wait (never fail). */
internal suspend fun awaitProtectedData(available: suspend () -> Boolean, awaitChange: suspend () -> Unit) {
    while (!available()) awaitChange()
}

private suspend fun protectedDataAvailableNow(): Boolean = withContext(Dispatchers.Main) {
    UIApplication.sharedApplication.protectedDataAvailable
}

/**
 * Suspends until `UIApplicationProtectedDataDidBecomeAvailable` posts, or returns at once if data became available
 * meanwhile. Runs on Main (UIKit state and the notification queue); it only registers and checks, never waits on disk.
 */
private suspend fun awaitProtectedDataNotification() = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine<Unit> { cont ->
        val center = NSNotificationCenter.defaultCenter
        var token: NSObjectProtocol? = null
        fun release() { token?.let { center.removeObserver(it) }; token = null }
        token = center.addObserverForName(UIApplicationProtectedDataDidBecomeAvailable, null, NSOperationQueue.mainQueue) {
            release()
            if (cont.isActive) cont.resumeWith(Result.success(Unit))
        }
        cont.invokeOnCancellation { release() }
        // Registered first, then re-checked: a notification posted before registration is not lost.
        if (UIApplication.sharedApplication.protectedDataAvailable) {
            release()
            if (cont.isActive) cont.resumeWith(Result.success(Unit))
        }
    }
}
