package net.bible.sharedcore.search

/**
 * The translations a reading-view Bible search targets.
 *
 * The persisted selection is the feature and is never replaced by the active document; the active
 * document is merely ADDED to it, and only when it is already indexed. Adding an unindexed document
 * would send the user into an index build for a translation they did not ask to search, on top of a
 * selection that already works (F44/B4). An empty selection still falls back to the active document
 * whatever its index state — there is nothing else to search, and the index prompt must stay
 * reachable.
 */
fun searchTranslationIds(
    persisted: List<String>,
    activeDocId: String,
    activeIsIndexed: Boolean,
): List<String> = when {
    persisted.isEmpty() -> listOf(activeDocId)
    !activeIsIndexed -> persisted
    persisted.contains(activeDocId) -> persisted
    else -> persisted + activeDocId
}
