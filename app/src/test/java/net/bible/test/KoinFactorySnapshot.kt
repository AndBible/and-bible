package net.bible.test

import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.instance.InstanceFactory
import org.koin.core.definition.indexKey
import org.koin.core.context.GlobalContext
import kotlin.reflect.KClass

/**
 * Saves the root-scope factory currently registered for one Koin type and later puts that exact
 * factory object back.
 *
 * Restoring an override by loading a new `module { single<T> { original } }` registers a different
 * factory and leaves the shared module's own factory out of the registry. That factory still caches
 * `original`, so `stopKoin()` never drops it, and the next Koin application serves the stale instance
 * (for scope-capturing definitions such as `AndroidGlobalTextDisplaySettingsSource { get() }` that
 * means ClosedScopeException). Use [restore] in `finally` instead.
 */
@OptIn(KoinInternalApi::class)
class KoinFactorySnapshot private constructor(
    private val koin: Koin,
    private val key: Any,
    private val factory: InstanceFactory<*>,
) {
    /** Re-registers the saved factory, replacing whatever override is registered now. */
    fun restore() {
        @Suppress("UNCHECKED_CAST")
        koin.instanceRegistry.saveMapping(true, key as String, factory)
    }

    companion object {
        fun of(type: KClass<*>, koin: Koin = GlobalContext.get()): KoinFactorySnapshot {
            val key = indexKey(type, null, org.koin.core.qualifier.StringQualifier("_root_"))
            val factory = checkNotNull(koin.instanceRegistry.instances[key]) { "No Koin factory registered for $type" }
            return KoinFactorySnapshot(koin, key, factory)
        }

        inline fun <reified T : Any> of(koin: Koin = GlobalContext.get()): KoinFactorySnapshot = of(T::class, koin)
    }
}
