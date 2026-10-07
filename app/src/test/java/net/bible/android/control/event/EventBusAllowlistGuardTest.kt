package net.bible.android.control.event

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ABEventBus removal program, spec §3.4: the event classes still allowed on the bus. The list only
 * shrinks. A new bus event fails here; move it to an owner's `Events<T>`/`StateFlow` instead (spec §3.2),
 * or add it on purpose, with a reason, in the same commit.
 */
class EventBusAllowlistGuardTest {

    private val allowlistFile = File("src/test/resources/net/bible/android/control/event/event-bus-allowlist.txt")
    private val allowlist get() = allowlistFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    private val usages by lazy { EventBusUsageScanner.productionUsages() }

    @Test
    fun everyBusEventIsAllowlisted() {
        val offenders = usages.filter { it.eventClass !in allowlist }
            .groupBy({ it.eventClass }, { it.location })
            .toSortedMap()
        assertTrue(
            "not on the ABEventBus allowlist (spec §3.4); use an owner's Events<T>/StateFlow, or add the " +
                "class deliberately:\n" + offenders.entries.joinToString("\n") { (c, l) -> "$c  <- ${l.joinToString()}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun theAllowlistHasNoStaleEntries() {
        val used = usages.map { it.eventClass }.toSet()
        assertEquals("these are no longer on the bus: delete them from ${allowlistFile.name}", emptyList<String>(), allowlist.filter { it !in used })
    }

    @Test
    fun theAllowlistIsSortedAndUnique() {
        assertEquals(allowlist.distinct().sorted(), allowlist)
    }

    /** A guard that cannot fail proves nothing: the scanner must see the shapes it is meant to catch. */
    @Test
    fun theScannerSeesPostsSubscriptionsAndVariablePostsButNotComments() {
        val src = """
            class X {
                fun a() { ABEventBus.post(BrandNewEvent(1)) }
                fun b() { ABEventBus.post(Outer.NestedEvent()) }
                fun c(e: Any) { ABEventBus.post(e) }
                // ABEventBus.post(CommentedEvent())
                init { ABEventBus.register(this) { on<OtherNewEvent> { }; onMain<MainEvent> { } } }
            }
        """.trimIndent()
        val found = EventBusUsageScanner.scan("X.kt", src).map { it.eventClass }.toSet()
        assertEquals(
            setOf("BrandNewEvent", "NestedEvent", EventBusUsageScanner.VARIABLE_POST, "OtherNewEvent", "MainEvent"),
            found,
        )
    }
}
