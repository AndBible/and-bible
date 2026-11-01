package net.bible.sharedcore.search

/**
 * The translations a reading-view Bible search targets.
 *
 * The persisted selection is the feature and is never replaced by the active document. Search seeding
 * already restored only the indexed remembered Bibles and the active Bible, indexed or not: prompting to
 * index an unindexed active Bible is intended, because searching it is deliberate. Here the active
 * document is ADDED to the (seeded or explicitly chosen) selection only when it is already indexed, so an
 * explicit selection that works is not sent into an index build for the translation being read (F44/B4).
 * An empty selection still falls back to the active document whatever its index state: there is nothing
 * else to search, and the index prompt must stay reachable.
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
