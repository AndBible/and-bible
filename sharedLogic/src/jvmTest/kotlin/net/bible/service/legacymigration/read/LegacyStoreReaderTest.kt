package net.bible.service.legacymigration.read

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.service.legacymigration.LegacyMigrationException
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.allFixtures
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.versionHashes

class LegacyStoreReaderTest {
    private fun FixtureDir.store(name: String) = File(root.toFile(), name)

    private fun FixtureDir.reportCounts(): Map<String, Int> {
        val text = File(root.toFile(), "oracle/report.json").readText()
        return Json.parseToJsonElement(text).jsonObject.getValue("rowCounts").jsonObject
            .mapValues { it.value.jsonPrimitive.int }
    }

    private fun <T> withRw(file: File, block: (SQLiteConnection) -> T): T {
        val c = BundledSQLiteDriver().open(file.path)
        try {
            return block(c)
        } finally {
            c.close()
        }
    }

    private fun execute(file: File, sql: String, blob: ByteArray? = null) = withRw(file) { c ->
        val s = c.prepare(sql)
        try {
            if (blob != null) s.bindBlob(1, blob)
            s.step()
        } finally {
            s.close()
        }
    }

    private fun sha256Of(dir: File): Map<String, String> = dir.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(dir).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
    }

    @Test
    fun readsRichFixtureCounts() {
        val fx = fixture("1.0.2", "18", "rich")
        val expected = fx.reportCounts()
        assertTrue(expected.isNotEmpty())
        for (storeName in listOf("AndBible", "LocalStore")) {
            LegacyStoreReader(fx.store("$storeName.store").path).use { reader ->
                assertEquals(30, reader.map.entities.size)
                for (entity in reader.map.entities.keys) {
                    val want = expected.getValue("$storeName.$entity")
                    assertEquals(want, reader.count(entity), "count $storeName.$entity")
                    assertEquals(want, reader.rows(entity).size, "rows $storeName.$entity")
                }
            }
        }
        assertTrue(expected.getValue("AndBible.BibleBookmark") > 0)
    }

    @Test
    fun uuidAndDateDecoding() {
        val fx = fixture("1.0.2", "18", "rich")
        LegacyStoreReader(fx.store("AndBible.store").path).use { reader ->
            val workspaces = reader.rows("Workspace")
            val a = workspaces.single { (it.values["id"] as LegacyValue.UuidV).value == "13000000-0000-0000-0000-0000000000a1" }
            assertEquals(LegacyValue.TextV("Study A ✨"), a.values["name"])
            assertEquals(LegacyValue.IntV(0), a.values["orderNumber"])
            // Seeding: createdAt = 1_700_000_000 + historyIndex * 60 + windowSeed (seeds 3, 4, 5).
            val dates = reader.rows("HistoryItem").map { (it.values["createdAt"] as LegacyValue.DateV).epochMillis }.toSet()
            assertTrue(1_700_000_003_000L in dates, "$dates")
            assertTrue(1_700_000_063_000L in dates, "$dates")
            assertTrue(1_700_000_125_000L in dates, "$dates")
            // A to-one relationship resolves to an existing Window primary key.
            val windowPks = reader.rows("Window").map { it.pk }.toSet()
            assertTrue(reader.rows("HistoryItem").all { it.toOne["window"] in windowPks })
        }
        LegacyStoreReader(fx.store("LocalStore.store").path).use { reader ->
            assertTrue(reader.rows("Setting").any { it.values["key"] != null })
        }
    }

    @Test
    fun snapshotSettingsMergeBothStores() {
        val fx = fixture("1.0.2", "18", "rich")
        LegacySnapshot(
            LegacyStoreReader(fx.store("AndBible.store").path),
            LegacyStoreReader(fx.store("LocalStore.store").path),
        ).use { snapshot ->
            assertTrue(snapshot.settings.size >= fx.reportCounts().getValue("LocalStore.Setting") - 1, "${snapshot.settings.size}")
        }
    }

    @Test
    fun unknownFingerprintFails() {
        val fx = fixture("1.0.2", "18", "rich")
        val store = fx.store("AndBible.store")
        val (plist, firstHash) = withRw(store) { c ->
            val s = c.prepare("SELECT Z_PLIST FROM Z_METADATA")
            val blob = try { s.step(); s.getBlob(0) } finally { s.close() }
            blob to c.versionHashes().values.first()
        }
        // Flip one byte of a version hash inside the plist bytes themselves (no re-serialisation).
        val offset = indexOf(plist, firstHash)
        assertTrue(offset >= 0)
        val mutated = plist.copyOf().also { it[offset] = (it[offset].toInt() xor 0x01).toByte() }
        execute(store, "UPDATE Z_METADATA SET Z_PLIST = ?", mutated)
        val e = assertFailsWith<LegacyMigrationException> { LegacyStoreReader(store.path) }
        assertEquals(Kind.UNKNOWN_MODEL, e.kind)
    }

    @Test
    fun truncatedFileFails() {
        val fx = fixture("1.0.2", "18", "rich")
        val store = fx.store("AndBible.store")
        File(fx.root.toFile(), "AndBible.store-wal").delete()
        File(fx.root.toFile(), "AndBible.store-shm").delete()
        val bytes = store.readBytes()
        store.writeBytes(bytes.copyOf(bytes.size / 2))
        assertEquals(Kind.CORRUPT_STORE, assertFailsWith<LegacyMigrationException> { LegacyStoreReader(store.path) }.kind)
        // Garbage that is not a database at all.
        store.writeBytes(ByteArray(4096) { 7 })
        assertEquals(Kind.CORRUPT_STORE, assertFailsWith<LegacyMigrationException> { LegacyStoreReader(store.path) }.kind)
    }

    @Test
    fun missingFileFails() {
        val fx = fixture("1.0.2", "18", "rich")
        assertEquals(Kind.MISSING_STORE, assertFailsWith<LegacyMigrationException> { LegacyStoreReader(fx.store("nope.store").path) }.kind)
    }

    @Test
    fun unknownBlobEncodingFails() {
        val fx = fixture("1.0.2", "18", "rich")
        val store = fx.store("AndBible.store")
        execute(store, "UPDATE ZWORKSPACE SET ZBOOKMARKSHIDELABELS = x'00'")
        LegacyStoreReader(store.path).use { reader ->
            val e = assertFailsWith<LegacyMigrationException> { reader.rows("Workspace") }
            assertEquals(Kind.UNSUPPORTED_ENCODING, e.kind)
            // Other entities stay readable.
            assertTrue(reader.rows("Label").isNotEmpty())
        }
    }

    @Test
    fun badUuidLengthIsCorrupt() {
        val fx = fixture("1.0.2", "18", "rich")
        val store = fx.store("AndBible.store")
        execute(store, "UPDATE ZWORKSPACE SET ZID = x'0102'")
        LegacyStoreReader(store.path).use { reader ->
            assertEquals(Kind.CORRUPT_STORE, assertFailsWith<LegacyMigrationException> { reader.rows("Workspace") }.kind)
        }
    }

    @Test
    fun sourceIsNotModified() {
        // 1.0.1/17/fresh has non-empty -wal files, so sidecars are exercised too.
        val fx = fixture("1.0.1", "17", "fresh")
        assertTrue(fx.store("AndBible.store-wal").length() > 0)
        val before = sha256Of(fx.root.toFile())
        LegacySnapshot(LegacyStoreReader(fx.store("AndBible.store").path), LegacyStoreReader(fx.store("LocalStore.store").path)).use { s ->
            s.cloud.map.entities.keys.forEach { s.cloud.rows(it); s.local.rows(it) }
        }
        val after = sha256Of(fx.root.toFile())
        val changed = (before.keys + after.keys).filter { before[it] != after[it] }.sorted()
        // The -shm file is SQLite's rebuildable WAL index: any reader (even a read-only one) updates its read marks,
        // so it is the only file allowed to change. The databases, the -wal files and every other file stay intact.
        assertEquals(emptyList(), changed.filterNot { it.endsWith(".store-shm") }, "changed files")
        // The shm really is disposable: without it the same content is read again.
        fx.root.toFile().listFiles { f -> f.name.endsWith("-shm") }!!.forEach { it.delete() }
        LegacyStoreReader(fx.store("AndBible.store").path).use { r ->
            assertEquals(r.rows("Label").size, fx.reportCounts().getValue("AndBible.Label"))
        }
    }

    @Test
    fun walContentIsVisibleThroughReadOnlyOpen() {
        val withWal = allFixtures().filter { fx ->
            fx.storeFilesWithWal().isNotEmpty()
        }
        assertTrue(withWal.isNotEmpty(), "no fixture with a non-empty -wal")
        var nonTrivial = 0
        for (fx in withWal) {
            for (store in fx.storeFilesWithWal()) {
                val name = store.name.removeSuffix(".store")
                val reader = LegacyStoreReader(store.path)
                val viaReader = reader.use { r -> r.map.entities.keys.associate { "$name.$it" to r.count(it) } }
                // Ground truth: the oracle's counts, taken from the live store the fixture was copied from.
                val expected = fx.reportCounts()
                assertEquals(expected.filterKeys { it.startsWith("$name.") }, viaReader, "${fx.app}/${fx.ios}/${fx.scenario} $name")
                nonTrivial += viaReader.values.sum()
            }
        }
        assertTrue(nonTrivial > 0)
    }

    @Test
    fun readsEveryManifestFixture() {
        val fixtures = allFixtures()
        assertEquals(24, fixtures.size)
        val fingerprints = mutableSetOf<String>()
        for (fx in fixtures) {
            val expected = fx.reportCounts()
            for (name in listOf("AndBible", "LocalStore")) {
                LegacyStoreReader(fx.store("$name.store").path).use { r ->
                    fingerprints += r.fingerprint
                    for (entity in r.map.entities.keys) {
                        val rows = r.rows(entity)
                        assertEquals(expected["$name.$entity"] ?: rows.size, rows.size, "${fx.app}/${fx.ios}/${fx.scenario} $name.$entity")
                        assertEquals(rows.size, r.count(entity))
                    }
                }
            }
        }
        assertEquals(4, fingerprints.size)
    }

    private fun FixtureDir.storeFilesWithWal(): List<File> =
        root.toFile().listFiles { f -> f.name.endsWith(".store") && File(f.path + "-wal").let { it.exists() && it.length() > 0 } }.orEmpty().toList()

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
