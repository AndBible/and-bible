package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.service.installzip.InstallError
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipInstallActivityAdapterTest {
    private var now = 0L
    private val center = ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { now }
    private val cancelled = mutableListOf<String>()
    private val adapter = ZipInstallActivityAdapter({ center }, cancel = { cancelled += it })

    private fun job(phase: InstallPhase, name: String? = "kjv.zip") = InstallJobState("j1", name, phase)

    @Test fun progressShowsAsInstallItemWithPercentAndCancel() {
        adapter.onJobs(listOf(job(InstallPhase.Acquiring(30))))
        now = 5_000
        adapter.onJobs(listOf(job(InstallPhase.Committing(60))))
        val item = center.items.value.single()
        assertEquals(ActivityKind.INSTALL, item.kind)
        assertEquals("kjv.zip", item.title)
        assertEquals(60, item.progress)
        item.cancel!!.invoke()
        assertEquals(listOf("j1"), cancelled)
    }

    @Test fun indeterminatePhasesHaveNoPercent() {
        adapter.onJobs(listOf(job(InstallPhase.Inspecting)))
        now = 5_000
        adapter.onJobs(listOf(job(InstallPhase.Inspecting)))
        assertNull(center.items.value.single().progress)
    }

    @Test fun successLeavesASucceededRow() {
        adapter.onJobs(listOf(job(InstallPhase.Committing(10))))
        now = 5_000
        adapter.onTerminal("j1", InstallPhase.Done, "")
        assertEquals(ActivityState.SUCCEEDED, center.items.value.single().state)
    }

    @Test fun failureCarriesTheUserVisibleMessageAndIsUnread() {
        adapter.onJobs(listOf(job(InstallPhase.Committing(10))))
        adapter.onTerminal("j1", InstallPhase.Error(InstallError.INVALID_FILE, null), "Invalid module")
        val item = center.items.value.single()
        assertEquals(ActivityState.FAILED, item.state)
        assertTrue(item.message!!.contains("Invalid module"))
        assertEquals(1, center.unreadFailures.value)
    }

    @Test fun failureOfAJobNeverSeenRunningIsStillRecorded() {
        adapter.onTerminal("j2", InstallPhase.Error(InstallError.INVALID_FILE, null), "Invalid module")
        assertEquals(ActivityState.FAILED, center.items.value.single().state)
    }

    @Test fun cancelledIsDroppedAndALateSnapshotDoesNotResurrectIt() {
        adapter.onJobs(listOf(job(InstallPhase.Committing(10))))
        adapter.onTerminal("j1", InstallPhase.Cancelled, "")
        adapter.onJobs(listOf(job(InstallPhase.Committing(90)))) // stale snapshot arriving after the terminal event
        assertTrue(center.items.value.isEmpty())
        assertNull(center.items.value.firstOrNull())
    }

    @Test fun missingCenterIsHarmless() {
        val a = ZipInstallActivityAdapter({ null }, cancel = {})
        a.onJobs(listOf(job(InstallPhase.Inspecting)))
        a.onTerminal("j1", InstallPhase.Done, "")
        assertNotNull(a)
    }
}
