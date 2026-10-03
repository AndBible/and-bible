package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocArrangement
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocGroupKey
import net.bible.sharedcore.navigation.DocSortCriterion
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.groupDocuments
import net.bible.sharedcore.navigation.sortDocuments
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudDocArrangementTest {
    private fun item(
        initials: String, name: String = "name $initials",
        category: DocCategory? = DocCategory.BIBLE,
        cloudOnly: Boolean = false, localOnly: Boolean = false, updateAvailable: Boolean = false,
        blocked: Boolean = false, cloudDeleted: Boolean = false, sizeBytes: Long? = null,
    ) = CloudDocItem(initials, name, category, cloudVersion = "1", localVersion = "1",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = updateAvailable,
        localNewer = false, blocked = blocked, canDeleteLocal = true, cloudDeleted = cloudDeleted,
        sizeLabel = null, sizeBytes = sizeBytes)

    @Test fun the_name_is_what_the_row_headline_shows_and_initials_is_the_tiebreak() {
        val i = item("KJV", name = "King James")
        assertEquals("King James", i.sortName)
        assertEquals("KJV", i.sortSecondaryName)
    }

    @Test fun cloud_rows_have_no_language_or_repository() {
        val i = item("KJV")
        assertEquals(null, i.sortLanguage)
        assertEquals(null, i.sortRepository)
    }

    @Test fun the_status_rank_follows_cloudDocStatus_ordering() {
        assertEquals(cloudDocStatus(item("a")).ordinal, item("a").sortStatusRank)
        assertEquals(CloudDocStatus.UPDATE.ordinal, item("b", updateAvailable = true).sortStatusRank)
        assertEquals(CloudDocStatus.REMOVED.ordinal, item("c", cloudDeleted = true).sortStatusRank)
    }

    @Test fun cloud_items_sort_by_size_numerically_not_by_a_formatted_label() {
        val rows = listOf(item("small", sizeBytes = 900_000), item("big", sizeBytes = 12_000_000))
        assertEquals(
            listOf("big", "small"),
            sortDocuments(rows, DocArrangement(listOf(DocSortCriterion(DocSortKey.SIZE, descending = true))))
                .map { it.initials },
        )
    }

    @Test fun cloud_items_group_by_status() {
        val rows = listOf(item("u", updateAvailable = true), item("s"))
        val groups = groupDocuments(rows, DocGroupBy.STATUS)
        assertEquals(2, groups.size)
        val ranks = groups.map { (it.key as DocGroupKey.Status).rank }
        assertEquals(listOf(cloudDocStatus(item("s")).ordinal, CloudDocStatus.UPDATE.ordinal).sorted(), ranks.sorted())
    }
}
