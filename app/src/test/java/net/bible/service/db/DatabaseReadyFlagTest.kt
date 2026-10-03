package net.bible.service.db

import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseReadyFlagTest {
    @After fun restore() { DatabaseContainer.ready = true }

    @Test fun readyComesBackEvenWhenTheBackupThrows() {
        DatabaseContainer.ready = true
        val thrown = assertFailsWith<IllegalStateException> {
            DatabaseContainer.withReadyCleared { throw IllegalStateException("disk full") }
        }
        assertEquals("disk full", thrown.message, "the failure must propagate -- maintainer decision, spec §0")
        assertTrue(DatabaseContainer.ready, "a failed backup left ready=false for good")
    }

    @Test fun readyIsClearedWhileTheBlockRuns() {
        DatabaseContainer.ready = true
        val during = DatabaseContainer.withReadyCleared { DatabaseContainer.ready }
        assertFalse(during)
        assertTrue(DatabaseContainer.ready)
    }
}
