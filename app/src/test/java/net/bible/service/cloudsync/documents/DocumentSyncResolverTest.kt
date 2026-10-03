/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSyncResolverTest {
    // simple numeric-dotted comparator for tests: cloud newer if component-wise integer parts greater
    private val isNewer: (String, String) -> Boolean = { c, l ->
        fun parts(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
        val cp = parts(c); val lp = parts(l)
        val n = maxOf(cp.size, lp.size)
        var result = false
        for (i in 0 until n) {
            val a = cp.getOrElse(i) { 0 }; val b = lp.getOrElse(i) { 0 }
            if (a != b) { result = a > b; break }
        }
        result
    }

    private fun cloud(initials: String, version: String = "1.0", deleted: Boolean = false, ts: Long = 100) =
        CloudDocument(initials, initials, DocumentType.SWORD, version, 1, ts, deleted)
    private fun local(initials: String, version: String = "1.0") = initials to LocalDocument(initials, version)

    @Test fun downloadsWhenNotInstalled() {
        val actions = resolveDocumentSyncActions(listOf(cloud("KJV")), emptyMap(), emptyMap(), emptySet(), isNewer)
        assertEquals(listOf(DocumentSyncAction("KJV", DocumentSyncActionType.DOWNLOAD)), actions)
    }

    @Test fun upgradesWhenCloudNewer() {
        val actions = resolveDocumentSyncActions(listOf(cloud("KJV", "2.0")), mapOf(local("KJV", "1.0")), emptyMap(), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.UPGRADE, actions.single().type)
    }

    @Test fun noneWhenLocalSameOrNewer() {
        val same = resolveDocumentSyncActions(listOf(cloud("KJV", "2.0")), mapOf(local("KJV", "2.0")), emptyMap(), emptySet(), isNewer)
        val newer = resolveDocumentSyncActions(listOf(cloud("KJV", "1.0")), mapOf(local("KJV", "2.0")), emptyMap(), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, same.single().type)
        assertEquals(DocumentSyncActionType.NONE, newer.single().type)
    }

    @Test fun skipsBlocked() {
        val actions = resolveDocumentSyncActions(listOf(cloud("KJV")), emptyMap(), emptyMap(), setOf("KJV"), isNewer)
        assertEquals(DocumentSyncActionType.SKIP_BLOCKED, actions.single().type)
    }

    @Test fun tombstoneUninstallsWhenNewerThanLocalSync() {
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            mapOf(local("KJV")), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.UNINSTALL, actions.single().type)
    }

    @Test fun tombstoneIgnoredWhenNoSyncRecord() {
        // user installed locally, never synced this doc → do not auto-delete
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            mapOf(local("KJV")), emptyMap(), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }

    @Test fun tombstoneIgnoredWhenOlderThanLocalSync() {
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 50)),
            mapOf(local("KJV")), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }

    @Test fun tombstoneIgnoredWhenEqualToLocalSync() {
        // The device that wrote the tombstone records its own sync timestamp equal to the
        // tombstone's. The strict `>` comparison must then resolve to NONE, so "remove from
        // cloud" does not uninstall the document on the very device that initiated it.
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 100)),
            mapOf(local("KJV")), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }

    @Test fun tombstoneIgnoredWhenNotInstalled() {
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            emptyMap(), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }

    @Test fun tombstoneDoesNotUninstallBlockedDocument() {
        // A blocked document is managed independently on this device; a "remove from all devices"
        // tombstone must not uninstall it even when it would otherwise be strictly newer.
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            mapOf(local("KJV")), mapOf("KJV" to 100L), setOf("KJV"), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }

    @Test fun preservesInputOrderAcrossMultipleDocuments() {
        // buildDocumentSyncOps and the service rely on resolveDocumentSyncActions emitting exactly
        // one action per cloud doc, in input order. The single-document tests above cannot catch an
        // ordering regression, so cover all five action types in one ordered pass here.
        val cloudDocs = listOf(
            cloud("AAA"),                              // not installed         → DOWNLOAD
            cloud("BBB", "2.0"),                       // cloud newer           → UPGRADE
            cloud("CCC", deleted = true, ts = 200),    // tombstone, synced 100 → UNINSTALL
            cloud("DDD"),                              // blocked               → SKIP_BLOCKED
            cloud("EEE", "1.0"),                       // local same version    → NONE
        )
        val installed = mapOf(local("BBB", "1.0"), local("CCC"), local("EEE", "1.0"))
        val actions = resolveDocumentSyncActions(cloudDocs, installed, mapOf("CCC" to 100L), setOf("DDD"), isNewer)
        assertEquals(listOf("AAA", "BBB", "CCC", "DDD", "EEE"), actions.map { it.initials })
        assertEquals(
            listOf(
                DocumentSyncActionType.DOWNLOAD,
                DocumentSyncActionType.UPGRADE,
                DocumentSyncActionType.UNINSTALL,
                DocumentSyncActionType.SKIP_BLOCKED,
                DocumentSyncActionType.NONE,
            ),
            actions.map { it.type },
        )
    }

    @Test fun uninstallDeletesWhenDeletable() {
        // A deletable book: the propagated tombstone removes the local copy and records the sync.
        assertEquals(UninstallDecision(delete = true, advanceTimestamp = true), decideUninstall(canDelete = true))
    }

    @Test fun uninstallKeepsUndeletableButStillAdvancesTimestamp() {
        // The critical data-loss guard: an undeletable book (e.g. the last Bible on the receiving
        // device) must NOT be deleted, yet the timestamp must still advance so the tombstone isn't
        // re-evaluated — and re-attempted — on every subsequent sync cycle.
        assertEquals(UninstallDecision(delete = false, advanceTimestamp = true), decideUninstall(canDelete = false))
    }

    // --- selectSyncActions ---

    private val sampleActions = listOf(
        DocumentSyncAction("DL", DocumentSyncActionType.DOWNLOAD),
        DocumentSyncAction("UP", DocumentSyncActionType.UPGRADE),
        DocumentSyncAction("RM", DocumentSyncActionType.UNINSTALL),
        DocumentSyncAction("BK", DocumentSyncActionType.SKIP_BLOCKED),
        DocumentSyncAction("NO", DocumentSyncActionType.NONE),
    )

    @Test
    fun selectSyncActions_allowAll_keepsExecutableDropsNonExecutable() {
        val result = selectSyncActions(sampleActions, allowDownload = true, allowDelete = true)
        assertEquals(
            listOf(
                DocumentSyncAction("DL", DocumentSyncActionType.DOWNLOAD),
                DocumentSyncAction("UP", DocumentSyncActionType.UPGRADE),
                DocumentSyncAction("RM", DocumentSyncActionType.UNINSTALL),
            ),
            result,
        )
    }

    @Test
    fun selectSyncActions_downloadOff_dropsDownloadAndUpgrade() {
        val result = selectSyncActions(sampleActions, allowDownload = false, allowDelete = true)
        assertEquals(listOf(DocumentSyncAction("RM", DocumentSyncActionType.UNINSTALL)), result)
    }

    @Test
    fun selectSyncActions_deleteOff_dropsUninstall() {
        val result = selectSyncActions(sampleActions, allowDownload = true, allowDelete = false)
        assertEquals(
            listOf(
                DocumentSyncAction("DL", DocumentSyncActionType.DOWNLOAD),
                DocumentSyncAction("UP", DocumentSyncActionType.UPGRADE),
            ),
            result,
        )
    }

    @Test
    fun selectSyncActions_allOff_keepsNothing() {
        assertEquals(emptyList<DocumentSyncAction>(), selectSyncActions(sampleActions, allowDownload = false, allowDelete = false))
    }

    // --- resolveUploads ---

    private val newer: (String, String) -> Boolean = { a, b -> a > b } // simple lexical comparator for tests

    @Test
    fun resolveUploads_includesLocalOnly() {
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        val result = resolveUploads(local, cloudDocs = emptyList(), blocked = emptySet(), isNewer = newer)
        assertEquals(listOf("KJV"), result)
    }

    @Test
    fun resolveUploads_includesLocalNewerThanCloud() {
        val local = mapOf("KJV" to LocalDocument("KJV", "2.0"))
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "1.0", 0, 0, deleted = false))
        val result = resolveUploads(local, cloud, blocked = emptySet(), isNewer = newer)
        assertEquals(listOf("KJV"), result)
    }

    @Test
    fun resolveUploads_excludesFullySynced() {
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "1.0", 0, 0, deleted = false))
        assertEquals(emptyList<String>(), resolveUploads(local, cloud, emptySet(), newer))
    }

    @Test
    fun resolveUploads_excludesBlocked() {
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        assertEquals(emptyList<String>(), resolveUploads(local, emptyList(), blocked = setOf("KJV"), isNewer = newer))
    }

    @Test
    fun resolveUploads_excludesTombstonedCloud() {
        // A tombstone is a deletion intent: a still-installed local copy (e.g. auto-delete disabled)
        // must NOT be auto-pushed back, or the sync cycle would resurrect a document deleted
        // elsewhere. Restoring is an explicit manual action, not part of resolveUploads.
        val local = mapOf("KJV" to LocalDocument("KJV", "1.0"))
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "9.0", 0, 0, deleted = true))
        assertEquals(emptyList<String>(), resolveUploads(local, cloud, emptySet(), newer))
    }

    @Test
    fun resolveUploads_excludesTombstonedEvenWhenLocalVersionNewer() {
        // Even a locally-newer copy must not be auto-resurrected over a tombstone.
        val local = mapOf("KJV" to LocalDocument("KJV", "9.0"))
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "1.0", 0, 0, deleted = true))
        assertEquals(emptyList<String>(), resolveUploads(local, cloud, emptySet(), newer))
    }

    @Test
    fun resolveUploads_tombstoneDoesNotBlockOtherLocalOnlyDocs() {
        // A tombstone for one document must not suppress an unrelated genuinely-local-only upload.
        val local = mapOf(
            "KJV" to LocalDocument("KJV", "1.0"),
            "ESV" to LocalDocument("ESV", "1.0"),
        )
        val cloud = listOf(CloudDocument("KJV", "KJV", DocumentType.SWORD, "1.0", 0, 0, deleted = true))
        assertEquals(listOf("ESV"), resolveUploads(local, cloud, emptySet(), newer))
    }
}
