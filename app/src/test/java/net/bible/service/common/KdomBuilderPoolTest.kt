package net.bible.service.common

import fi.sykero.kdom.input.KdomBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KdomBuilderPoolTest {
    /** Holds [depth] builders live at once (nested uses) and returns them all. */
    private fun nested(depth: Int): List<KdomBuilder> {
        val seen = ArrayList<KdomBuilder>()
        fun rec(n: Int) {
            if (n == 0) return
            useKdomBuilder {
                assertTrue("a live builder was handed out twice", seen.none { s -> s === it })
                seen.add(it)
                rec(n - 1)
            }
        }
        rec(depth)
        return seen
    }

    @Test
    fun nestedUsesNeverShareALiveBuilderAndPoolKeepsAtMost32() {
        val first = nested(40)
        assertEquals(40, first.size)
        val second = nested(40)
        val reused = second.count { s -> first.any { it === s } }
        assertEquals("the pool must keep exactly 32 of the 40 returned builders", 32, reused)
    }

    @Test
    fun concurrentUsesNeverShareALiveBuilder() {
        val live = Collections.synchronizedMap(IdentityHashMap<KdomBuilder, Boolean>())
        val allLive = CountDownLatch(40)
        val executor = Executors.newFixedThreadPool(40)
        try {
            val futures = (1..40).map {
                executor.submit {
                    useKdomBuilder { b ->
                        assertTrue("shared live builder", live.put(b, true) == null)
                        allLive.countDown()
                        assertTrue(allLive.await(30, TimeUnit.SECONDS))
                        live.remove(b)
                        b.build("<a/>").rootElement
                    }
                }
            }
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertTrue(live.isEmpty())
    }

    @Test
    fun throwingBlockDoesNotReturnItsBuilderToThePool() {
        var thrown: KdomBuilder? = null
        // Drain: make the pool empty of builders other than ones we can recognise by using nested(33)+ is
        // unnecessary; instead check the thrown builder is never handed out again in the next 40 nested uses.
        try {
            useKdomBuilder { thrown = it; throw IllegalStateException("boom") }
        } catch (e: IllegalStateException) {
        }
        val after = nested(40)
        after.forEach { assertNotSame(thrown, it) }
    }
}
