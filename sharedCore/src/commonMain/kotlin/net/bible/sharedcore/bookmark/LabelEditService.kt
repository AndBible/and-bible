package net.bible.sharedcore.bookmark

interface LabelEditService {
    /** Number of bookmarks that would be orphaned by deleting the label [labelId]. */
    suspend fun orphanedBookmarkCount(labelId: String): Int

    /** "Copy to my labels": the editable copy of add-on collection label [labelId]; returns the copy's name, or null on failure. */
    suspend fun copyAddonLabel(labelId: String): String?
}
