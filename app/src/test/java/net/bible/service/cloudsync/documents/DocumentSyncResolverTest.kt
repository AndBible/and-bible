package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSyncResolverTest {
    // simple numeric-dotted comparator for tests: cloud newer if lexently-compared parts greater
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

    @Test fun tombstoneIgnoredWhenNotInstalled() {
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            emptyMap(), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }
}
