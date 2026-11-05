package net.bible.service.db

import androidx.room3.useWriterConnection
import kotlinx.coroutines.runBlocking
import net.bible.android.DurableTestBibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TestDatabaseDurabilityTest {
    @Test fun containerAndRawConnectionsSkipFsync() = runBlocking {
        DatabaseContainer.instance.settingsDb.useWriterConnection {
            assertEquals(0L, it.queryLong("PRAGMA synchronous"))
        }
        val file = File(CommonUtils.tmpDir, "test-durability.sqlite3")
        openSqlite(file.path).use { assertEquals(0L, it.queryLong("PRAGMA synchronous")) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = DurableTestBibleApplication::class, sdk = [TEST_SDK])
class DurableDatabaseFixtureTest {
    @Test fun recoveryFixtureKeepsProductionDurability() = runBlocking {
        DatabaseContainer.instance.settingsDb.useWriterConnection {
            assertEquals(2L, it.queryLong("PRAGMA synchronous"))
        }
        val file = File(CommonUtils.tmpDir, "durable-fixture.sqlite3")
        openSqlite(file.path).use { assertEquals(2L, it.queryLong("PRAGMA synchronous")) }
    }
}
