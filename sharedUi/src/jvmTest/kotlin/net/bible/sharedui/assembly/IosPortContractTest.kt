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

import net.bible.android.control.buildSharedBindings
import net.bible.service.format.SaxTextPolicy
import org.koin.core.Koin
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * The Mac-free half of the iOS assembly check: finds, by resolving every `buildSharedBindings()` definition against
 * stubbed ports, which port types the shared definitions require, and compares that SET with what
 * `IosPlatformBindings` is declared to bind ([IOS_BOUND_PORT_TYPES]) plus the four sync ports the acceptance test
 * supplies as doubles ([SYNC_PORT_TYPES]). The discovery is a LOWER bound: a port that a definition looks up lazily
 * (inside a lambda, or through `KoinPlatform` in a constructor body) is not seen here. The `:app` test
 * `IosPortParityTest` covers that gap by comparing with the ports Android binds; the Mac run resolves everything. The iOS test `IosAssemblyTest.boundPortListMatchesTheModule` ties that
 * declared list to the real module, so a missing binding fails here (JVM) or there (Native), never silently.
 */
class IosPortContractTest {
    /** Values for required types that are not interfaces (enums etc.). */
    private val concrete: Map<KClass<*>, Any> = mapOf(SaxTextPolicy::class to SaxTextPolicy.MODERN)

    /**
     * A lenient stub: answers neutral defaults (0, false, "", empty collections, a nested stub for an interface) so a
     * constructor body that calls a port does not abort the walk before its later `get()` arguments are seen.
     */
    private fun stub(type: KClass<*>): Any = stubOf(type.java)

    private fun stubOf(c: Class<*>): Any =
        Proxy.newProxyInstance(c.classLoader, arrayOf(c)) { proxy, m, args ->
            val r = m.returnType
            when {
                m.name == "toString" -> "stub of ${c.simpleName}"
                m.name == "hashCode" -> System.identityHashCode(proxy)
                m.name == "equals" -> proxy === args?.get(0)
                r == Void.TYPE -> null
                r == java.lang.Boolean.TYPE -> false
                r == java.lang.Integer.TYPE -> 0
                r == java.lang.Long.TYPE -> 0L
                r == java.lang.Double.TYPE -> 0.0
                r == java.lang.Float.TYPE -> 0f
                r == String::class.java -> ""
                r == List::class.java -> emptyList<Any>()
                r == Map::class.java -> emptyMap<Any, Any>()
                r == Set::class.java -> emptySet<Any>()
                r.isInterface -> stubOf(r)
                else -> null
            }
        }

    private fun missingType(e: Throwable): String? {
        var t: Throwable? = e
        while (t != null) {
            if (t is NoDefinitionFoundException) {
                return Regex("No definition found for type '([^']+)'").find(t.message.orEmpty())?.groupValues?.get(1)
                    ?: error("unparsable NoDefinitionFoundException: ${t.message}")
            }
            t = t.cause
        }
        return null
    }

    /**
     * Port types needed by the shared definitions. A definition whose constructor body calls a stub still counts as
     * resolved far enough: every `get()` argument is evaluated before the body, so its missing types were seen.
     */
    internal fun requiredPorts(): Set<KClass<*>> {
        val required = linkedSetOf<KClass<*>>()
        val koin: Koin = koinApplication { modules(buildSharedBindings()) }.koin
        var rounds = 0
        while (true) {
            check(++rounds < 200) { "no fixpoint after 200 rounds; required so far: ${required.map { it.simpleName }}" }
            var added = false
            @OptIn(org.koin.core.annotation.KoinInternalApi::class)
            val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinctBy { it.primaryType to it.qualifier }
            for (def in definitions) {
                val name = try {
                    koin.get<Any>(def.primaryType, def.qualifier) { parametersOf(CoroutineScope(Job())) }
                    null
                } catch (e: Throwable) {
                    missingType(e)
                } ?: continue
                val type = Class.forName(name).kotlin
                if (required.add(type)) {
                    added = true
                    val value = concrete[type] ?: run {
                        assertTrue(type.java.isInterface, "shared definitions need the concrete type $name; give it a value in `concrete`")
                        stub(type)
                    }
                    koin.declare(value, secondaryTypes = listOf(type), allowOverride = true)
                }
            }
            if (!added) return required
        }
    }

    private fun names(types: Collection<KClass<*>>) = types.map { it.qualifiedName!! }.sorted()

    @Test
    fun theDiscoveryFindsTheKnownPorts() {
        val required = requiredPorts()
        // Guards the check itself: if resolution silently stopped discovering, these would vanish.
        val known = listOf("AppSettings", "CoreStrings", "DownloadHttp", "SpeakPlatform", "AddonCatalog", "LocalCalendar", "AppEnvironment")
        assertTrue(known.all { k -> required.any { it.simpleName == k } }, "discovered: ${names(required)}")
        assertTrue(required.size >= 15, "only ${required.size} ports discovered: ${names(required)}")
    }

    @Test
    fun everyPortTheSharedDefinitionsNeedIsBoundOrDoubled() {
        val covered = IOS_BOUND_PORT_TYPES + SYNC_PORT_TYPES
        assertEquals(emptyList(), names(requiredPorts() - covered.toSet()), "ports required by buildSharedBindings() but not bound by IosPlatformBindings")
    }

    @Test
    fun theCheckReportsAMissingBinding() {
        val reduced = (IOS_BOUND_PORT_TYPES - net.bible.sharedcore.platform.CoreStrings::class) + SYNC_PORT_TYPES
        assertEquals(listOf("net.bible.sharedcore.platform.CoreStrings"), names(requiredPorts() - reduced.toSet()))
    }
}
