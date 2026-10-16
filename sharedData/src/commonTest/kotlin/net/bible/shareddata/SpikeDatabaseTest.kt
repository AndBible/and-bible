package net.bible.shareddata

import kotlinx.coroutines.test.runTest
import net.bible.shareddata.spike.SpikeDatabase
import net.bible.shareddata.spike.SpikeEntity
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SpikeDatabaseTest {
    @Test
    fun insertAndReadBack() = runTest {
        val database = openTestDatabase<SpikeDatabase>(testDatabaseDir(), "spike.db")
        try {
            val entity = SpikeEntity(id = 1, text = "Jaak. 1:5 ✓")
            database.spikeDao().insert(entity)
            assertEquals(entity, database.spikeDao().find(1))
        } finally {
            database.close()
        }
    }

    @Test
    fun jswordTypesResolve() {
        assertEquals("KJV", assertNotNull(Versifications.instance().getVersification("KJV")).name)
    }
}
