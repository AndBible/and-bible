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
package net.bible.sharedui.assembly

import net.bible.sharedcore.log.nsLog
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.mp.KoinPlatform

/** Outcome of resolving every Koin definition: how many resolved, which are phase 2 dependents, which failed otherwise. */
data class AssemblyProbeResult(val resolved: Int, val phase2Dependents: List<String>, val failures: List<String>)

/**
 * Debug-only proof that the real graph assembles (spec section 4): resolves every definition. A failure that
 * [classifyAssemblyFailure] calls a phase 2 dependent (Stream D ports unbound, workspace not initialised: the
 * workspace init is a phase 2 join) is counted separately; any other failure is a real assembly bug and is listed.
 */
@OptIn(KoinInternalApi::class)
internal fun Koin.probeAllDefinitions(): AssemblyProbeResult {
    var resolved = 0
    val sync = mutableListOf<String>()
    val failures = mutableListOf<String>()
    val seen = mutableSetOf<Any>()
    for (factory in instanceRegistry.instances.values.toList()) {
        val definition = factory.beanDefinition
        if (!seen.add(definition)) continue
        val name = (definition.primaryType.qualifiedName ?: definition.primaryType.toString()) +
            (definition.qualifier?.let { "[$it]" } ?: "")
        try {
            get<Any>(definition.primaryType, definition.qualifier, parameters(definition.primaryType))
            resolved++
        } catch (e: Throwable) {
            val chain = generateSequence(e) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
            if (classifyAssemblyFailure(chain) == AssemblyFailureKind.PHASE_2_DEPENDENT) sync += name
            else failures += "$name: ${generateSequence(e) { it.cause }.last().message.orEmpty().take(400)}"
        }
    }
    return AssemblyProbeResult(resolved, sync, failures)
}

/** The one parameterised factory: built per settings screen from a screen-owned scope. */
private fun parameters(type: kotlin.reflect.KClass<*>): org.koin.core.parameter.ParametersDefinition? =
    if (type == net.bible.android.view.activity.settings.SyncSettingsServiceImpl::class) {
        { org.koin.core.parameter.parametersOf(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job())) }
    } else null

/** Logs the one probe line the Mac run greps for. Call off the main thread, after the startup is Ready. */
internal fun logAssemblyProbe() {
    val r = KoinPlatform.getKoin().probeAllDefinitions()
    nsLog("I2 assembly: Koin graph resolved (${r.resolved} definitions), databases open")
    nsLog("I2 assembly: ${r.phase2Dependents.size} phase-2 dependents not resolved (Stream D ports / workspace): ${r.phase2Dependents.joinToString()}")
    r.failures.forEach { nsLog("I2 assembly: UNEXPECTED unresolvable: $it") }
}
