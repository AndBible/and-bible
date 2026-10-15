package net.bible.sharedcore.platform

import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.secrets.SecretStoreException
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosAppEnvironmentTest {
    private class MemorySecrets : SecretStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private fun withEnvironment(block: (IosAppEnvironment) -> Unit) {
        val environment = IosAppEnvironment(MemorySecrets(), { false })
        try { block(environment) } finally { environment.close() }
    }

    private fun assertWritableDirectory(path: String) {
        val directory = path.toPath()
        assertTrue(FileSystem.SYSTEM.metadata(directory).isDirectory)
        val probe = directory / "i1-probe-${NSUUID().UUIDString}"
        try {
            FileSystem.SYSTEM.write(probe) { writeUtf8("writable") }
            assertEquals("writable", FileSystem.SYSTEM.read(probe) { readUtf8() })
        } finally { FileSystem.SYSTEM.delete(probe, mustExist = false) }
        assertFalse(FileSystem.SYSTEM.exists(probe))
    }

    @Test fun databasePathCreatesWritableDatabasesDirectory() = withEnvironment { environment ->
        val path = environment.databasePath("x.sqlite3")
        assertTrue(path.endsWith("/databases/x.sqlite3"))
        assertWritableDirectory(assertNotNull(path.toPath().parent).toString())
    }

    @Test fun sandboxDirectoriesExistAndAreWritable() = withEnvironment { environment ->
        assertTrue(environment.modulesDir.endsWith("/Documents/sword"))
        assertTrue(environment.dbBackupPath.endsWith("/Application Support/backup"))
        listOf(environment.modulesDir, environment.cacheDir, environment.tmpDir, environment.dbBackupPath)
            .forEach(::assertWritableDirectory)
    }

    @Test fun identifierIsPersistedAcrossInstances() {
        val secrets = MemorySecrets()
        val first = IosAppEnvironment(secrets, { false })
        val second = IosAppEnvironment(secrets, { false })
        try {
            val identifier = first.deviceIdentifier
            assertNotNull(NSUUID(uUIDString = identifier))
            assertEquals(identifier, secrets.values["device_identifier"])
            assertEquals(identifier, second.deviceIdentifier)
            assertEquals(identifier, first.deviceIdentifier)
        } finally { first.close(); second.close() }
    }

    @Test fun identifierReadFailurePropagatesWithoutCreatingReplacement() {
        var writes = 0
        val secrets = object : SecretStore {
            override fun get(key: String): String? = throw SecretStoreException("read failed")
            override fun put(key: String, value: String) { writes++ }
            override fun remove(key: String) = Unit
        }
        val environment = IosAppEnvironment(secrets, { false })
        try {
            assertFailsWith<SecretStoreException> { environment.deviceIdentifier }
            assertEquals(0, writes)
        } finally { environment.close() }
    }

    @Test fun identifierWriteFailurePropagates() {
        val secrets = object : SecretStore {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String): Unit = throw SecretStoreException("write failed")
            override fun remove(key: String) = Unit
        }
        val environment = IosAppEnvironment(secrets, { false })
        try { assertFailsWith<SecretStoreException> { environment.deviceIdentifier } }
        finally { environment.close() }
    }

    @Test fun deleteDatabaseRemovesDatabaseAndSidecars() = withEnvironment { environment ->
        val name = "i1-${NSUUID().UUIDString}.sqlite3"
        val database = environment.databasePath(name)
        val paths = listOf(database, "$database-wal", "$database-shm").map { it.toPath() }
        try {
            paths.forEach { path -> FileSystem.SYSTEM.write(path) { writeUtf8("probe") } }
            assertTrue(environment.deleteDatabase(name))
            paths.forEach { assertFalse(FileSystem.SYSTEM.exists(it)) }
            assertFalse(environment.deleteDatabase(name))
        } finally { paths.forEach { FileSystem.SYSTEM.delete(it, mustExist = false) } }
    }

    @Test fun missingDatabaseStillRemovesOrphanSidecars() = withEnvironment { environment ->
        val name = "i1-${NSUUID().UUIDString}.sqlite3"
        val database = environment.databasePath(name)
        val paths = listOf("$database-wal", "$database-shm").map { it.toPath() }
        try {
            paths.forEach { path -> FileSystem.SYSTEM.write(path) { writeUtf8("probe") } }
            assertFalse(environment.deleteDatabase(name))
            paths.forEach { assertFalse(FileSystem.SYSTEM.exists(it)) }
        } finally { paths.forEach { FileSystem.SYSTEM.delete(it, mustExist = false) } }
    }

    @Test fun freeSpaceIsPositive() = withEnvironment { assertTrue(it.megabytesFree > 0) }

    @Test fun discreteReadsCallerStateAndFlagsRemainSupplied() {
        var discrete = false
        val environment = IosAppEnvironment(MemorySecrets(), { discrete }, isBeta = true, isDebugMode = true)
        try {
            assertFalse(environment.isDiscrete)
            discrete = true
            assertTrue(environment.isDiscrete)
            assertTrue(environment.isBeta)
            assertTrue(environment.isDebugMode)
            assertTrue(environment.isInitialized)
            assertEquals(0L, environment.lastCrashTime)
            assertEquals(null, environment.apkPath)
        } finally { environment.close() }
    }

    @Test fun closeIsIdempotent() {
        val environment = IosAppEnvironment(MemorySecrets(), { false })
        environment.close()
        environment.close()
    }
}
