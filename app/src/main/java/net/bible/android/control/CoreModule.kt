package net.bible.android.control

import org.koin.dsl.module

/** The application's Koin module; the one instance every `startKoin` loads. */
val coreModule = buildCoreModule()

/**
 * Builds a NEW instance of the application's module. A Koin `Module` holds its definitions' instance factories, and
 * with them the cached singletons, so two Koin applications loading the same `Module` object share (and, on
 * `close()`, drop) each other's singletons. A test that builds the graph in an isolated application must load its own
 * instance from here, never [coreModule]. Both parts are built anew on every call.
 */
fun buildCoreModule() = module {
    includes(buildPlatformBindings(), buildSharedBindings())
}
