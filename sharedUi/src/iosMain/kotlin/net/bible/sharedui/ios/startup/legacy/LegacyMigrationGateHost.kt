package net.bible.sharedui.ios.startup.legacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.legacymigration.IosLegacyPlatform
import net.bible.service.legacymigration.control.DeclineReason
import net.bible.service.legacymigration.control.LegacyGateDecision
import net.bible.service.legacymigration.control.LegacyMigrationControl
import net.bible.service.legacymigration.control.LegacyRecoveryModel
import net.bible.service.legacymigration.control.RecoveryReason
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.platform.secrets.IosKeychainSecretStore
import net.bible.sharedui.ios.ports.iosShareFile
import net.bible.sharedui.ios.report.Attachment
import net.bible.sharedui.ios.report.IosBugReport
import net.bible.sharedui.ios.startup.LEGACY_SETTLED_KEY
import net.bible.sharedui.ios.startup.StartupGateResult

/**
 * Model shown when [IosLegacyMigrationRunner.run] throws. The migration itself has already proceeded and only applying
 * the extras failed, so "Start without old data" and "Restore a backup" would be wrong; the runner does not cache a
 * failed attempt, so closing and reopening the app retries safely.
 */
internal val legacyRunnerFailureModel = LegacyRecoveryModel(
    reason = RecoveryReason.FAILED,
    canRestoreBackup = false,
    canStartFresh = false,
    tryAgainResetsAttempts = false,
)

/**
 * The legacy-migration startup gate. Runs [IosLegacyMigrationRunner.run] and shows the progress view (itself delayed, so
 * a quick run is invisible) until it answers. `Proceed` resolves `Continue`; `Recover` shows [LegacyRecoveryScreen]; a
 * throw also shows the recovery screen (with [legacyRunnerFailureModel]) and never resolves `Continue`. The caller
 * provides the theme and `LocalStrings`.
 */
@Composable
internal fun LegacyMigrationGateHost(neutral: Boolean, reporter: IosBugReport, onResolve: (StartupGateResult) -> Unit) {
    var model by remember { mutableStateOf<LegacyRecoveryModel?>(null) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    val control = remember { IosLegacyMigrationRunner.testHooks?.control ?: productionControl() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            when (val decision = IosLegacyMigrationRunner.run()) {
                LegacyGateDecision.Proceed -> onResolve(StartupGateResult.Continue)
                is LegacyGateDecision.Recover -> model = decision.model
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            nsLog("legacy migration gate failed: ${t.stackTraceToString()}")
            failure = t
            model = legacyRunnerFailureModel
        }
    }

    val shown = model
    if (shown == null) {
        LegacyMigrationProgressScreen(neutral)
        return
    }
    fun settle(reason: DeclineReason, result: StartupGateResult) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.Default) { control.decline(reason) }
                IosDeviceSettings().putBoolean(LEGACY_SETTLED_KEY, true)
                onResolve(result)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                nsLog("legacy migration decline failed: ${t.stackTraceToString()}")
                busy = false
            }
        }
    }
    LegacyRecoveryScreen(
        model = shown,
        onSaveOldData = {
            if (!actionBusy) {
                actionBusy = true
                scope.launch {
                    try {
                        iosShareFile(IosLegacyOldDataArchive.write(control))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        nsLog("saving old data failed: ${t.stackTraceToString()}")
                    } finally {
                        actionBusy = false
                    }
                }
            }
        },
        onReport = {
            if (!reporting) {
                reporting = true
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.Default) { IosLegacyOldDataArchive.reportBytes(control) }
                        val extras = listOfNotNull(bytes?.let { Attachment("legacy_migration_report.json", "application/json", it) })
                        reporter.report(failure, extras)
                    } finally {
                        reporting = false
                    }
                }
            }
        },
        onTryAgain = { scope.launch { withContext(Dispatchers.Default) { runCatching { control.resetAttempts() } } } },
        onRestoreBackup = { settle(DeclineReason.RESTORED_BACKUP, StartupGateResult.OpenBackup) },
        onStartFresh = { settle(DeclineReason.START_FRESH, StartupGateResult.Continue) },
    )
}

private fun productionControl(): LegacyMigrationControl {
    val platform = IosLegacyPlatform(IosKeychainSecretStore())
    return LegacyMigrationControl(platform.fileSystem, platform.applicationSupport, sync = platform::syncFile)
}
