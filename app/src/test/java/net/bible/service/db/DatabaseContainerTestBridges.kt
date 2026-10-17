package net.bible.service.db

import net.bible.android.database.AiSettingsDatabase

/*
 * Blocking, test-only wrappers around the suspend lifecycle of DatabaseContainer. They live in test sources
 * so the production class carries no blocking bridge; production code uses the *Owned variants.
 */

/** Closes, forgets and (if never opened) first opens the container, then drops it. */
fun DatabaseContainer.Companion.reset() = blockingAtEdge { aiLifetime.replace { resetOwned(it) } } // L1-edge: test-only synchronous wrapper; production uses resetOwned

/**
 * Failure-path variant of [reset]: closes only an instance that already exists.
 */
fun DatabaseContainer.Companion.dropInstanceWithoutOpening() = blockingAtEdge { // L1-edge: test-only synchronous wrapper; production uses dropInstanceWithoutOpeningOwned
    aiLifetime.replace { dropInstanceWithoutOpeningOwned(it) }
}

/** Flushes the settings store, cancels its writer and closes every database. */
fun DatabaseContainer.closeAll() = blockingAtEdge { DatabaseContainer.aiLifetime.replace { closeAllOwned(it) } } // L1-edge: test-only synchronous wrapper; production uses closeAllOwned

/** Closes one database by file name ahead of its file being replaced or deleted. */
fun DatabaseContainer.closeForReplace(fileName: String) = blockingAtEdge { // L1-edge: test-only synchronous wrapper; production uses closeForReplaceOwned
    DatabaseContainer.aiLifetime.replace { closeForReplaceOwned(fileName, it) }
}

fun DatabaseContainer.resetAiSettingsDb(): AiSettingsDatabase = blockingAtEdge { // L1-edge: test-only synchronous wrapper; production uses resetAiSettingsDbOwned
    DatabaseContainer.aiLifetime.replace { resetAiSettingsDbOwned(it) }
}
