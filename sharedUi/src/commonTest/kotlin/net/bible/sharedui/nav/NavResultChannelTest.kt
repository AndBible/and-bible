package net.bible.sharedui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [deliverNavResult], the boolean-in branch, rather than [NavResultChannel] itself -- for the
 * reason [popOrExitOnFailedPop]'s kdoc gives: a `NavHostController` needs an Android `Context` to
 * construct, has no lightweight fake, and `:sharedUi` has no Robolectric-style runner.
 *
 * Both branches are asserted, and each assertion names the line it protects, because a test that
 * exercised only one mode would stay green with the other deleted -- the exact failure the previous
 * batch's `SearchHostBackRoutingGuardTest` shipped for a full task.
 */
class NavResultChannelTest {

    @Test
    fun withNoParentEntryTheResultLeavesTheHostAndNothingIsPublished() {
        var exited: String? = null
        var published: String? = null
        var popped = false

        deliverNavResult(
            hasParentEntry = false,
            result = "r",
            publishToParent = { published = it },
            popBackStack = { popped = true; true },
            exitWithResult = { exited = it },
        )

        assertEquals("r", exited, "an entry destination must exit the host WITH its result")
        assertNull(published, "nothing is waiting for it inside the graph")
        assertTrue(!popped, "popping an entry destination would empty the back stack")
    }

    @Test
    fun withAParentEntryTheResultIsPublishedAndThePopHappens() {
        var exited: String? = null
        var published: String? = null
        var popped = false

        deliverNavResult(
            hasParentEntry = true,
            result = "r",
            publishToParent = { published = it },
            popBackStack = { popped = true; true },
            exitWithResult = { exited = it },
        )

        assertEquals("r", published, "the in-graph parent reads it from the channel")
        assertTrue(popped, "the child must leave the back stack")
        assertNull(exited, "the host must NOT be finished -- the parent is still on it")
    }

    /** Plan D2: unreachable in theory; losing the result would be worse than exiting. */
    @Test
    fun aFailedPopDespiteAParentEntryStillDeliversTheResultOutwards() {
        var exited: String? = null

        deliverNavResult(
            hasParentEntry = true,
            result = "r",
            publishToParent = {},
            popBackStack = { false },
            exitWithResult = { exited = it },
        )

        assertEquals("r", exited)
    }

    @Test
    fun consumeReadsOnceAndClears() {
        val channel = NavResultChannel<String> { }
        channel.publishForTest("r")

        assertEquals("r", channel.consume())
        assertNull(channel.consume(), "a second read must not see the same result again")
    }
}
