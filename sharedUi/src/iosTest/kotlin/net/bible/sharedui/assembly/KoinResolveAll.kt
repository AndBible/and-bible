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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass

/**
 * The factories that take a Koin parameter, each with the parameters to resolve it with. This is the ONLY set
 * [resolveAllDefinitions] resolves differently; everything else is resolved plainly and a failure is reported.
 * `SyncSettingsServiceImpl` is built per settings screen from a screen-owned scope.
 */
val PARAMETERISED_FACTORIES: Map<KClass<*>, ParametersDefinition> = mapOf(
    SyncSettingsServiceImpl::class to { parametersOf(CoroutineScope(Job())) },
)

/**
 * Test helper: resolves EVERY definition of this Koin application (by primary type and qualifier, one per factory)
 * and returns the ones that failed as (definition name, failure). Nothing is skipped: a definition that cannot be
 * resolved is reported, never ignored. The failure's message is the definition and the whole cause chain (Koin wraps
 * the real error in several layers), so a failure names its root cause without a debugger.
 */
@OptIn(KoinInternalApi::class)
fun Koin.resolveAllDefinitions(
    parameterised: Map<KClass<*>, ParametersDefinition> = PARAMETERISED_FACTORIES,
): List<Pair<String, Throwable>> {
    val failures = mutableListOf<Pair<String, Throwable>>()
    val seen = mutableSetOf<Any>()
    for (factory in instanceRegistry.instances.values.toList()) {
        val definition = factory.beanDefinition
        if (!seen.add(definition)) continue // a definition is indexed under its primary and each secondary type
        val name = (definition.primaryType.qualifiedName ?: definition.primaryType.toString()) +
            (definition.qualifier?.let { "[$it]" } ?: "")
        try {
            get<Any>(definition.primaryType, definition.qualifier, parameterised[definition.primaryType])
        } catch (e: Throwable) {
            failures += name to IllegalStateException("$name\n${causeChain(e)}", e)
        }
    }
    return failures
}

/** Every layer of [e] on its own line, newest cause last, so the root cause is the final line. */
fun causeChain(e: Throwable): String =
    generateSequence(e) { it.cause }.joinToString("\n  caused by ") { t ->
        "${t::class.qualifiedName}: ${t.message.orEmpty().replace('\n', ' ').take(500)}"
    }

/** A failure list as one readable assertion message. */
fun describeFailures(failures: List<Pair<String, Throwable>>): String =
    failures.joinToString("\n\n") { (name, e) -> "$name -> ${e.message}" }
