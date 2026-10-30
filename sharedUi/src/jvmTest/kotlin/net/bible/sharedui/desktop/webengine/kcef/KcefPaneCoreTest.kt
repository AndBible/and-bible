/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.webengine.kcef

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** CEF-free tests of what both pane kinds share: eval replies, origin trust and the rendering-mode switch. */
class KcefPaneCoreTest {
    @Test
    fun evalRepliesGoToTheirCallbackNotToTheBridge() {
        val evals = EvalReplies()
        val got = mutableListOf<String>()
        val script = evals.wrap("1+1", "post", { got += "a:$it" }).script
        evals.wrap("2", "post", { got += "b:$it" })
        assertTrue(script.contains("(0, eval)(\"1+1\")"), script)
        assertTrue(script.contains("__eval: 1"), script)

        assertTrue(evals.consume("""{"__eval":2,"v":"\"a\\\"b\""}"""))
        assertTrue(evals.consume("""{"__eval":1,"v":"2"}"""))
        assertEquals(listOf("b:\"a\\\"b\"", "a:2"), got)
        assertEquals(0, evals.pendingCount)

        // A reply for an unknown or finished id is swallowed, bridge traffic is not.
        assertTrue(evals.consume("""{"__eval":1,"v":"3"}"""))
        assertFalse(evals.consume("""{"method":"setClientReady","args":[]}"""))
        assertEquals(2, got.size)
    }

    @Test
    fun timedOutEvalIsAnsweredWithNullOnceAndItsLateReplyDropped() {
        val evals = EvalReplies()
        val got = mutableListOf<String>()
        val first = evals.wrap("slow()", "post", { got += "a:$it" })
        evals.wrap("2", "post", { got += "b:$it" })
        evals.expire(first.id)
        evals.expire(first.id)
        assertEquals(listOf("a:null"), got)
        assertTrue(evals.consume("""{"__eval":${first.id},"v":"42"}"""), "a late reply is still not bridge traffic")
        assertEquals(listOf("a:null"), got)
        assertEquals(1, evals.pendingCount)
    }

    @Test
    fun failAllAnswersEveryPendingCallbackWithNull() {
        val evals = EvalReplies()
        val got = mutableListOf<String>()
        val a = evals.wrap("1", "post", { got += "a:$it" })
        evals.wrap("2", "post", { got += "b:$it" })
        // A callback that registers another evaluation while being failed (as a re-trying caller might) is safe.
        evals.wrap("3", "post", { got += "c:$it"; evals.wrap("4", "post", { got += "d:$it" }) })
        evals.failAll()
        assertEquals(listOf("a:null", "b:null", "c:null"), got)
        assertEquals(1, evals.pendingCount)
        evals.failAll()
        assertEquals(listOf("a:null", "b:null", "c:null", "d:null"), got)
        evals.expire(a.id)
        assertEquals(4, got.size, "an answered callback is never called again")
    }

    @Test
    fun failAllAnswersEveryCallbackEvenWhenOneThrows() {
        val evals = EvalReplies()
        val got = mutableListOf<String>()
        evals.wrap("1", "post", { got += "a:$it" })
        evals.wrap("2", "post", { throw IllegalStateException("boom") })
        evals.wrap("3", "post", { got += "c:$it" })
        evals.failAll()
        assertEquals(listOf("a:null", "c:null"), got)
        assertEquals(0, evals.pendingCount)
    }

    @Test
    fun onlyTheBundleOriginIsTrusted() {
        assertTrue(isAtBundleOrigin(BUNDLE_ORIGIN))
        assertTrue(isAtBundleOrigin("$BUNDLE_ORIGIN/"))
        assertTrue(isAtBundleOrigin("$BUNDLE_ORIGIN/index.html?lang=en&night=true"))
        assertTrue(isAtBundleOrigin("$BUNDLE_ORIGIN?x"))
        assertTrue(isAtBundleOrigin("$BUNDLE_ORIGIN#top"))
        assertFalse(isAtBundleOrigin(null))
        assertFalse(isAtBundleOrigin(""))
        assertFalse(isAtBundleOrigin("about:blank"))
        assertFalse(isAtBundleOrigin("https://example.org/"))
        assertFalse(isAtBundleOrigin("$BUNDLE_ORIGIN.evil.org/index.html"))
        assertFalse(isAtBundleOrigin("${BUNDLE_ORIGIN}evil/index.html"))
        assertFalse(isAtBundleOrigin("$BUNDLE_ORIGIN:8443/index.html"), "another port is another origin")
        assertFalse(isAtBundleOrigin("$BUNDLE_ORIGIN@evil.org/"), "userinfo trick")
        assertFalse(isAtBundleOrigin("http://bibleview.andbible.local/index.html"), "another scheme")
        assertFalse(isAtBundleOrigin("file:///tmp/index.html"))
    }

    @Test
    fun evalWrapperQuotesAnySource() {
        val literal = EvalReplies.jsStringLiteral("a\"b\\c\nd\u2028e</script>\u0001")
        assertEquals("\"a\\\"b\\\\c\\nd\\u2028e</script>\\u0001\"", literal)
    }

    @Test
    fun renderingModeParse() {
        val lines = mutableListOf<String>()
        assertEquals(KcefRenderingMode.Windowed, KcefRenderingMode.parse(null) { lines += it })
        assertEquals(KcefRenderingMode.Windowed, KcefRenderingMode.parse("windowed") { lines += it })
        assertEquals(KcefRenderingMode.Osr, KcefRenderingMode.parse("osr") { lines += it })
        assertTrue(lines.isEmpty(), "valid values and the default log nothing")
        assertEquals(KcefRenderingMode.Windowed, KcefRenderingMode.parse("OSR") { lines += it }, "values are case-sensitive")
        assertEquals(KcefRenderingMode.Windowed, KcefRenderingMode.parse("gpu") { lines += it })
        assertEquals(2, lines.size)
        assertTrue("gpu" in lines[1], lines[1])
    }

    @Test
    fun windowlessFollowsTheMode() {
        assertTrue(KcefRenderingMode.Osr.windowless)
        assertFalse(KcefRenderingMode.Windowed.windowless)
    }
}
