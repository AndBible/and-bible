package net.bible.sharedcore.search

/**
 * The Bibles offered by the results document selector.
 * A Strong's search only makes sense against Bibles that carry Strong's numbers,
 * so those are filtered to the [hasStrongs][BibleOption.hasStrongs] subset;
 * a plain search offers everything. Input order is preserved.
 */
fun candidateDocuments(strongsSearch: Boolean, all: List<BibleOption>): List<BibleOption> =
    if (strongsSearch) all.filter { it.hasStrongs } else all
