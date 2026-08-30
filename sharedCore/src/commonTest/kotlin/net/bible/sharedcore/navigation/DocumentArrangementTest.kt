/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentArrangementTest {
    private val en = LangOption("en", "English", "en")

    private fun row(
        id: String,
        cat: DocCategory = DocCategory.BIBLE,
        status: DocInstallStatus = DocInstallStatus.NOT_INSTALLED,
        lang: LangOption = en,
        repo: String = "CrossWire",
        recommended: Boolean = false,
        sizeMb: Double? = null,
    ) = DocRow(id, "osis-$id", id, "name $id", lang, repo, cat, status, 0, recommended,
        badWarn = false, locked = false, enciphered = false, canDelete = false, installSizeMb = sizeMb)

    @Test fun status_rank_orders_downloading_then_upgrade_then_installed_then_missing() {
        assertEquals(0, row("a", status = DocInstallStatus.BEING_INSTALLED).sortStatusRank)
        assertEquals(1, row("a", status = DocInstallStatus.UPGRADE_AVAILABLE).sortStatusRank)
        assertEquals(2, row("a", status = DocInstallStatus.INSTALLED).sortStatusRank)
        assertEquals(2, row("a", status = DocInstallStatus.ERROR_DOWNLOADING).sortStatusRank)
        assertEquals(2, row("a", status = DocInstallStatus.INSTALL_CANCELLED).sortStatusRank)
        assertEquals(3, row("a", status = DocInstallStatus.NOT_INSTALLED).sortStatusRank)
    }

    @Test fun docRow_exposes_the_fields_the_row_headline_starts_with() {
        val r = row("KJV", sizeMb = 2.5)
        assertEquals("KJV", r.sortName)            // the headline reads "<abbreviation> <name>"
        assertEquals("name KJV", r.sortSecondaryName)
        assertEquals("English", r.sortLanguage)
        assertEquals("CrossWire", r.sortRepository)
        assertEquals(2_621_440L, r.sortSizeBytes)  // 2.5 MiB
    }

    @Test fun null_install_size_stays_null_rather_than_becoming_zero() {
        assertEquals(null, row("a", sizeMb = null).sortSizeBytes)
    }

    @Test fun category_rank_keeps_the_classic_order_including_its_gap_at_three() {
        assertEquals(
            listOf(0, 1, 2, 4, 5, 6, 7, 8),
            listOf(DocCategory.BIBLE, DocCategory.COMMENTARY, DocCategory.DICTIONARY,
                DocCategory.GENERAL_BOOK, DocCategory.MAPS, DocCategory.AND_BIBLE,
                DocCategory.OTHER, null).map { docCategoryRank(it) },
        )
    }

    private val fi = LangOption("fi", "Finnish", "fi")

    @Test fun default_arrangement_is_the_canonical_key_order_filtered_to_the_applicable_set() {
        val all = defaultArrangement(DocSortKey.entries.toSet())
        assertEquals(
            listOf(DocSortKey.STATUS, DocSortKey.RECOMMENDED, DocSortKey.TYPE, DocSortKey.NAME,
                DocSortKey.LANGUAGE, DocSortKey.REPOSITORY, DocSortKey.SIZE),
            all.sort.map { it.key },
        )
        assertEquals(false, all.sort.any { it.descending })
        assertEquals(DocGroupBy.NONE, all.groupBy)
        assertEquals(null, all.repository)

        val picker = defaultArrangement(setOf(DocSortKey.STATUS, DocSortKey.TYPE, DocSortKey.NAME))
        assertEquals(listOf(DocSortKey.STATUS, DocSortKey.TYPE, DocSortKey.NAME), picker.sort.map { it.key })
    }

    /**
     * THE anchor test of this refactor: the default arrangement must reproduce, row for row, the
     * order the pre-17e comparator produced. The fixture deliberately covers every status bucket,
     * both recommended values, several categories and an abbreviation tie, so a reordering of any
     * single comparator key shows up here.
     */
    @Test fun default_arrangement_reproduces_the_pre17e_order() {
        val rows = listOf(
            row("zeta", DocCategory.COMMENTARY, DocInstallStatus.NOT_INSTALLED),
            row("alpha", DocCategory.BIBLE, DocInstallStatus.NOT_INSTALLED),
            row("beta", DocCategory.BIBLE, DocInstallStatus.INSTALLED),
            row("gamma", DocCategory.BIBLE, DocInstallStatus.UPGRADE_AVAILABLE),
            row("delta", DocCategory.BIBLE, DocInstallStatus.BEING_INSTALLED),
            row("epsi", DocCategory.MAPS, DocInstallStatus.NOT_INSTALLED),
        )
        val legacy = rows.sortedWith(
            compareBy<DocRow>(
                { when (it.installStatus) {
                    DocInstallStatus.BEING_INSTALLED -> 0
                    DocInstallStatus.UPGRADE_AVAILABLE -> 1
                    else -> 2
                } },
                { it.installStatus == DocInstallStatus.NOT_INSTALLED },
                { false },
                { docCategoryRank(it.category) },
                { it.abbreviation.lowercase() },
            )
        )
        assertEquals(
            legacy.map { it.docId },
            sortDocuments(rows, defaultArrangement(DocSortKey.entries.toSet())).map { it.docId },
        )
    }

    @Test fun recommended_first_applies_unconditionally() {
        val rows = listOf(row("b"), row("a", recommended = true))
        val arranged = sortDocuments(rows, DocArrangement(listOf(DocSortCriterion(DocSortKey.RECOMMENDED))))
        assertEquals(listOf("a", "b"), arranged.map { it.docId })
    }

    @Test fun priority_follows_criterion_order() {
        val rows = listOf(
            row("b", DocCategory.BIBLE), row("a", DocCategory.MAPS),
        )
        val byType = sortDocuments(rows, DocArrangement(
            listOf(DocSortCriterion(DocSortKey.TYPE), DocSortCriterion(DocSortKey.NAME))))
        assertEquals(listOf("b", "a"), byType.map { it.docId })
        val byName = sortDocuments(rows, DocArrangement(
            listOf(DocSortCriterion(DocSortKey.NAME), DocSortCriterion(DocSortKey.TYPE))))
        assertEquals(listOf("a", "b"), byName.map { it.docId })
    }

    @Test fun descending_reverses_a_single_criterion() {
        val rows = listOf(row("a"), row("c"), row("b"))
        assertEquals(
            listOf("c", "b", "a"),
            sortDocuments(rows, DocArrangement(listOf(DocSortCriterion(DocSortKey.NAME, descending = true))))
                .map { it.docId },
        )
    }

    @Test fun missing_values_sort_last_in_BOTH_directions() {
        val rows = listOf(row("known", sizeMb = 1.0), row("unknown", sizeMb = null), row("big", sizeMb = 9.0))
        assertEquals(
            listOf("known", "big", "unknown"),
            sortDocuments(rows, DocArrangement(listOf(DocSortCriterion(DocSortKey.SIZE)))).map { it.docId },
        )
        assertEquals(
            listOf("big", "known", "unknown"),
            sortDocuments(rows, DocArrangement(listOf(DocSortCriterion(DocSortKey.SIZE, descending = true))))
                .map { it.docId },
        )
    }

    @Test fun secondary_name_is_the_implicit_final_tiebreak_so_input_order_never_shows() {
        val a = row("same").copy(docId = "a", name = "aaa")
        val b = row("same").copy(docId = "b", name = "bbb")
        val arrangement = DocArrangement(listOf(DocSortCriterion(DocSortKey.NAME)))
        assertEquals(listOf("a", "b"), sortDocuments(listOf(b, a), arrangement).map { it.docId })
        assertEquals(listOf("a", "b"), sortDocuments(listOf(a, b), arrangement).map { it.docId })
    }

    @Test fun language_and_repository_sort_case_insensitively() {
        val rows = listOf(row("x", lang = fi), row("y", lang = en))
        assertEquals(
            listOf("y", "x"),
            sortDocuments(rows, DocArrangement(listOf(DocSortCriterion(DocSortKey.LANGUAGE)))).map { it.docId },
        )
    }

    @Test fun grouping_none_yields_one_group_holding_everything_in_order() {
        val rows = listOf(row("a"), row("b"))
        val groups = groupDocuments(rows, DocGroupBy.NONE)
        assertEquals(1, groups.size)
        assertEquals(DocGroupKey.None, groups[0].key)
        assertEquals(listOf("a", "b"), groups[0].rows.map { it.docId })
    }

    @Test fun grouping_by_type_orders_groups_by_category_rank_not_by_first_appearance() {
        // deliberately fed MAPS-first so "first appearance" would give the wrong answer
        val rows = listOf(row("m", DocCategory.MAPS), row("b", DocCategory.BIBLE))
        val groups = groupDocuments(rows, DocGroupBy.TYPE)
        assertEquals(
            listOf(DocGroupKey.Category(DocCategory.BIBLE), DocGroupKey.Category(DocCategory.MAPS)),
            groups.map { it.key },
        )
    }

    @Test fun grouping_preserves_the_incoming_row_order_within_each_group() {
        val rows = listOf(row("b2", DocCategory.BIBLE), row("m", DocCategory.MAPS), row("b1", DocCategory.BIBLE))
        val groups = groupDocuments(rows, DocGroupBy.TYPE)
        assertEquals(listOf("b2", "b1"), groups.first().rows.map { it.docId })
    }

    @Test fun grouping_by_language_and_repository_is_alphabetical_with_missing_last() {
        val rows = listOf(row("x", lang = fi), row("y", lang = en))
        assertEquals(
            listOf(DocGroupKey.Language("English"), DocGroupKey.Language("Finnish")),
            groupDocuments(rows, DocGroupBy.LANGUAGE).map { it.key },
        )
        val repos = listOf(row("p", repo = "Zeta"), row("q", repo = ""), row("r", repo = "Alpha"))
        assertEquals(
            listOf(DocGroupKey.Repository("Alpha"), DocGroupKey.Repository("Zeta"), DocGroupKey.Repository(null)),
            groupDocuments(repos, DocGroupBy.REPOSITORY).map { it.key },
        )
    }

    @Test fun grouping_by_status_orders_by_rank() {
        val rows = listOf(
            row("n", status = DocInstallStatus.NOT_INSTALLED),
            row("d", status = DocInstallStatus.BEING_INSTALLED),
        )
        assertEquals(
            listOf(DocGroupKey.Status(0), DocGroupKey.Status(3)),
            groupDocuments(rows, DocGroupBy.STATUS).map { it.key },
        )
    }

    private val allKeys = DocSortKey.entries.toSet()

    @Test fun encode_uses_a_leading_minus_for_descending_and_pipes_between_sections() {
        val a = DocArrangement(
            sort = listOf(DocSortCriterion(DocSortKey.STATUS), DocSortCriterion(DocSortKey.SIZE, descending = true)),
            groupBy = DocGroupBy.TYPE,
            repository = "CrossWire",
        )
        assertEquals("STATUS,-SIZE|TYPE|CrossWire", encodeArrangement(a))
    }

    @Test fun decode_round_trips_and_fills_in_applicable_keys_the_stored_string_lacks() {
        val stored = "SIZE,-NAME|LANGUAGE|"
        val decoded = decodeArrangement(stored, allKeys)
        assertEquals(DocSortKey.SIZE, decoded.sort[0].key)
        assertEquals(DocSortCriterion(DocSortKey.NAME, descending = true), decoded.sort[1])
        // every applicable key is present exactly once, missing ones appended in canonical order
        assertEquals(allKeys, decoded.sort.map { it.key }.toSet())
        assertEquals(allKeys.size, decoded.sort.size)
        assertEquals(DocGroupBy.LANGUAGE, decoded.groupBy)
        assertEquals(null, decoded.repository)
    }

    @Test fun decode_drops_keys_this_screen_does_not_declare_applicable() {
        val picker = setOf(DocSortKey.STATUS, DocSortKey.TYPE, DocSortKey.NAME)
        val decoded = decodeArrangement("SIZE,NAME,STATUS,TYPE||", picker)
        assertEquals(listOf(DocSortKey.NAME, DocSortKey.STATUS, DocSortKey.TYPE), decoded.sort.map { it.key })
    }

    @Test fun decode_of_junk_is_total_and_returns_the_default() {
        val default = defaultArrangement(allKeys)
        assertEquals(default, decodeArrangement(null, allKeys))
        assertEquals(default, decodeArrangement("", allKeys))
        assertEquals(default, decodeArrangement("nonsense", allKeys))
        assertEquals(default, decodeArrangement("STATUS", allKeys))          // too few sections
        assertEquals(default, decodeArrangement("STATUS|NOT_A_GROUP|", allKeys))
    }

    @Test fun decode_keeps_a_repository_name_containing_a_pipe() {
        assertEquals("odd|name", decodeArrangement("STATUS|NONE|odd|name", allKeys).repository)
    }

    // Final-review fix I2b: a stored groupBy this screen's sheet has no radio row for must not be
    // accepted silently — it clamps to NONE, while the sort criteria the string DID validly specify
    // (here: a full permutation, since `allKeys` is applicable) still apply undisturbed.
    @Test fun decode_clamps_a_groupBy_the_screen_does_not_declare_applicable_to_none() {
        val restrictedGroupKeys = setOf(DocGroupBy.NONE, DocGroupBy.TYPE)
        val stored = "SIZE,-NAME|STATUS|CrossWire"
        val decoded = decodeArrangement(stored, allKeys, restrictedGroupKeys)
        assertEquals(DocGroupBy.NONE, decoded.groupBy)
        // the sort criteria and repository from the same string are preserved, unaffected by the clamp
        assertEquals(DocSortKey.SIZE, decoded.sort[0].key)
        assertEquals(DocSortCriterion(DocSortKey.NAME, descending = true), decoded.sort[1])
        assertEquals(allKeys, decoded.sort.map { it.key }.toSet())
        assertEquals("CrossWire", decoded.repository)
    }

    @Test fun decode_accepts_a_groupBy_the_screen_does_declare_applicable() {
        val restrictedGroupKeys = setOf(DocGroupBy.NONE, DocGroupBy.TYPE)
        val decoded = decodeArrangement("STATUS||", allKeys, restrictedGroupKeys)
        assertEquals(DocGroupBy.NONE, decoded.groupBy) // blank groupBy field, not the case under test
        val decodedType = decodeArrangement("STATUS|TYPE|", allKeys, restrictedGroupKeys)
        assertEquals(DocGroupBy.TYPE, decodedType.groupBy)
    }
}
