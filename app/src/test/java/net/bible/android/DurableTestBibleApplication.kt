package net.bible.android

/** Opt-out for fixtures testing real journal, corruption, WAL and restore/recovery behavior. */
class DurableTestBibleApplication : TestBibleApplication() {
    override val useNonDurableTestDatabases = false
}
