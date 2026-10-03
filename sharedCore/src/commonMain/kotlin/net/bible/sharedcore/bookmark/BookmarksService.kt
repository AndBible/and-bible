package net.bible.sharedcore.bookmark

/**
 * Host seam for the Bookmarks list (mirrors classic `Bookmarks.kt`'s Room/JSword access): the
 * controller owns UI state (filter/sort/search/showNotes/selection) and calls through here for the
 * async row load and the two persisted prefs (sort mode, show-notes). Implemented on Android by a
 * Room/JSword-backed adapter; keeps `:sharedCore` commonMain iOS-clean (no Android/JSword/Room/IdType
 * types cross this boundary — everything is `String`/`List`/primitives, per [BookmarksModel]).
 */
interface BookmarksService {
    /** allLabels displayNames, classic spinner order (0=All, 1=Unlabeled, …). */
    fun filterLabels(): List<BookmarkFilterLabel>

    /** Loads the filtered/sorted/searched rows. [search] is `null` when there's no active search text. */
    suspend fun loadRows(filterIndex: Int, sort: BookmarkSortMode, search: String?, showNotes: Boolean): List<BookmarkRow>

    /** From the BookmarkSortOrder pref, mapped onto the reachable [BookmarkSortMode] cycle. */
    fun loadSortMode(): BookmarkSortMode

    /** Persist the current sort mode. */
    fun saveSortMode(mode: BookmarkSortMode)

    /** `bookmark_show_notes` pref (default true). */
    fun loadShowNotes(): Boolean

    /** Persist the show-notes toggle. */
    fun saveShowNotes(v: Boolean)
}
