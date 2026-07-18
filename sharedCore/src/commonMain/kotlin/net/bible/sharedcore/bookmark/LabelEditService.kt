package net.bible.sharedcore.bookmark

interface LabelEditService {
    /** Number of bookmarks that would be orphaned by deleting the label [labelId]. */
    fun orphanedBookmarkCount(labelId: String): Int
}
