package net.bible.sharedui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.view.activity.bookmark.BookmarkRecreationState
import net.bible.android.view.activity.bookmark.BookmarksValue
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.app.mydocuments.MyDocumentsRecreationState
import net.bible.sharedui.app.readingplan.ReadingPlanRecreationState
import net.bible.sharedui.strings.iosStrings
import org.koin.dsl.koinApplication
import sun.misc.Unsafe

/** Each lifting task adds a `<feature>StateRoundTrips` test with non-default field values. */
class AppHostsStateRoundTripTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    /** Constructing [AppHosts] touches no port, so an unconstructed [HostPorts] is enough here. */
    private fun hosts(): AppHosts {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val ports = unsafe.allocateInstance(HostPorts::class.java) as HostPorts
        return AppHosts(koinApplication { }.koin, shell, ports, iosStrings("en"))
    }

    @Test
    fun emptyStateRoundTrips() {
        val json = Json.encodeToString(AppHostsState())
        assertEquals(AppHostsState(), Json.decodeFromString<AppHostsState>(json))
    }

    @Test
    fun bookmarksStateRoundTrips() {
        val bookmarks = BookmarkRecreationState(
            filter = 3,
            list = BookmarksValue(
                filter = 3, query = "grace", searchOpen = true, selection = setOf("b-1"), expanded = setOf("b-7"),
            ),
            pendingAssignIds = listOf("b-1", "b-2"),
            awaitingManager = true,
        )
        val state = AppHostsState(bookmarks = bookmarks)
        val decoded = Json.decodeFromString<AppHostsState>(Json.encodeToString(state))
        assertEquals(state, decoded)

        val appHosts = hosts()
        appHosts.restoreState(decoded)
        assertEquals(bookmarks, appHosts.saveState().bookmarks)
        // A fresh start carries nothing, so the default snapshot is stored as absent.
        appHosts.restoreState(null)
        assertEquals(null, appHosts.saveState().bookmarks)
    }

    @Test
    fun readingPlanStateRoundTrips() {
        val plan = ReadingPlanRecreationState(resetPlanCodes = setOf("y1ot1nt1", "chronological"))
        val state = AppHostsState(readingPlan = plan)
        val decoded = Json.decodeFromString<AppHostsState>(Json.encodeToString(state))
        assertEquals(state, decoded)

        val appHosts = hosts()
        appHosts.restoreState(decoded)
        assertEquals(plan, appHosts.saveState().readingPlan)
        // A fresh start carries nothing, so the empty snapshot is stored as absent.
        appHosts.restoreState(null)
        assertEquals(null, appHosts.saveState().readingPlan)
    }

    @Test
    fun myDocumentsStateRoundTrips() {
        val pending = MyDocumentsRecreationState(documentIds = listOf("11", "12"), documentsBatch = true)
        val state = AppHostsState(myDocuments = pending)
        val decoded = Json.decodeFromString<AppHostsState>(Json.encodeToString(state))
        assertEquals(state, decoded)

        val appHosts = hosts()
        appHosts.restoreState(decoded)
        assertEquals(pending, appHosts.saveState().myDocuments)
        // A fresh start carries nothing, so the empty snapshot is stored as absent.
        appHosts.restoreState(null)
        assertEquals(null, appHosts.saveState().myDocuments)
    }
}
