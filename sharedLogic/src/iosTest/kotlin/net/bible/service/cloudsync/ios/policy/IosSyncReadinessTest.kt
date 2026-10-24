package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class IosSyncReadinessTest {
    @Test fun backgroundEntryStartsKoinWhenCold() = runTest {
        val events = mutableListOf<String>()
        assertTrue(runIosBackgroundRefresh({ true }, { false }, { events += "start" },
            { events += "open" }, { events += "sync"; true }, { events += "schedule" }))
        assertEquals(listOf("start", "open", "sync", "schedule"), events)
    }

    @Test fun backgroundEntryReschedulesWithoutProtectedData() = runTest {
        val events = mutableListOf<String>()
        assertTrue(runIosBackgroundRefresh({ false }, { false }, { error("no keychain") },
            { error("no databases") }, { error("no sync") }, { events += "schedule" }))
        assertEquals(listOf("schedule"), events)
    }

    @Test fun backgroundEntryAlwaysReschedulesOnOpenFailure() = runTest {
        var schedules = 0
        assertFalse(runIosBackgroundRefresh({ true }, { true }, { error("already started") },
            { error("locked database") }, { error("not open") }, { schedules++ }))
        assertEquals(1, schedules)
    }

    @Test fun cancellationReschedulesAndPropagates() = runTest {
        var schedules = 0
        assertFailsWith<CancellationException> {
            runIosBackgroundRefresh({ true }, { true }, {}, {},
                { throw CancellationException("expired") }, { schedules++ })
        }
        assertEquals(1, schedules)
    }

    @Test fun debugNamespaceIsOptInAndNeverEnabledInRelease() {
        assertNull(task7SyncNamespace(true, emptyList()))
        assertNull(task7SyncNamespace(false, listOf("-task7AndroidDebugSyncNamespace")))
        assertEquals("net.bible.android.activity.debug", task7SyncNamespace(true, listOf("-task7AndroidDebugSyncNamespace")))
    }
}
