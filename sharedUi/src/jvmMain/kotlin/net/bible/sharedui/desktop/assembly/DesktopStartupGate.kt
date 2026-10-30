/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.assembly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import net.bible.sharedui.host.startup.CrashRecoveryScreen
import net.bible.sharedui.host.startup.HostStartupGates
import net.bible.sharedui.host.startup.StartupGate
import net.bible.sharedui.host.startup.StartupGateResult
import net.bible.sharedui.host.startup.productionStartupGates
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.AbTheme

/** What the desktop gate chain resolved to. */
enum class DesktopGateOutcome {
    /** Normal startup. */
    Continue,

    /** The crash-recovery screen asked for the backup screen, which runs with the databases closed. */
    OpenBackup,

    /** The crash-recovery screen asked to quit. */
    Exit,
}

/**
 * The hoisted startup gate chain (`productionStartupGates`, as iOS `MainViewController` uses it) with the desktop's
 * gates: no calculator and no legacy migration, only crash recovery, required when [crashRecoveryRequired] says so
 * (no desktop crash tracker exists before D3d, so the app passes `{ false }`). While the gate is up it shows the hoisted
 * [CrashRecoveryScreen] (database free) in [strings] and the [dark] theme; [onReport] runs its "report" action (one at a
 * time). Once resolved, [content] gets the outcome and, for [DesktopGateOutcome.OpenBackup], the call that returns from
 * the backup screen to the gate.
 */
@Composable
fun DesktopStartupGate(
    crashRecoveryRequired: () -> Boolean,
    dark: Boolean,
    strings: Strings,
    onReport: suspend () -> Unit,
    content: @Composable (outcome: DesktopGateOutcome, returnFromBackup: () -> Unit) -> Unit,
) {
    val gates = remember {
        HostStartupGates(productionStartupGates(
            calculatorRequired = { false },
            legacyMigrationRequired = { false },
            crashRecoveryRequired = crashRecoveryRequired,
        )).apply { advance() }
    }
    var revision by remember { mutableIntStateOf(0) }
    revision // changes when a gate resolves
    if (gates.current is StartupGate.CrashRecovery) {
        val scope = rememberCoroutineScope()
        var reporting by remember { mutableStateOf(false) }
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = dark) {
                CrashRecoveryScreen(
                    onReport = {
                        if (!reporting) {
                            reporting = true
                            scope.launch {
                                try { onReport() } finally { reporting = false }
                            }
                        }
                    },
                    onResolve = { result ->
                        gates.advance(result)
                        revision++
                    },
                )
            }
        }
        return
    }
    val outcome = when (gates.result) {
        StartupGateResult.OpenBackup -> DesktopGateOutcome.OpenBackup
        StartupGateResult.Exit -> DesktopGateOutcome.Exit
        StartupGateResult.Continue, null -> DesktopGateOutcome.Continue
    }
    content(outcome) {
        gates.returnFromBackup()
        revision++
    }
}
