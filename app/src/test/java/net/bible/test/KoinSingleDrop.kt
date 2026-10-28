package net.bible.test

import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.indexKey
import org.koin.core.qualifier.StringQualifier
import kotlin.reflect.KClass

/**
 * Drops the cached instance of one root-scope Koin single, running its `onClose` callback, so the next `get()`
 * builds a fresh one. The definition itself stays registered (unlike an override, see [KoinFactorySnapshot]).
 *
 * For singles whose lifetime is tied to per-test global state the fixture resets, e.g. a subscriber of an event
 * source whose subscribers [net.bible.android.TestBibleApplication.onTerminate] clears: the surviving instance
 * would run the next test unsubscribed.
 */
@OptIn(KoinInternalApi::class)
fun Koin.dropSingle(type: KClass<*>) {
    val key = indexKey(type, null, StringQualifier("_root_"))
    instanceRegistry.instances[key]?.dropAll()
}
